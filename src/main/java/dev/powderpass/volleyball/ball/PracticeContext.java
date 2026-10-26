package dev.powderpass.volleyball.ball;

import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.court.Court;
import dev.powderpass.volleyball.hit.HitResult;
import dev.powderpass.volleyball.math.Vec3;
import dev.powderpass.volleyball.physics.Aabb;
import dev.powderpass.volleyball.physics.NetContact;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Free play: anyone can hit, nets still collide, and landings on a court are called in/out. */
public final class PracticeContext implements BallContext {

    private final PowderPassPlugin plugin;
    /** ball id -> tick of the last touch whose landing has been called */
    private final Map<UUID, Long> called = new HashMap<>();

    public PracticeContext(PowderPassPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Court court(Volleyball ball) {
        Court c = plugin.courts().at(ball.world(), ball.position(), 1.0);
        if (c != null && plugin.matches().byCourt(c) != null) {
            return null;
        }
        return c;
    }

    @Override
    public boolean canTouch(Player player, Volleyball ball) {
        return plugin.matches().byPlayer(player) == null;
    }

    @Override
    public void onHit(Player player, Volleyball ball, HitResult result) {
    }

    @Override
    public void onBlockContact(Volleyball ball, Vec3 position, Vec3 normal, double impact, Aabb box) {
        if (normal.y < 0.7 || impact < 0.5 || ball.lastToucher() == null) {
            return;
        }
        Long done = called.get(ball.id());
        if (done != null && done == ball.lastHitTick()) {
            return;
        }
        called.put(ball.id(), ball.lastHitTick());
        Court c = ball.court();
        if (c == null) {
            return;
        }
        if (Math.abs(position.y - c.centerY()) > 0.6) {
            return;
        }
        var visuals = plugin.courts().visuals(c);
        double r = plugin.settings().physics.radius();
        boolean in = visuals != null ? visuals.callLanding(position, r)
                : c.isIn(c.frame().toU(position.x, position.z), c.frame().toW(position.x, position.z), r);
        Player p = Bukkit.getPlayer(ball.lastToucher());
        if (p != null && p.getWorld() == ball.world() && p.getLocation().distanceSquared(ball.location()) < 60 * 60) {
            p.sendActionBar(Component.text(in ? "IN" : "OUT", in ? NamedTextColor.GREEN : NamedTextColor.RED,
                    TextDecoration.BOLD));
        }
    }

    @Override
    public void onNetContact(Volleyball ball, NetContact contact, Vec3 localPoint, double impact) {
    }

    @Override
    public void onNetCross(Volleyball ball, int fromSide, double localW, double localY) {
    }

    /** Drops bookkeeping for a removed ball. */
    public void forget(Volleyball ball) {
        called.remove(ball.id());
    }

    @Override
    public boolean shouldDespawn(Volleyball ball) {
        boolean idle = ball.ticksSinceMove() > plugin.settings().ball.idleDespawnSeconds() * 20L;
        if (idle) {
            called.remove(ball.id());
        }
        return idle;
    }
}
