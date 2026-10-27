package dev.powderpass.volleyball.ball;

import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.math.Vec3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Keeps track of every live ball and ticks them. */
public final class BallManager {

    private final PowderPassPlugin plugin;
    private final List<Volleyball> balls = new ArrayList<>();
    private final PracticeContext practice;

    public BallManager(PowderPassPlugin plugin) {
        this.plugin = plugin;
        this.practice = new PracticeContext(plugin);
    }

    public PracticeContext practice() {
        return practice;
    }

    public Volleyball spawn(World world, Vec3 pos, BallContext context) {
        Volleyball b = new Volleyball(plugin, world, pos, context);
        balls.add(b);
        return b;
    }

    /** Spawns a free-play ball owned by {@code owner}, removing their oldest balls above the limit. */
    public Volleyball spawnPractice(Player owner, Vec3 pos) {
        int max = plugin.settings().ball.maxPracticeBalls();
        List<Volleyball> owned = new ArrayList<>();
        for (Volleyball b : balls) {
            if (owner.getUniqueId().equals(b.owner()) && b.context() == practice) {
                owned.add(b);
            }
        }
        while (owned.size() >= max) {
            remove(owned.remove(0));
        }
        Volleyball b = spawn(owner.getWorld(), pos, practice);
        b.setOwner(owner.getUniqueId());
        return b;
    }

    /** The free-play ball {@code p} is holding, if any. */
    public Volleyball holding(Player p) {
        for (Volleyball b : balls) {
            if (b.held() && b.context() == practice && p.getUniqueId().equals(b.owner())) {
                return b;
            }
        }
        return null;
    }

    /** Puts a free-play ball in {@code p}'s hands (replacing one they already hold). */
    public Volleyball giveHeld(Player p) {
        Volleyball old = holding(p);
        if (old != null) {
            return old;
        }
        Volleyball b = spawnPractice(p, plugin.hits().handPosition(p));
        b.setHeld(true);
        return b;
    }

    /** Serves the held free-play ball: a toss, or an underhand serve when sneaking. */
    public void serveHeld(Player p, Volleyball b) {
        b.setHeld(false);
        if (p.isSneaking()) {
            b.teleport(plugin.hits().underhandPosition(p));
            b.markToss(p.getUniqueId());
            plugin.hits().underhand(p, b);
            return;
        }
        Vec3[] st = plugin.hits().tossState(p);
        b.teleport(st[0]);
        plugin.hits().prepareToss(p, b, st[1]);
    }

    public void tick() {
        if (balls.isEmpty()) {
            return;
        }
        for (Volleyball b : new ArrayList<>(balls)) {
            if (b.removed()) {
                balls.remove(b);
                continue;
            }
            try {
                b.doTick();
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error ticking ball, removing it", e);
                remove(b);
                continue;
            }
            if (b.position().y < b.world().getMinHeight() - 16) {
                remove(b);
            } else if (b.context().shouldDespawn(b)) {
                removeAnimated(b);
            }
        }
    }

    public void remove(Volleyball b) {
        b.remove();
        balls.remove(b);
        practice.forget(b);
    }

    /** Removes with the shrink animation (end of a rally, idle practice ball). */
    public void removeAnimated(Volleyball b) {
        b.removeAnimated();
        balls.remove(b);
        practice.forget(b);
    }

    public void removeIf(java.util.function.Predicate<Volleyball> filter) {
        Iterator<Volleyball> it = balls.iterator();
        while (it.hasNext()) {
            Volleyball b = it.next();
            if (filter.test(b)) {
                b.remove();
                it.remove();
                practice.forget(b);
            }
        }
    }

    public int removeOwnedBy(UUID owner) {
        int before = balls.size();
        removeIf(b -> owner.equals(b.owner()) && b.context() == practice);
        return before - balls.size();
    }

    public List<Volleyball> all() {
        return Collections.unmodifiableList(balls);
    }

    public List<Volleyball> near(Location loc, double radius) {
        List<Volleyball> out = new ArrayList<>();
        double r2 = radius * radius;
        for (Volleyball b : balls) {
            if (b.world() != loc.getWorld()) {
                continue;
            }
            Vec3 p = b.position();
            double dx = p.x - loc.getX();
            double dy = p.y - loc.getY();
            double dz = p.z - loc.getZ();
            if (dx * dx + dy * dy + dz * dz <= r2) {
                out.add(b);
            }
        }
        return out;
    }

    public void shutdown() {
        for (Volleyball b : balls) {
            b.remove();
        }
        balls.clear();
    }
}
