package dev.powderpass.volleyball;

import dev.powderpass.volleyball.ball.BallManager;
import dev.powderpass.volleyball.command.VolleyballCommand;
import dev.powderpass.volleyball.config.Settings;
import dev.powderpass.volleyball.court.CourtManager;
import dev.powderpass.volleyball.game.MatchManager;
import dev.powderpass.volleyball.game.SnapshotStore;
import dev.powderpass.volleyball.hit.HitController;
import dev.powderpass.volleyball.listener.InputListener;
import dev.powderpass.volleyball.listener.ProtectionListener;
import dev.powderpass.volleyball.ui.Fx;
import dev.powderpass.volleyball.ui.HudManager;
import dev.powderpass.volleyball.ui.Menus;
import dev.powderpass.volleyball.ui.ScoreboardManager;
import dev.powderpass.volleyball.util.Keys;
import dev.powderpass.volleyball.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

/** PowderPass: realistic volleyball for Paper. */
public final class PowderPassPlugin extends JavaPlugin {

    private Settings settings;
    private Fx fx;
    private CourtManager courts;
    private BallManager balls;
    private HitController hits;
    private MatchManager matches;
    private HudManager hud;
    private ScoreboardManager boards;
    private Menus menus;
    private SnapshotStore snapshots;
    private dev.powderpass.volleyball.pepper.PepperManager pepper;
    private BukkitTask ticker;

    private final long[] tickNanos = new long[200];
    private int tickIndex;
    /** accumulated nanos per subsystem: balls, hits, matches, courts, hud, boards */
    private final long[] partNanos = new long[6];
    private long partTicks;

    @Override
    public void onEnable() {
        Keys.init(this);
        saveDefaultConfig();
        // older configs shipped a smaller ball; move untouched values to the new default
        if (getConfig().getDouble("ball.visual-size", 0.45) == 0.30) {
            getConfig().set("ball.visual-size", 0.45);
            saveConfig();
        }
        settings = new Settings(getConfig(), getLogger());
        loadSettings();

        snapshots = new SnapshotStore(getDataFolder(), getLogger());
        courts = new CourtManager(this);
        balls = new BallManager(this);
        hits = new HitController(this);
        matches = new MatchManager(this);
        hud = new HudManager(this);
        boards = new ScoreboardManager(this);
        menus = new Menus(this);
        pepper = new dev.powderpass.volleyball.pepper.PepperManager(this);

        removeStrayEntities();
        courts.load();
        courts.spawnAll();
        boards.load();
        boards.spawnAll();

        var pm = getServer().getPluginManager();
        pm.registerEvents(new InputListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);
        pm.registerEvents(new dev.powderpass.volleyball.listener.SeatListener(this), this);
        pm.registerEvents(menus, this);

        PluginCommand cmd = getCommand("volleyball");
        if (cmd != null) {
            VolleyballCommand executor = new VolleyballCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        ticker = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        getLogger().info("PowderPass volleyball enabled: " + courts.all().size() + " court(s), "
                + boards.all().size() + " scoreboard(s).");
    }

    private void loadSettings() {
        settings = new Settings(getConfig(), getLogger());
        fx = new Fx(this, settings);
        String prefix = getConfig().getString("prefix");
        if (prefix != null && !prefix.isBlank()) {
            Msg.setPrefix(prefix);
        }
    }

    /** Removes display entities left behind by an unclean shutdown. */
    private void removeStrayEntities() {
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getPersistentDataContainer().has(Keys.ENTITY, PersistentDataType.STRING)) {
                    e.remove();
                    n++;
                }
            }
        }
        if (n > 0) {
            getLogger().info("Removed " + n + " leftover display entities.");
        }
    }

    private void tick() {
        long start = System.nanoTime();
        try {
            long t0 = start;
            balls.tick();
            long t1 = System.nanoTime();
            hits.tick();
            long t2 = System.nanoTime();
            matches.tick();
            pepper.tick();
            long t3 = System.nanoTime();
            courts.tick();
            long t4 = System.nanoTime();
            hud.tick();
            long t5 = System.nanoTime();
            boards.tick();
            long t6 = System.nanoTime();
            partNanos[0] += t1 - t0;
            partNanos[1] += t2 - t1;
            partNanos[2] += t3 - t2;
            partNanos[3] += t4 - t3;
            partNanos[4] += t5 - t4;
            partNanos[5] += t6 - t5;
            partTicks++;
            double slowMs = getConfig().getDouble("performance.log-slow-ticks-ms", 0);
            if (slowMs > 0 && (t6 - t0) / 1e6 > slowMs) {
                getLogger().warning(String.format(java.util.Locale.ROOT,
                        "Slow volleyball tick %.2f ms: balls %.2f hits %.2f matches %.2f courts %.2f hud %.2f boards %.2f",
                        (t6 - t0) / 1e6, (t1 - t0) / 1e6, (t2 - t1) / 1e6, (t3 - t2) / 1e6, (t4 - t3) / 1e6,
                        (t5 - t4) / 1e6, (t6 - t5) / 1e6));
            }
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Error in volleyball tick", e);
        }
        tickNanos[tickIndex] = System.nanoTime() - start;
        tickIndex = (tickIndex + 1) % tickNanos.length;
    }

    public double avgTickMs() {
        long sum = 0;
        for (long t : tickNanos) {
            sum += t;
        }
        return sum / (double) tickNanos.length / 1e6;
    }

    /** Average ms per tick of each subsystem since the last call, then resets. */
    public String tickBreakdown() {
        String[] names = {"balls", "hits", "matches", "courts", "hud", "boards"};
        StringBuilder sb = new StringBuilder();
        long n = Math.max(1, partTicks);
        for (int i = 0; i < names.length; i++) {
            sb.append(names[i]).append(String.format(java.util.Locale.ROOT, " %.3f", partNanos[i] / (double) n / 1e6)).append("  ");
            partNanos[i] = 0;
        }
        partTicks = 0;
        return sb.toString().trim();
    }

    public double maxTickMs() {
        long max = 0;
        for (long t : tickNanos) {
            max = Math.max(max, t);
        }
        return max / 1e6;
    }

    /** /vb reload: re-reads config.yml and respawns decorations. Running matches keep going. */
    public void reloadPlugin() {
        reloadConfig();
        loadSettings();
        hits.reload();
        courts.spawnAll();
        boards.spawnAll();
    }

    @Override
    public void onDisable() {
        if (ticker != null) {
            ticker.cancel();
        }
        if (matches != null) {
            matches.shutdown();
        }
        if (pepper != null) {
            pepper.shutdown();
        }
        if (hud != null) {
            hud.shutdown();
        }
        if (hits != null) {
            hits.shutdown();
        }
        if (balls != null) {
            balls.shutdown();
        }
        if (courts != null) {
            courts.shutdown();
        }
        if (boards != null) {
            boards.shutdown();
        }
        for (World w : Bukkit.getWorlds()) {
            w.removePluginChunkTickets(this);
        }
    }

    public Settings settings() {
        return settings;
    }

    public Fx fx() {
        return fx;
    }

    public CourtManager courts() {
        return courts;
    }

    public BallManager balls() {
        return balls;
    }

    public HitController hits() {
        return hits;
    }

    public MatchManager matches() {
        return matches;
    }

    public HudManager hud() {
        return hud;
    }

    public ScoreboardManager boards() {
        return boards;
    }

    public Menus menus() {
        return menus;
    }

    public dev.powderpass.volleyball.pepper.PepperManager pepper() {
        return pepper;
    }

    public SnapshotStore snapshots() {
        return snapshots;
    }
}
