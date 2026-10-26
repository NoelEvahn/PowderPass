package dev.powderpass.volleyball.pepper;

import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.ball.BallContext;
import dev.powderpass.volleyball.ball.Volleyball;
import dev.powderpass.volleyball.config.Settings;
import dev.powderpass.volleyball.court.Court;
import dev.powderpass.volleyball.hit.HitResult;
import dev.powderpass.volleyball.hit.HitType;
import dev.powderpass.volleyball.math.Vec3;
import dev.powderpass.volleyball.physics.Aabb;
import dev.powderpass.volleyball.physics.NetContact;
import dev.powderpass.volleyball.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A pepper drill: no rules, just keep the ball off the floor. One player juggles (solo pepper), two go
 * bump - set - hit (pair pepper), three or more pass around a circle (circle pepper). Touches are aimed at
 * the teammate you face, hits are kept controllable, and a dropped ball is fed back in automatically.
 */
public final class PepperSession implements BallContext {

    /** Kind of drill, from the number of players. */
    public enum Mode {
        SOLO("Solo pepper"), PAIR("Pepper"), CIRCLE("Circle pepper");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    private static final double HIT_SPEED_CAP = 15.0;
    private static final double ASSIST_CONE_DEG = 70;

    private final PowderPassPlugin plugin;
    private final UUID host;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final BossBar bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
    private Volleyball ball;
    private int streak;
    private int best;
    private UUID lastToucher;
    private HitType lastType;
    private int feedIn = -1;
    private int idleTicks;
    private boolean closed;

    public PepperSession(PowderPassPlugin plugin, Player host) {
        this.plugin = plugin;
        this.host = host.getUniqueId();
        add(host);
    }

    public UUID host() {
        return host;
    }

    public Set<UUID> members() {
        return members;
    }

    public Mode mode() {
        return members.size() <= 1 ? Mode.SOLO : members.size() == 2 ? Mode.PAIR : Mode.CIRCLE;
    }

    public int best() {
        return best;
    }

    public boolean closed() {
        return closed;
    }

    public void add(Player p) {
        members.add(p.getUniqueId());
        p.showBossBar(bar);
        updateBar();
    }

    public void remove(Player p) {
        members.remove(p.getUniqueId());
        p.hideBossBar(bar);
        if (members.isEmpty()) {
            close();
        } else {
            updateBar();
        }
    }

    public List<Player> online() {
        List<Player> list = new ArrayList<>();
        for (UUID id : members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(bar);
        }
        if (ball != null) {
            plugin.balls().remove(ball);
            ball = null;
        }
    }

    // ------------------------------------------------------------------------------------------- feeding

    /** Drops a fresh ball onto {@code p}'s hands (start of the drill or after it hit the floor). */
    public void feed(Player p) {
        Location l = p.getLocation();
        double yaw = Math.toRadians(l.getYaw());
        Vec3 pos = new Vec3(l.getX() - Math.sin(yaw) * 0.6, l.getY() + 3.3, l.getZ() + Math.cos(yaw) * 0.6);
        if (ball == null || ball.removed()) {
            ball = plugin.balls().spawn(p.getWorld(), pos, this);
        } else {
            ball.teleport(pos);
        }
        ball.launch(new Vec3(0, -1.0, 0), new Vec3(), false, null, null);
        streak = 0;
        lastToucher = null;
        lastType = null;
        feedIn = -1;
        plugin.fx().sound(p.getLocation(), Settings.SoundId.TOSS);
        p.sendActionBar(Component.text("Ball in! ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("keep it off the floor", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false)));
        updateBar();
    }

    private Player nearestTo(Vec3 pos) {
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player p : online()) {
            if (p.getWorld() != ball.world()) {
                continue;
            }
            Location l = p.getLocation();
            double d = (l.getX() - pos.x) * (l.getX() - pos.x) + (l.getZ() - pos.z) * (l.getZ() - pos.z);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------------------------------- BallContext

    @Override
    public Court court(Volleyball b) {
        Court c = plugin.courts().at(b.world(), b.position(), 1.0);
        return c != null && plugin.matches().byCourt(c) != null ? null : c;
    }

    @Override
    public boolean canTouch(Player player, Volleyball b) {
        return b == ball && members.contains(player.getUniqueId());
    }

    @Override
    public void onHit(Player player, Volleyball b, HitResult result) {
        idleTicks = 0;
        streak++;
        lastToucher = player.getUniqueId();
        lastType = result.type();
        if (streak > best) {
            best = streak;
        }
        if (streak == 10 || streak == 25 || streak == 50 || streak % 100 == 0) {
            for (Player p : online()) {
                plugin.fx().sound(p, Settings.SoundId.CHEER);
                p.sendActionBar(Component.text(streak + " in a row!", NamedTextColor.GOLD, TextDecoration.BOLD));
            }
        }
        updateBar();
    }

    @Override
    public void onBlockContact(Volleyball b, Vec3 position, Vec3 normal, double impact, Aabb box) {
        if (b != ball || normal.y < 0.7 || impact < 0.3 || feedIn >= 0) {
            return;
        }
        if (streak > 0) {
            boolean record = streak >= best && streak >= 5;
            Component msg = Component.text("Dropped! ", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text("Streak " + streak + (record ? " - new best!" : "  (best " + best + ")"),
                            record ? NamedTextColor.GOLD : NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false));
            for (Player p : online()) {
                p.sendActionBar(msg);
            }
            if (record) {
                Msg.send(net.kyori.adventure.audience.Audience.audience(online()),
                        "<gold>New pepper record: <white><b>" + streak + "</b></white> touches in a row!");
            }
        }
        streak = 0;
        feedIn = 30;
        updateBar();
    }

    @Override
    public void onNetContact(Volleyball b, NetContact contact, Vec3 localPoint, double impact) {
    }

    @Override
    public void onNetCross(Volleyball b, int fromSide, double localW, double localY) {
    }

    @Override
    public void onTick(Volleyball b) {
        if (b != ball) {
            return;
        }
        if (feedIn > 0 && --feedIn == 0) {
            feedIn = -1;
            Player next = nearestTo(b.position());
            if (next != null) {
                feed(next);
            }
        }
        Player near = nearestTo(b.position());
        if (near != null && feedIn < 0) {
            Location l = near.getLocation();
            double dx = l.getX() - b.position().x;
            double dz = l.getZ() - b.position().z;
            if (dx * dx + dz * dz > 25 * 25 || b.body().resting) {
                feed(near);
            }
        }
    }

    @Override
    public boolean shouldDespawn(Volleyball b) {
        return closed;
    }

    /**
     * Solo: every touch goes back up to yourself. With others: the touch goes to the teammate closest to
     * where you are facing (within 70 degrees), otherwise free aim. Hits land just in front of the target so
     * they can dig them.
     */
    @Override
    public Vec3 assistTarget(Player player, Volleyball b, HitType type) {
        if (type.serve()) {
            return null;
        }
        Location l = player.getLocation();
        double yaw = Math.toRadians(l.getYaw());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        if (mode() == Mode.SOLO) {
            return new Vec3(l.getX() + fx * 0.3, l.getY(), l.getZ() + fz * 0.3);
        }
        Player best = null;
        double bestAngle = ASSIST_CONE_DEG;
        for (Player o : online()) {
            if (o == player || o.getWorld() != player.getWorld()) {
                continue;
            }
            Location ol = o.getLocation();
            double dx = ol.getX() - l.getX();
            double dz = ol.getZ() - l.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < 1.5 || dist > 18) {
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (dx * fx + dz * fz) / dist))));
            if (angle < bestAngle) {
                bestAngle = angle;
                best = o;
            }
        }
        if (best == null) {
            return null;
        }
        Location t = best.getLocation();
        double dx = l.getX() - t.getX();
        double dz = l.getZ() - t.getZ();
        double d = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        // land a little in front of the receiver (toward the passer): it drops into their hands
        double lead = type.attack() ? 0.9 : 0.5;
        return new Vec3(t.getX() + dx / d * lead, t.getY(), t.getZ() + dz / d * lead);
    }

    @Override
    public double speedCap(HitType type) {
        return type.attack() ? HIT_SPEED_CAP : Double.MAX_VALUE;
    }

    // ------------------------------------------------------------------------------------------- HUD

    /** What the receiver usually does next in the bump - set - hit cycle. */
    private String next() {
        if (mode() == Mode.SOLO) {
            return "bump or set it up";
        }
        if (lastType == null) {
            return "bump";
        }
        if (lastType.attack()) {
            return "dig";
        }
        if (lastType == HitType.SET || lastType == HitType.QUICK_SET) {
            return "hit";
        }
        return "set";
    }

    void updateBar() {
        Mode m = mode();
        bar.name(Component.text(m.label, NamedTextColor.YELLOW, TextDecoration.BOLD)
                .append(Component.text("  streak ", NamedTextColor.GRAY))
                .append(Component.text(streak, NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text("  best " + best, NamedTextColor.GRAY))
                .append(Component.text("  next: " + next(), NamedTextColor.AQUA)));
        bar.progress(Math.min(1f, (streak % 10) / 10f));
    }

    /** Called every tick by the manager. */
    void tick() {
        idleTicks++;
        if (idleTicks > 20 * 180) {
            for (Player p : online()) {
                Msg.send(p, "<gray>Pepper ended (idle).");
            }
            close();
        }
    }
}
