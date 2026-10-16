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
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import io.papermc.paper.entity.TeleportFlag;
import com.powderpass.backrooms.cutscene.Cutscene;
import com.powderpass.backrooms.cutscene.Transitions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Level 4's lifts. Every tower has one car, which is in one place at a time: the shaft is a stack of identical 3x3
 * car rooms, one per floor, and the car is "at" the floor whose doors open. Everywhere else the landing doors are
 * shut: two sliding steel panels (block displays) in the doorway, with a real barrier behind them, so nobody walks
 * into a car that is not there and nobody steps out of a moving one.
 *
 * <ul>
 *   <li><b>Call buttons</b> beside every landing door (an Interaction hitbox, a lit plate) bring the car; a hall
 *   indicator over them shows where it is and which way it is going. Calls made while the car is busy queue.</li>
 *   <li><b>Inside</b> a panel carries one button per floor (right-click it; sneaking in the car opens the same
 *   choice as a menu). An indicator over the door counts the floors.</li>
 *   <li><b>A ride</b> closes the doors (they will not close on someone standing in the doorway: they bounce back
 *   open), runs the motor for a time scaled by the distance, carries everyone inside with a relative teleport into
 *   the same spot of the destination car (camera, motion and look untouched, so it is seamless), dings and opens.
 *   Any number of people can ride; whoever is inside when the car is called elsewhere goes with it.</li>
 *   <li><b>Stray rides</b>: on each ride there is a chance ({@code levels.level4.generation.lift-stray-chance},
 *   default 10%) that the doors open somewhere else entirely, a random other enabled, loaded level, shown as the
 *   elevator cutscene when cutscenes are on (otherwise the normal safe teleport with arrival effects). Return
 *   points are untouched.</li>
 * </ul>
 *
 * <p>Door panels, buttons and indicators exist only near players (spawned and removed as they move, never saved
 * with the world); the doorway barriers are reconciled with the car state whenever a player comes near, so a crash
 * or restart cannot leave a door in the wrong state for long. Cars start at the lobby; a player found inside a car
 * room where the car is not (after a restart, say) simply has the car there.</p>
 *
 * <p>The service also keeps the night honest: a player who goes over an edge and falls far below the towers is
 * taken by the drop (killed, so the configured death handling applies) instead of landing alive on the bedrock
 * far below; creative and spectator players are put back on the nearest floor instead.</p>
 */
public final class LiftService implements Listener {
    /** Ticks for the doors to slide. */
    static final int DOOR_TICKS = 12;
    /** Ticks the doors stay open after the car arrives before closing by themselves. */
    public static final int DWELL = 100;
    private static final double NEAR = 44;

    private final BackroomsPlugin plugin;
    private final Map<String, Car> cars = new HashMap<>();
    private final Map<String, Landing> landings = new HashMap<>();
    private final Map<UUID, Long> hinted = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private DoubleSupplier roll = () -> ThreadLocalRandom.current().nextDouble();
    private int rides;
    private long tick;
    private NamespacedKey partKey;

    /** One car: where it is, whether its doors are open, and the calls it still has to answer. */
    static final class Car {
        final OfficeGenerator.Lift lift;
        int floor;
        boolean open;
        boolean moving;
        int heading; // +1 up, -1 down, 0 idle
        int target = -1;
        long closeAt = -1;
        final Deque<Integer> calls = new ArrayDeque<>();

        Car(OfficeGenerator.Lift lift) {
            this.lift = lift;
        }
    }

    /** The service's entities at one landing (door panels, call button, indicators), spawned while players are near. */
    private static final class Landing {
        final String key;
        final World world;
        final OfficeGenerator.Lift lift;
        final int floor;
        BlockDisplay left, right, lamp;
        TextDisplay hall, inside;
        Interaction button;
        Boolean shownOpen;
        boolean called;
        String text = "";

        Landing(String key, World world, OfficeGenerator.Lift lift, int floor) {
            this.key = key;
            this.world = world;
            this.lift = lift;
            this.floor = floor;
        }

        List<Entity> all() {
            List<Entity> out = new ArrayList<>();
            for (Entity e : new Entity[]{left, right, lamp, hall, inside, button}) if (e != null) out.add(e);
            return out;
        }
    }

    public LiftService(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        partKey = Keys.of("lift_part");
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::watch, 40L, 10L));
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickCars, 1L, 1L));
    }

    public void stop() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
        for (Landing l : landings.values()) for (Entity e : l.all()) e.remove();
        landings.clear();
        for (Car c : cars.values()) if (c.moving) for (Player p : plugin.getServer().getOnlinePlayers()) plugin.sessions().get(p).inTransition = false;
        cars.clear();
    }

    /** Test hook: the source of the stray roll (values below the stray chance send the car somewhere else). */
    public void setRoll(DoubleSupplier roll) {
        this.roll = roll == null ? () -> ThreadLocalRandom.current().nextDouble() : roll;
    }

    /** Rides started since the service was created. */
    public int rides() {
        return rides;
    }

    /** The floor the car of this lift is at (or moving from), for tests and the indicators. */
    public int carFloor(OfficeGenerator.Lift lift) {
        return car(lift).floor;
    }

    public boolean doorsOpen(OfficeGenerator.Lift lift) {
        return car(lift).open;
    }

    private OfficeGenerator gen() {
        if (!plugin.worlds().available(Level.LEVEL_4)) return null;
        LevelGenerator g = plugin.worlds().generator(Level.LEVEL_4);
        return g instanceof OfficeGenerator og ? og : null;
    }

    private boolean inLevel4(World w) {
        return plugin.worlds().levelOf(w) == Level.LEVEL_4;
    }

    private static String key(OfficeGenerator.Lift l) {
        return l.cx() + "," + l.cz();
    }

    private Car car(OfficeGenerator.Lift l) {
        return cars.computeIfAbsent(key(l), k -> new Car(l));
    }

    // ------------------------------------------------------------------ controls

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onButton(PlayerInteractEntityEvent e) {
        if (handle(e.getPlayer(), e.getRightClicked())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onButtonHit(PrePlayerAttackEntityEvent e) {
        if (handle(e.getPlayer(), e.getAttacked())) e.setCancelled(true);
    }

    /** A car panel button ({@code lift:<floor>}) or a landing call button ({@code liftcall:<floor>}). */
    private boolean handle(Player p, Entity en) {
        String action = en.getPersistentDataContainer().get(Keys.ACTION, PersistentDataType.STRING);
        if (action == null || !inLevel4(en.getWorld())) return false;
        try {
            if (action.startsWith("lift:")) {
                press(p, en.getLocation(), Integer.parseInt(action.substring(5)));
                return true;
            }
            if (action.startsWith("liftcall:")) {
                call(p, en.getLocation(), Integer.parseInt(action.substring(9)));
                return true;
            }
        } catch (NumberFormatException ignored) {
            return false;
        }
        return false;
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
        int size = lift.floors() <= 9 ? 9 : 18;
        m.inv = plugin.getServer().createInventory(m, size, BackroomsConfig.mm("<dark_gray>Lift — choose a floor"));
        for (int k = 0; k < lift.floors() && k < size; k++) {
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
        if (slot < 0 || slot >= m.lift.floors() || slot >= e.getInventory().getSize()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            p.closeInventory();
            press(p, p.getLocation(), slot);
        });
    }

    private static String label(int floor) {
        return floor == 0 ? "L" : String.valueOf(floor);
    }

    // ------------------------------------------------------------------ rides

    /** Players inside the car room of {@code floor} (not spectators, not in a cutscene, alive). */
    private List<Player> inside(World w, OfficeGenerator.Lift lift, int floor) {
        List<Player> out = new ArrayList<>();
        for (Player o : w.getPlayers()) {
            if (o.getGameMode() == GameMode.SPECTATOR || o.isDead()) continue;
            if (plugin.cutscenes() != null && plugin.cutscenes().playing(o)) continue;
            Location l = o.getLocation();
            if (lift.inCar(l.getX(), l.getZ()) && lift.floorAt(l.getY()) == floor) out.add(o);
        }
        return out;
    }

    /** Someone standing in the doorway of {@code floor} (the doors will not close on them). */
    private boolean doorwayBlocked(World w, OfficeGenerator.Lift lift, int floor) {
        for (Player o : w.getPlayers()) {
            if (o.getGameMode() == GameMode.SPECTATOR) continue;
            Location l = o.getLocation();
            if (Math.abs(l.getY() - lift.feetY(floor)) > 1.2) continue;
            // the player's box (0.3 either side of their feet) overlaps the doorway column
            if (l.getX() + 0.3 > lift.doorX() && l.getX() - 0.3 < lift.doorX() + 1
                    && l.getZ() + 0.3 > lift.doorZ() && l.getZ() - 0.3 < lift.doorZ() + 1) return true;
        }
        return false;
    }

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
        int from = lift.floorAt(p.getLocation().getY());
        if (from < 0) from = lift.floorAt(at.getY());
        if (from < 0 || target < 0 || target >= lift.floors()) return false;
        Car car = car(lift);
        if (car.moving) {
            p.sendActionBar(BackroomsConfig.mm("<gray>The lift is already moving."));
            return false;
        }
        if (car.floor != from) car.floor = from; // they are in the car room, so the car is here
        Location door = doorCentre(p.getWorld(), lift, from);
        if (target == from) {
            sound(p, "lift.ding", Sound.BLOCK_NOTE_BLOCK_BELL, door, 0.8f, 1.6f);
            if (!car.open) openDoors(p.getWorld(), car);
            p.sendActionBar(BackroomsConfig.mm("<gray>You are on floor <white>" + label(from) + "</white>."));
            return false;
        }
        if (inside(p.getWorld(), lift, from).isEmpty()) {
            p.sendActionBar(BackroomsConfig.mm("<gray>Step inside the car first."));
            return false;
        }
        return move(p.getWorld(), car, target, p);
    }

    /** A call button at a landing: bring the car (or open it if it is already here). */
    public boolean call(Player p, Location at, int floor) {
        OfficeGenerator g = gen();
        if (g == null || !inLevel4(p.getWorld())) return false;
        OfficeGenerator.Lift lift = g.liftNear(at.getX(), at.getZ());
        if (floor < 0 || floor >= lift.floors()) return false;
        Car car = car(lift);
        World w = p.getWorld();
        Location door = doorCentre(w, lift, floor);
        p.playSound(at, Sound.BLOCK_STONE_BUTTON_CLICK_ON, SoundCategory.BLOCKS, 0.6f, 1.4f);
        if (!car.moving && car.floor == floor) {
            if (!car.open) {
                sound(p, "lift.ding", Sound.BLOCK_NOTE_BLOCK_BELL, door, 0.8f, 1.6f);
                openDoors(w, car);
            } else {
                car.closeAt = tick + DWELL; // hold the doors
            }
            return true;
        }
        if (car.moving || car.open && !inside(w, lift, car.floor).isEmpty()) {
            if (!car.calls.contains(floor)) car.calls.add(floor);
            p.sendActionBar(BackroomsConfig.mm("<gray>Called. The lift is on <white>" + label(car.floor) + "</white>."));
            return true;
        }
        p.sendActionBar(BackroomsConfig.mm("<gray>The lift is coming " + (car.floor < floor ? "up" : "down") + " from <white>" + label(car.floor) + "</white>."));
        return move(w, car, floor, null);
    }

    /**
     * Send a car to {@code target}: doors shut (waiting for the doorway to clear), the motor runs, whoever is inside
     * is carried, the doors open at the other end.
     */
    private boolean move(World w, Car car, int target, Player who) {
        if (car.moving || target == car.floor) return false;
        car.moving = true;
        car.heading = Integer.signum(target - car.floor);
        car.target = target;
        car.calls.remove(target);
        rides++;
        int start = car.floor;
        OfficeGenerator.Lift lift = car.lift;
        Location door = doorCentre(w, lift, start);
        List<Player> riders = inside(w, lift, start);
        for (Player r : riders) plugin.sessions().get(r).inTransition = true;
        for (Player r : riders) r.sendActionBar(BackroomsConfig.mm("<#ffb347>" + (target > start ? "▲" : "▼") + " <white>" + label(start) + " → " + label(target)));
        int wait = 0;
        if (car.open) {
            wait = closeDoors(w, car, riders, 0);
            if (wait < 0) { // the doorway never cleared: give up this ride
                car.moving = false;
                car.heading = 0;
                for (Player r : riders) plugin.sessions().get(r).inTransition = false;
                if (who != null) who.sendActionBar(BackroomsConfig.mm("<gray>Something is in the doorway."));
                return false;
            }
        }
        int travel = 24 + 10 * Math.abs(target - start);
        final int delay = wait;
        later(delay + 12, () -> {
            // whoever slipped in while the doors were closing rides too
            for (Player r : inside(w, lift, start)) {
                if (riders.contains(r)) continue;
                riders.add(r);
                plugin.sessions().get(r).inTransition = true;
            }
            for (Player r : riders) if (r.isOnline()) sound(r, "lift.motor", Sound.ENTITY_MINECART_INSIDE, r.getLocation(), 0.35f, 0.6f);
            for (Player r : riders) if (r.isOnline()) r.playSound(r.getLocation(), Sound.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 0.25f, 0.5f);
        });
        // the indicators count the floors as it goes
        int floors = Math.abs(target - start);
        for (int k = 1; k <= floors; k++) {
            int f = start + car.heading * k;
            later(delay + 12 + 14 + 10 * k, () -> {
                if (car.moving && f != target) car.floor = f;
            });
        }
        later(delay + 12 + travel, () -> arrive(w, car, start, target, riders));
        return true;
    }

    private void arrive(World w, Car car, int from, int target, List<Player> riders) {
        OfficeGenerator.Lift lift = car.lift;
        OfficeGenerator g = gen();
        double stray = g == null || riders.isEmpty() ? 0 : g.liftStrayChance();
        Level elsewhere = roll.getAsDouble() < stray ? randomLevel() : null;
        List<Player> arrived = new ArrayList<>();
        for (Player r : riders) {
            Session s = plugin.sessions().get(r);
            stopMotor(r);
            Location l = r.getLocation();
            if (!r.isOnline() || r.isDead() || r.getWorld() != w || !lift.inCar(l.getX(), l.getZ()) || lift.floorAt(l.getY()) != from) {
                s.inTransition = false;
                continue;
            }
            if (elsewhere != null) {
                strayTo(r, elsewhere);
                continue;
            }
            carry(r, OfficeGenerator.FH * (target - from));
            s.inTransition = false;
            arrived.add(r);
        }
        car.floor = target;
        car.moving = false;
        car.heading = 0;
        car.target = -1;
        Location door = doorCentre(w, lift, target);
        for (Player r : arrived) {
            sound(r, "lift.ding", Sound.BLOCK_NOTE_BLOCK_BELL, door, 0.9f, 1.6f);
            r.playSound(r.getLocation(), Sound.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 0.2f, 0.5f); // the car settles
        }
        if (arrived.isEmpty()) for (Player o : near(w, door, 16)) sound(o, "lift.ding", Sound.BLOCK_NOTE_BLOCK_BELL, door, 0.7f, 1.6f);
        later(10, () -> {
            if (!car.moving && car.floor == target && !car.open) openDoors(w, car);
        });
    }

    /** Move a rider by {@code dy} with a fully relative teleport: the client keeps its camera, look and motion. */
    private void carry(Player r, double dy) {
        r.setFallDistance(0);
        Location to = r.getLocation().add(0, dy, 0);
        boolean ok;
        try {
            ok = r.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN, TeleportFlag.Relative.VELOCITY_X, TeleportFlag.Relative.VELOCITY_Y,
                    TeleportFlag.Relative.VELOCITY_Z, TeleportFlag.Relative.YAW, TeleportFlag.Relative.PITCH,
                    TeleportFlag.Relative.X, TeleportFlag.Relative.Y, TeleportFlag.Relative.Z);
        } catch (RuntimeException e) {
            ok = false;
        }
        if (!ok) r.teleport(to);
    }

    /** Called every tick: doors that have stood open long enough close, and queued calls are answered. */
    private void tickCars() {
        tick++;
        World w = plugin.worlds().world(Level.LEVEL_4);
        if (w == null) return;
        for (Car car : cars.values()) {
            if (car.moving) continue;
            if (car.open && car.closeAt >= 0 && tick >= car.closeAt) {
                if (!car.calls.isEmpty()) {
                    int next = car.calls.poll();
                    if (next == car.floor) {
                        car.closeAt = tick + DWELL;
                        continue;
                    }
                    if (!move(w, car, next, null)) car.calls.addFirst(next);
                } else if (closeDoors(w, car, inside(w, car.lift, car.floor), 0) < 0) {
                    car.closeAt = tick + 40;
                }
            } else if (!car.open && !car.calls.isEmpty()) {
                int next = car.calls.poll();
                if (next == car.floor) openDoors(w, car);
                else if (!move(w, car, next, null)) car.calls.addFirst(next);
            }
        }
    }

    /**
     * Shut the doors of the car's floor. If someone stands in the doorway the doors bounce back and try again a
     * moment later; returns the ticks until they are shut, or -1 if the doorway is blocked right now.
     */
    private int closeDoors(World w, Car car, List<Player> riders, int attempt) {
        OfficeGenerator.Lift lift = car.lift;
        int f = car.floor;
        if (doorwayBlocked(w, lift, f)) {
            Location door = doorCentre(w, lift, f);
            for (Player o : near(w, door, 10)) o.playSound(door, Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.BLOCKS, 0.4f, 0.6f);
            car.closeAt = tick + 30;
            return -1;
        }
        car.open = false;
        car.closeAt = -1;
        Location door = doorCentre(w, lift, f);
        for (Player o : near(w, door, 16)) sound(o, "lift.door", Sound.BLOCK_IRON_DOOR_CLOSE, door, 0.8f, 0.8f);
        setBarrier(w, lift, f, true);
        Landing l = landings.get(landingKey(lift, f));
        if (l != null) slide(l, false, true);
        return DOOR_TICKS;
    }

    private void openDoors(World w, Car car) {
        OfficeGenerator.Lift lift = car.lift;
        int f = car.floor;
        car.open = true;
        car.closeAt = tick + DWELL;
        Location door = doorCentre(w, lift, f);
        for (Player o : near(w, door, 16)) sound(o, "lift.door", Sound.BLOCK_IRON_DOOR_OPEN, door, 0.8f, 0.8f);
        setBarrier(w, lift, f, false);
        Landing l = landings.get(landingKey(lift, f));
        if (l != null) slide(l, true, true);
    }

    /** The doorway's two blocks: a barrier while the doors are shut, air while they are open. */
    private void setBarrier(World w, OfficeGenerator.Lift lift, int floor, boolean closed) {
        int y = lift.feetY(floor);
        if (!loaded(w, lift.doorX(), lift.doorZ())) return;
        for (int dy = 0; dy < 2; dy++) {
            org.bukkit.block.Block b = w.getBlockAt(lift.doorX(), y + dy, lift.doorZ());
            Material m = b.getType();
            if (m != Material.AIR && m != Material.BARRIER && m != Material.CAVE_AIR) continue; // never touch anything else
            Material want = closed ? Material.BARRIER : Material.AIR;
            if (m != want) b.setType(want, false);
        }
    }

    /** The chunk is loaded, or a player stands close enough that it must be (never load one just for a door). */
    private static boolean loaded(World w, int x, int z) {
        if (w.isChunkLoaded(x >> 4, z >> 4)) return true;
        for (Player o : w.getPlayers()) {
            Location l = o.getLocation();
            if (Math.abs(l.getX() - x) < 48 && Math.abs(l.getZ() - z) < 48) return true;
        }
        return false;
    }

    private Location doorCentre(World w, OfficeGenerator.Lift lift, int floor) {
        return new Location(w, lift.doorX() + 0.5, lift.feetY(floor) + 1, lift.doorZ() + 0.5);
    }

    private List<Player> near(World w, Location at, double r) {
        List<Player> out = new ArrayList<>();
        for (Player o : w.getPlayers()) if (o.getLocation().distanceSquared(at) <= r * r) out.add(o);
        return out;
    }

    private void later(long ticks, Runnable r) {
        plugin.getServer().getScheduler().runTaskLater(plugin, r, Math.max(1, ticks));
    }

    /** A random other level that is enabled and loaded, or null if there is none. */
    private Level randomLevel() {
        List<Level> options = new ArrayList<>();
        for (Level l : Level.values()) if (l != Level.LEVEL_4 && plugin.worlds().available(l)) options.add(l);
        if (options.isEmpty()) return null;
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }

    /**
     * The doors open somewhere else entirely: the elevator cutscene into another level (the world changes behind
     * black), or, without cutscenes, the normal safe teleport with arrival effects.
     */
    private void strayTo(Player p, Level level) {
        World w = plugin.worlds().world(level);
        Session s = plugin.sessions().get(p);
        if (w == null) {
            s.inTransition = false;
            return;
        }
        int[] spot = plugin.worlds().generator(level).safeSpot(p.getLocation().getBlockX(), p.getLocation().getBlockZ(), 0);
        Location target = new Location(w, spot[0] + 0.5, spot[1], spot[2] + 0.5, p.getLocation().getYaw(), 0);
        if (plugin.cutscenes() != null) {
            Cutscene cs = Transitions.elevator(plugin, p, target, level);
            cs.at(cs.duration() - 2, q -> q.sendActionBar(Component.text("The doors open on the wrong floor.")));
            if (plugin.cutscenes().play(p, cs)) return;
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 70, 0, false, false));
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.AMBIENT, 0.8f, 0.6f);
        plugin.teleports().teleportSafe(p, target, l -> {
            plugin.sessions().arrived(p, level);
            sound(p, "lift.ding", Sound.BLOCK_NOTE_BLOCK_BELL, l, 0.9f, 1.3f);
            p.sendActionBar(Component.text("The doors open on the wrong floor."));
        }).thenAccept(ok -> {
            if (!ok) s.inTransition = false;
        });
    }

    // ------------------------------------------------------------------ landing entities

    private static String landingKey(OfficeGenerator.Lift lift, int floor) {
        return lift.cx() + "," + lift.cz() + "," + floor;
    }

    /** Spawn the landings near players, remove the ones nobody is near, keep doors and indicators in step. */
    private void syncLandings(OfficeGenerator g, World w) {
        Set<String> want = new HashSet<>();
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            OfficeGenerator.Lift lift = g.liftNear(l.getX(), l.getZ());
            double dx = lift.doorX() + 0.5 - l.getX(), dz = lift.doorZ() + 0.5 - l.getZ();
            if (dx * dx + dz * dz > NEAR * NEAR) continue;
            Car car = car(lift);
            int pf = (int) Math.floor((l.getY() - OfficeGenerator.BASE - 1) / OfficeGenerator.FH + 0.2);
            // someone in a car room where the car is not (a restart, a teleport): the car is there
            int in = lift.inCar(l.getX(), l.getZ()) ? lift.floorAt(l.getY()) : -1;
            if (in >= 0 && in != car.floor && !car.moving && inside(w, lift, car.floor).isEmpty()) {
                car.floor = in;
                car.open = false;
            }
            for (int f = Math.max(0, pf - 2); f <= Math.min(lift.floors() - 1, pf + 2); f++) {
                String k = landingKey(lift, f);
                want.add(k);
                Landing land = landings.get(k);
                if (land == null) {
                    land = spawnLanding(w, lift, f, k);
                    landings.put(k, land);
                }
                boolean open = car.floor == f && car.open && !car.moving;
                if (land.shownOpen == null || land.shownOpen != open) {
                    setBarrier(w, lift, f, !open);
                    slide(land, open, land.shownOpen != null);
                }
                updateText(land, car);
            }
        }
        landings.entrySet().removeIf(e -> {
            if (want.contains(e.getKey())) return false;
            for (Entity en : e.getValue().all()) en.remove();
            return true;
        });
    }

    private Landing spawnLanding(World w, OfficeGenerator.Lift lift, int f, String k) {
        Landing land = new Landing(k, w, lift, f);
        double y = lift.feetY(f);
        BlockData panel = Material.IRON_BLOCK.createBlockData();
        Location doorAt = new Location(w, lift.doorX(), y, lift.doorZ() + 0.45);
        land.left = w.spawn(doorAt, BlockDisplay.class, d -> prep(d, panel));
        land.right = w.spawn(doorAt, BlockDisplay.class, d -> prep(d, panel));
        // the call button: a dark plate with a lamp, beside the door on the landing side
        Location btn = new Location(w, lift.doorX() + 1.5, y + 1.0, lift.doorZ() - 0.03);
        land.lamp = w.spawn(btn, BlockDisplay.class, d -> {
            prep(d, Material.POLISHED_BLACKSTONE.createBlockData());
            cosmetic(() -> d.setTransformation(new Transformation(new Vector3f(-0.09f, -0.16f, 0f), new AxisAngle4f(), new Vector3f(0.18f, 0.32f, 0.04f), new AxisAngle4f())));
        });
        land.button = w.spawn(btn.clone().add(0, -0.2, -0.15), Interaction.class, i -> {
            i.setInteractionWidth(0.45f);
            i.setInteractionHeight(0.45f);
            i.setPersistent(false);
            i.getPersistentDataContainer().set(Keys.ACTION, PersistentDataType.STRING, "liftcall:" + f);
            i.getPersistentDataContainer().set(partKey, PersistentDataType.BYTE, (byte) 1);
        });
        land.hall = w.spawn(new Location(w, lift.doorX() + 1.5, y + 1.55, lift.doorZ() - 0.04, 180f, 0f), TextDisplay.class, t -> prepText(t));
        land.inside = w.spawn(new Location(w, lift.doorX() + 0.5, y + 2.25, lift.doorZ() + 1.03, 0f, 0f), TextDisplay.class, t -> prepText(t));
        return land;
    }

    private void prep(BlockDisplay d, BlockData data) {
        d.setPersistent(false);
        d.getPersistentDataContainer().set(partKey, PersistentDataType.BYTE, (byte) 1);
        cosmetic(() -> {
            d.setBlock(data);
            d.setViewRange(0.5f);
        });
    }

    private void prepText(TextDisplay t) {
        t.setPersistent(false);
        t.getPersistentDataContainer().set(partKey, PersistentDataType.BYTE, (byte) 1);
        cosmetic(() -> {
            t.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
            t.setBackgroundColor(org.bukkit.Color.fromARGB(200, 10, 10, 10));
            t.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.5f, 0.5f, 0.5f), new AxisAngle4f()));
            t.setViewRange(0.3f);
        });
    }

    /** Purely visual display settings: a server (or test mock) that lacks one must not stop the lift working. */
    private static void cosmetic(Runnable r) {
        try {
            r.run();
        } catch (UnsupportedOperationException e) {
            // not supported here; the entity still exists and does its job
        }
    }

    /** Slide the two panels shut (side by side in the doorway) or open (into the wall pockets either side). */
    private void slide(Landing land, boolean open, boolean animate) {
        land.shownOpen = open;
        float off = open ? 0.48f : 0f;
        Vector3f scale = new Vector3f(0.5f, 2f, 0.1f);
        setPanel(land.left, new Vector3f(-off, 0, 0), scale, animate);
        setPanel(land.right, new Vector3f(0.5f + off, 0, 0), scale, animate);
    }

    private void setPanel(BlockDisplay d, Vector3f t, Vector3f scale, boolean animate) {
        if (d == null || !d.isValid()) return;
        cosmetic(() -> {
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(animate ? DOOR_TICKS : 0);
            d.setTransformation(new Transformation(t, new AxisAngle4f(), scale, new AxisAngle4f()));
        });
    }

    private void updateText(Landing land, Car car) {
        // the call lamp is lit while this landing's call waits, or while the car is on its way here
        boolean called = car.calls.contains(land.floor) || car.moving && car.target == land.floor;
        if (land.lamp != null && land.lamp.isValid() && land.called != called) {
            land.called = called;
            BlockDisplay lamp = land.lamp;
            cosmetic(() -> {
                lamp.setBlock((called ? Material.SHROOMLIGHT : Material.POLISHED_BLACKSTONE).createBlockData());
                lamp.setBrightness(called ? new org.bukkit.entity.Display.Brightness(15, 15) : null);
            });
        }
        String arrow = car.heading > 0 ? "▲ " : car.heading < 0 ? "▼ " : "";
        String text = arrow + label(car.floor);
        if (text.equals(land.text)) return;
        land.text = text;
        Component c = BackroomsConfig.mm("<#ffb347>" + text);
        for (TextDisplay t : new TextDisplay[]{land.hall, land.inside}) if (t != null && t.isValid()) cosmetic(() -> t.text(c));
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
        syncLandings(g, w);
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
