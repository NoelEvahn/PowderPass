package com.powderpass.backrooms.world;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.display.Keys;
import com.powderpass.backrooms.gen.CustomBlocks;
import com.powderpass.backrooms.gen.LevelGenerator;
import com.powderpass.backrooms.gen.levels.OfficeGenerator;
import com.powderpass.backrooms.player.Session;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Level 4's lifts. Every tower has one car, stacked in its shaft as a 3x3 car room on every floor. Inside the car a
 * steel panel carries one button per floor (right-click it; sneaking in the car opens the same choice as a menu).
 * A ride closes the doors (client-side door blocks, so nothing in the world changes), runs the motor for a time
 * scaled by the distance, moves everyone in the car to the same spot in the chosen floor's car, and opens the doors
 * with a ding.
 *
 * <p>On every ride there is a chance ({@code levels.level4.generation.lift-stray-chance}, default 10%) that when
 * the doors open the riders are somewhere else entirely: a random other Backrooms level that is enabled and loaded.
 * They arrive through the normal safe-teleport path at that level's nearest safe spot, with the usual arrival
 * effects; their return point is untouched.</p>
 *
 * <p>The service also keeps the night honest: a player who goes over an edge and falls far below the towers is
 * taken by the drop (killed, so the configured death handling applies) instead of landing alive on the bedrock
 * far below; creative and spectator players are put back on the nearest floor instead.</p>
 */
public final class LiftService implements Listener {
    private final BackroomsPlugin plugin;
    private final Set<String> moving = new HashSet<>();
    private final Map<UUID, Long> hinted = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private DoubleSupplier roll = () -> ThreadLocalRandom.current().nextDouble();
    private int rides;

    public LiftService(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::watch, 40L, 10L));
    }

    public void stop() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        moving.clear();
    }

    /** Test hook: the source of the stray roll (values below the stray chance send the car somewhere else). */
    public void setRoll(DoubleSupplier roll) {
        this.roll = roll == null ? () -> ThreadLocalRandom.current().nextDouble() : roll;
    }

    /** Rides started since the service was created. */
    public int rides() {
        return rides;
    }

    private OfficeGenerator gen() {
        if (!plugin.worlds().available(Level.LEVEL_4)) return null;
        LevelGenerator g = plugin.worlds().generator(Level.LEVEL_4);
        return g instanceof OfficeGenerator og ? og : null;
    }

    private boolean inLevel4(World w) {
        return plugin.worlds().levelOf(w) == Level.LEVEL_4;
    }

    // ------------------------------------------------------------------ controls

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onButton(PlayerInteractEntityEvent e) {
        int floor = button(e.getRightClicked());
        if (floor < 0) return;
        e.setCancelled(true);
        press(e.getPlayer(), e.getRightClicked().getLocation(), floor);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onButtonHit(PrePlayerAttackEntityEvent e) {
        int floor = button(e.getAttacked());
        if (floor < 0) return;
        e.setCancelled(true);
        press(e.getPlayer(), e.getAttacked().getLocation(), floor);
    }

    private int button(Entity en) {
        String action = en.getPersistentDataContainer().get(Keys.ACTION, PersistentDataType.STRING);
        if (action == null || !action.startsWith("lift:") || !inLevel4(en.getWorld())) return -1;
        try {
            return Integer.parseInt(action.substring(5));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Sneaking inside a car opens the floor list as a menu (works without display entities too). */
    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking() || !inLevel4(e.getPlayer().getWorld())) return;
        OfficeGenerator g = gen();
        if (g == null) return;
        Location l = e.getPlayer().getLocation();
        OfficeGenerator.Lift lift = g.liftAt(l.getX(), l.getY(), l.getZ());
        if (lift == null) return;
        openMenu(e.getPlayer(), lift, lift.floorAt(l.getY()));
    }

    private static final class Menu implements InventoryHolder {
        final OfficeGenerator.Lift lift;
        final int from;
        Inventory inv;

        Menu(OfficeGenerator.Lift lift, int from) {
            this.lift = lift;
            this.from = from;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public void openMenu(Player p, OfficeGenerator.Lift lift, int from) {
        Menu m = new Menu(lift, from);
        m.inv = plugin.getServer().createInventory(m, 9, BackroomsConfig.mm("<dark_gray>Lift — choose a floor"));
        for (int k = 0; k < lift.floors() && k < 9; k++) {
            ItemStack it = new ItemStack(k == from ? Material.LIME_STAINED_GLASS_PANE : Material.IRON_TRAPDOOR);
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.displayName(BackroomsConfig.mm((k == from ? "<#ffb347>" : "<white>") + "Floor " + label(k) + (k == from ? " <gray>(here)" : "")));
                it.setItemMeta(meta);
            }
            m.inv.setItem(k, it);
        }
        p.openInventory(m.inv);
    }

    @EventHandler
    public void onMenu(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= m.lift.floors() || slot >= 9) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            p.closeInventory();
            press(p, p.getLocation(), slot);
        });
    }

    private static String label(int floor) {
        return floor == 0 ? "L" : String.valueOf(floor);
    }

    // ------------------------------------------------------------------ rides

    /**
     * A floor button pressed at {@code at} (a panel inside a car, or the rider's own position). Returns true when a
     * ride started.
     */
    public boolean press(Player p, Location at, int target) {
        OfficeGenerator g = gen();
        if (g == null || !inLevel4(p.getWorld())) return false;
        OfficeGenerator.Lift lift = g.liftAt(at.getX(), at.getY(), at.getZ());
        if (lift == null) lift = g.liftAt(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ());
        if (lift == null) return false;
        int from = lift.floorAt(at.getY());
        if (from < 0) from = lift.floorAt(p.getLocation().getY());
        if (from < 0 || target < 0 || target >= lift.floors()) return false;
        String key = lift.cx() + "," + lift.cz();
        Location door = new Location(p.getWorld(), lift.doorX() + 0.5, lift.feetY(from) + 1, lift.doorZ() + 0.5);
        if (moving.contains(key)) {
            p.sendActionBar(BackroomsConfig.mm("<gray>The lift is already moving."));
            return false;
        }
        if (target == from) {
            sound(p, "lift.ding", Sound.BLOCK_NOTE_BLOCK_BELL, door, 0.8f, 1.6f);
            p.sendActionBar(BackroomsConfig.mm("<gray>You are on floor <white>" + label(from) + "</white>."));
            return false;
        }
        List<Player> riders = new ArrayList<>();
        for (Player o : p.getWorld().getPlayers()) {
            if (o.getGameMode() == GameMode.SPECTATOR) continue;
            Location l = o.getLocation();
            if (lift.inCar(l.getX(), l.getZ()) && lift.floorAt(l.getY()) == from) riders.add(o);
        }
        if (riders.isEmpty()) {
            p.sendActionBar(BackroomsConfig.mm("<gray>Step inside the car first."));
            return false;
        }
        moving.add(key);
        rides++;
        final OfficeGenerator.Lift car = lift;
        final int start = from;
        World w = p.getWorld();
        for (Player r : riders) plugin.sessions().get(r).inTransition = true;
        doors(w, car, start, true);
        for (Player r : riders) {
            sound(r, "lift.door", Sound.BLOCK_IRON_DOOR_CLOSE, door, 0.8f, 0.8f);
            r.sendActionBar(BackroomsConfig.mm("<#ffb347>▲▼ <white>" + label(start) + " → " + label(target)));
        }
        int travel = 24 + 10 * Math.abs(target - start);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Player r : riders) if (r.isOnline()) sound(r, "lift.motor", Sound.ENTITY_MINECART_INSIDE, r.getLocation(), 0.35f, 0.6f);
        }, 12L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> arrive(w, car, start, target, riders, key), 12L + travel);
        return true;
    }

    private void arrive(World w, OfficeGenerator.Lift car, int from, int target, List<Player> riders, String key) {
        moving.remove(key);
        doors(w, car, from, false);
        OfficeGenerator g = gen();
        double stray = g == null ? 0 : g.liftStrayChance();
        Level elsewhere = roll.getAsDouble() < stray ? randomLevel() : null;
        List<Player> arrived = new ArrayList<>();
        for (Player r : riders) {
            Session s = plugin.sessions().get(r);
            stopMotor(r);
            Location l = r.getLocation();
            if (!r.isOnline() || r.getWorld() != w || !car.inCar(l.getX(), l.getZ()) || car.floorAt(l.getY()) != from) {
                s.inTransition = false; // stepped out before the doors closed for good
                continue;
            }
            if (elsewhere != null) {
                strayTo(r, elsewhere);
                continue;
            }
            Location to = l.clone();
            to.setY(l.getY() + OfficeGenerator.FH * (target - from));
            r.setFallDistance(0);
            r.teleport(to);
            s.inTransition = false;
            arrived.add(r);
        }
        if (arrived.isEmpty()) return;
        doors(w, car, target, true);
        Location door = new Location(w, car.doorX() + 0.5, car.feetY(target) + 1, car.doorZ() + 0.5);
        for (Player r : arrived) sound(r, "lift.ding", Sound.BLOCK_NOTE_BLOCK_BELL, door, 0.9f, 1.6f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            doors(w, car, target, false);
            for (Player r : arrived) if (r.isOnline()) sound(r, "lift.door", Sound.BLOCK_IRON_DOOR_OPEN, door, 0.8f, 0.8f);
        }, 10L);
    }

    /** A random other level that is enabled and loaded, or null if there is none. */
    private Level randomLevel() {
        List<Level> options = new ArrayList<>();
        for (Level l : Level.values()) if (l != Level.LEVEL_4 && plugin.worlds().available(l)) options.add(l);
        if (options.isEmpty()) return null;
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }

    /** The doors open somewhere else entirely: the normal safe teleport into another level, with arrival effects. */
    private void strayTo(Player p, Level level) {
        World w = plugin.worlds().world(level);
        Session s = plugin.sessions().get(p);
        if (w == null) {
            s.inTransition = false;
            return;
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 70, 0, false, false));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.AMBIENT, 0.8f, 0.6f);
        int[] spot = plugin.worlds().generator(level).safeSpot(p.getLocation().getBlockX(), p.getLocation().getBlockZ(), 0);
        Location target = new Location(w, spot[0] + 0.5, spot[1], spot[2] + 0.5, p.getLocation().getYaw(), 0);
        plugin.teleports().teleportSafe(p, target, l -> {
            plugin.sessions().arrived(p, level);
            sound(p, "lift.ding", Sound.BLOCK_NOTE_BLOCK_BELL, l, 0.9f, 1.3f);
            p.sendActionBar(Component.text("The doors open on the wrong floor."));
        }).thenAccept(ok -> {
            if (!ok) s.inTransition = false;
        });
    }

    /** Show the car's door closed (steel) or open to every player near it, client-side only. */
    private void doors(World w, OfficeGenerator.Lift car, int floor, boolean closed) {
        Location base = new Location(w, car.doorX(), car.feetY(floor), car.doorZ());
        BlockData steel = closed ? steel() : null;
        for (Player o : w.getPlayers()) {
            if (o.getLocation().distanceSquared(base) > 32 * 32) continue;
            for (int dy = 0; dy < 2; dy++) {
                Location l = base.clone().add(0, dy, 0);
                o.sendBlockChange(l, closed ? steel : l.getBlock().getBlockData());
            }
        }
    }

    private static BlockData steel() {
        try {
            return CustomBlocks.get("tower_elevator").data();
        } catch (RuntimeException e) {
            return Material.IRON_BLOCK.createBlockData();
        }
    }

    // ------------------------------------------------------------------ sound

    private String event(Player p, String id) {
        if (!plugin.pack().loaded(p) || plugin.audio() == null || plugin.audio().index().def(id) == null) return null;
        return plugin.audio().index().resolve(id, null, false);
    }

    private void sound(Player p, String id, Sound fallback, Location at, float volume, float pitch) {
        String ev = event(p, id);
        if (ev != null) p.playSound(at, "backrooms:" + ev, SoundCategory.BLOCKS, volume, 1f);
        else p.playSound(at, fallback, SoundCategory.BLOCKS, volume, pitch);
    }

    private void stopMotor(Player p) {
        String ev = event(p, "lift.motor");
        net.kyori.adventure.key.Key k = ev != null ? net.kyori.adventure.key.Key.key("backrooms", ev) : net.kyori.adventure.key.Key.key("minecraft", "entity.minecart.inside");
        p.stopSound(net.kyori.adventure.sound.SoundStop.namedOnSource(k, net.kyori.adventure.sound.Sound.Source.BLOCK));
    }

    // ------------------------------------------------------------------ hints and the drop

    private void watch() {
        OfficeGenerator g = gen();
        World w = plugin.worlds().world(Level.LEVEL_4);
        if (g == null || w == null) return;
        long now = plugin.sessions().now();
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            if (l.getY() < OfficeGenerator.BASE - 24) {
                fell(p, g);
                continue;
            }
            OfficeGenerator.Lift lift = g.liftAt(l.getX(), l.getY(), l.getZ());
            if (lift == null) continue;
            Long last = hinted.get(p.getUniqueId());
            if (last != null && now - last < 20 * 60) continue;
            hinted.put(p.getUniqueId(), now);
            p.sendActionBar(BackroomsConfig.mm("<gray>Lift: <white>right-click a floor button</white>, or <white>sneak</white> for the floor list."));
        }
    }

    private void fell(Player p, OfficeGenerator g) {
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
            if (p.getLocation().getY() > 8) return;
            int[] s = g.safeSpot(p.getLocation().getBlockX(), p.getLocation().getBlockZ(), 0);
            p.teleport(new Location(p.getWorld(), s[0] + 0.5, s[1], s[2] + 0.5, p.getLocation().getYaw(), 0));
            return;
        }
        if (p.isDead() || p.getHealth() <= 0) return;
        p.setHealth(0); // taken by the drop: the normal death handling (death.mode) applies
    }
}
