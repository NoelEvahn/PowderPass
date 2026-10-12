package dev.powderpass;

import dev.powderpass.command.PowderPassCommand;
import dev.powderpass.config.Messages;
import dev.powderpass.config.PluginConfig;
import dev.powderpass.course.CourseManager;
import dev.powderpass.display.Managed;
import dev.powderpass.gear.GearItems;
import dev.powderpass.gui.Menu;
import dev.powderpass.gui.MenuListener;
import dev.powderpass.lift.LiftManager;
import dev.powderpass.pass.KioskManager;
import dev.powderpass.pass.PassService;
import dev.powderpass.profile.BadgeService;
import dev.powderpass.profile.ProfileStore;
import dev.powderpass.ski.SkiListener;
import dev.powderpass.ski.SkiManager;
import dev.powderpass.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * PowderPass: a physics-driven alpine resort engine. Skiing and snowboarding with an animated display-entity
 * skier, chairlifts built entirely from displays, slalom racing with record ghosts, lift passes and badges.
 */
public final class PowderPassPlugin extends JavaPlugin {

    private PluginConfig config;
    private final Messages messages = new Messages();
    private ProfileStore profiles;
    private BadgeService badges;
    private GearItems gear;
    private PassService passes;
    private SkiManager skis;
    private LiftManager lifts;
    private CourseManager courses;
    private KioskManager kiosks;
    private ExecutorService fileWriter;
    private BukkitTask ticker;
    private long tick;

    @Override
    public void onEnable() {
        Keys.init(this);
        saveDefaultConfig();
        reloadConfig();
        config = new PluginConfig(getConfig(), getLogger());
        messages.load(this);
        fileWriter = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PowderPass-Writer");
            t.setDaemon(true);
            return t;
        });

        profiles = new ProfileStore(this);
        badges = new BadgeService(messages);
        gear = new GearItems(this, config);
        gear.registerRecipes();
        passes = new PassService(() -> config);
        skis = new SkiManager(this);
        lifts = new LiftManager(this);
        courses = new CourseManager(this);
        kiosks = new KioskManager(this);

        sweepStrayEntities();
        lifts.load();
        lifts.buildAll();
        courses.load();
        courses.rebuildAll();
        kiosks.load();
        kiosks.buildAll();

        getServer().getPluginManager().registerEvents(new SkiListener(this, skis), this);
        getServer().getPluginManager().registerEvents(new GeneralListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        registerCommand("powderpass", "PowderPass alpine resort", List.of("pp", "ski"), new PowderPassCommand(this));

        for (Player p : Bukkit.getOnlinePlayers()) {
            profiles.get(p);
        }
        ticker = Bukkit.getScheduler().runTaskTimer(this, this::tickAll, 1L, 1L);
        getLogger().info("PowderPass ready: " + lifts.definitions().size() + " lift(s), " + courses.courses().size()
                + " course(s), " + kiosks.count() + " kiosk(s).");
    }

    @Override
    public void onDisable() {
        if (ticker != null) {
            ticker.cancel();
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) {
                p.closeInventory();
            }
        }
        if (skis != null) {
            skis.shutdown();
        }
        if (lifts != null) {
            lifts.shutdown();
        }
        if (courses != null) {
            courses.shutdown();
        }
        if (kiosks != null) {
            kiosks.shutdown();
        }
        if (gear != null) {
            gear.unregisterRecipes();
        }
        if (profiles != null) {
            profiles.shutdown();
        }
        if (fileWriter != null) {
            fileWriter.shutdown();
            try {
                if (!fileWriter.awaitTermination(10, TimeUnit.SECONDS)) {
                    getLogger().warning("Timed out waiting for data files to be written");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        sweepStrayEntities();
    }

    private void tickAll() {
        tick++;
        safely("skiers", skis::tickAll);
        safely("lifts", lifts::tick);
        safely("courses", courses::tick);
        safely("kiosks", kiosks::tick);
        if (tick % (config.autosaveSeconds * 20L) == 0) {
            profiles.saveDirtyAsync();
        }
    }

    private void safely(String what, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Error while ticking " + what, e);
        }
    }

    /** Removes any PowderPass entity left in loaded chunks (e.g. after a crash or a hot reload). */
    private void sweepStrayEntities() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (Managed.is(e)) {
                    e.eject();
                    e.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            getLogger().info("Removed " + removed + " leftover display entities.");
        }
    }

    /** /pp reload: re-reads config and messages and rebuilds every structure. */
    public void reloadEverything() {
        skis.shutdown();
        reloadConfig();
        config = new PluginConfig(getConfig(), getLogger());
        messages.load(this);
        gear.reload(config);
        gear.registerRecipes();
        skis.reload();
        lifts.buildAll();
        courses.rebuildAll();
        kiosks.buildAll();
    }

    void onWorldUnload(World world) {
        for (var session : skis.sessions()) {
            if (session.world().equals(world)) {
                skis.end(session, dev.powderpass.ski.SkiSession.EndReason.WORLD, false);
            }
        }
        lifts.onWorldUnload(world);
        courses.onWorldUnload(world);
        kiosks.onWorldUnload(world);
    }

    void onWorldLoad(World world) {
        lifts.onWorldLoad(world);
        courses.onWorldLoad(world);
        kiosks.buildAll();
    }

    // ------------------------------------------------------------------------------------------------ files

    /** Writes a file atomically on the writer thread. Writes are applied in submission order. */
    public void writeAsync(File target, byte[] data) {
        fileWriter.execute(() -> {
            try {
                CourseManager.atomicWrite(target, data);
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Could not write " + target.getName(), e);
            }
        });
    }

    public void deleteAsync(File target) {
        fileWriter.execute(() -> {
            try {
                Files.deleteIfExists(target.toPath());
            } catch (IOException e) {
                getLogger().log(Level.WARNING, "Could not delete " + target.getName(), e);
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ access

    public PluginConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public ProfileStore profiles() {
        return profiles;
    }

    public BadgeService badges() {
        return badges;
    }

    public GearItems gear() {
        return gear;
    }

    public PassService passes() {
        return passes;
    }

    public SkiManager skis() {
        return skis;
    }

    public LiftManager lifts() {
        return lifts;
    }

    public CourseManager courses() {
        return courses;
    }

    public KioskManager kiosks() {
        return kiosks;
    }

    /** Extra yaw applied to the skier's head item (config visuals.head-yaw-offset). */
    public float headYawOffset() {
        return (float) getConfig().getDouble("visuals.head-yaw-offset", 0.0);
    }
}
