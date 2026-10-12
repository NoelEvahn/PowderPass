package com.powderpass.backrooms;

import com.powderpass.backrooms.command.BackroomsCommand;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.config.DataStore;
import com.powderpass.backrooms.creature.CreatureManager;
import com.powderpass.backrooms.display.DecorationManager;
import com.powderpass.backrooms.display.Keys;
import com.powderpass.backrooms.event.EnvironmentManager;
import com.powderpass.backrooms.gen.Feature;
import com.powderpass.backrooms.gen.LevelGenerator;
import com.powderpass.backrooms.item.FlashlightService;
import com.powderpass.backrooms.item.Items;
import com.powderpass.backrooms.item.LootService;
import com.powderpass.backrooms.listener.CombatListener;
import com.powderpass.backrooms.listener.PlayerListener;
import com.powderpass.backrooms.listener.ProtectionListener;
import com.powderpass.backrooms.player.SessionManager;
import com.powderpass.backrooms.player.TransitionService;
import com.powderpass.backrooms.world.BackroomsChunkGenerator;
import com.powderpass.backrooms.world.TeleportService;
import com.powderpass.backrooms.world.WorldManager;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

/**
 * Backrooms: five custom worlds forming one alternate dimension.
 *
 * <p>Startup order: config → persistent metadata → worlds (created or re-loaded, never regenerated) → services →
 * listeners → command. Shutdown removes every runtime entity (all of which are non-persistent anyway) and saves
 * metadata; worlds are left to the server's normal save cycle.</p>
 */
public class BackroomsPlugin extends JavaPlugin {
    private BackroomsConfig cfg;
    private DataStore data;
    private WorldManager worlds;
    private TeleportService teleports;
    private SessionManager sessions;
    private TransitionService transitions;
    private DecorationManager decorations;
    private CreatureManager creatures;
    private EnvironmentManager environment;
    private Items items;
    private LootService loot;
    private FlashlightService flashlights;
    private BukkitTask debugTask;
    private boolean consoleDebug;

    private void ensureCore() {
        if (cfg != null) return;
        saveDefaultConfig();
        Keys.init(this);
        cfg = new BackroomsConfig(getConfig());
        consoleDebug = cfg.debug;
        data = new DataStore(getDataFolder(), getLogger());
        data.load();
        worlds = new WorldManager(this, data);
    }

    @Override
    public void onEnable() {
        ensureCore();
        long t = System.currentTimeMillis();
        worlds.loadAll();
        teleports = new TeleportService(this);
        items = new Items(this);
        loot = new LootService(this);
        sessions = new SessionManager(this);
        transitions = new TransitionService(this);
        decorations = new DecorationManager(this);
        creatures = new CreatureManager(this);
        environment = new EnvironmentManager(this);
        flashlights = new FlashlightService(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(decorations, this);

        startServices();

        PluginCommand cmd = getCommand("backrooms");
        if (cmd != null) {
            BackroomsCommand bc = new BackroomsCommand(this);
            cmd.setExecutor(bc);
            cmd.setTabCompleter(bc);
        }
        getLogger().info("Backrooms ready: " + worlds.worlds().size() + "/" + Level.values().length + " levels loaded in " + (System.currentTimeMillis() - t) + " ms (seed " + data.baseSeed() + ").");
    }

    private void startServices() {
        sessions.start();
        transitions.start();
        decorations.start();
        creatures.start();
        environment.start();
        flashlights.start();
        if (debugTask != null) debugTask.cancel();
        debugTask = getServer().getScheduler().runTaskTimer(this, this::debugOverlay, 20L, 10L);
    }

    private void stopServices() {
        if (debugTask != null) debugTask.cancel();
        debugTask = null;
        if (flashlights != null) flashlights.stop();
        if (environment != null) environment.stop();
        if (creatures != null) creatures.stop();
        if (decorations != null) decorations.stop();
        if (transitions != null) transitions.stop();
        if (sessions != null) sessions.stop();
    }

    @Override
    public void onDisable() {
        stopServices();
        if (data != null) data.save();
        getServer().getScheduler().cancelTasks(this);
    }

    /** Re-read config.yml and restart runtime services without unloading or recreating any world. */
    public void reload() {
        stopServices();
        reloadConfig();
        cfg = new BackroomsConfig(getConfig());
        consoleDebug = cfg.debug;
        worlds.loadAll();
        worlds.reapplyRules();
        startServices();
    }

    /** Lets server owners reference the generator directly, e.g. bukkit.yml: generator: Backrooms:level0 */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        ensureCore();
        Level l = Level.parse(id == null || id.isBlank() ? worldName.replace(cfg.worldPrefix, "") : id);
        if (l == null) {
            getLogger().warning("Unknown Backrooms generator id '" + id + "' for world " + worldName + "; use level0, poolrooms, level1..level5");
            return null;
        }
        return new BackroomsChunkGenerator(worlds.generator(l), cfg.biome.get(l));
    }

    // ------------------------------------------------------------------ debug

    public void debug(String msg) {
        if (consoleDebug) getLogger().info("[debug] " + msg);
    }

    public void setConsoleDebug(boolean on) {
        consoleDebug = on;
    }

    private void debugOverlay() {
        for (Player p : getServer().getOnlinePlayers()) {
            if (!sessions.debug(p)) continue;
            Level l = worlds.levelOf(p.getWorld());
            if (l == null) {
                p.sendActionBar(BackroomsConfig.mm("<dark_gray>[debug] not inside the Backrooms"));
                continue;
            }
            LevelGenerator g = worlds.generator(l);
            Location loc = p.getLocation();
            int cx = g.cellOf(loc.getBlockX()), cz = g.cellOf(loc.getBlockZ());
            int floor = g.floorAt(loc.getBlockY());
            Feature here = g.featureAt(floor, cx, cz);
            Feature nearest = null;
            double nd = Double.MAX_VALUE;
            for (Feature f : g.featuresNear(loc.getBlockX(), loc.getBlockZ(), 400)) {
                if (!f.isTransition()) continue;
                double[] c = g.featureCenter(f);
                double d = Math.hypot(c[0] - loc.getX(), c[2] - loc.getZ());
                if (d < nd) {
                    nd = d;
                    nearest = f;
                }
            }
            String text = String.format(Locale.ROOT,
                    "<gray>%s <white>cell %d,%d f%d</white> %s <dark_gray>|</dark_gray> light <white>%d</white> sanity <white>%.0f</white> "
                            + "<dark_gray>|</dark_gray> next: <white>%s</white> %s",
                    l.arg(), cx, cz, floor, g.zoneName(floor, cx, cz) + (here != null ? " <gold>[" + here.id() + "]</gold>" : ""),
                    loc.getBlock().getLightFromBlocks(), sessions.sanity(p),
                    nearest == null ? "none" : nearest.id() + "→" + nearest.dest().describe(), nearest == null ? "" : (int) nd + "m");
            p.sendActionBar(BackroomsConfig.mm(text));
        }
    }

    // ------------------------------------------------------------------ accessors

    public BackroomsConfig cfg() {
        return cfg;
    }

    public DataStore data() {
        return data;
    }

    public WorldManager worlds() {
        return worlds;
    }

    public TeleportService teleports() {
        return teleports;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public TransitionService transitions() {
        return transitions;
    }

    public DecorationManager decorations() {
        return decorations;
    }

    public CreatureManager creatures() {
        return creatures;
    }

    public EnvironmentManager environment() {
        return environment;
    }

    public Items items() {
        return items;
    }

    public LootService loot() {
        return loot;
    }

    public FlashlightService flashlights() {
        return flashlights;
    }
}
