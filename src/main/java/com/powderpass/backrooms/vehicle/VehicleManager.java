package com.powderpass.backrooms.vehicle;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.display.GarageProps;
import com.powderpass.backrooms.display.Keys;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.LevelGenerator;
import com.powderpass.backrooms.gen.levels.HabitableGenerator;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.SoundStop;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Drivable cars of Level 1. Right-clicking a parked car turns its decoration into a live car (the decoration is
 * hidden while the car exists): the first rider drives, up to three more ride along. W/S throttle and reverse,
 * A/D steer, Space brakes, Sprint sounds the horn and Sneak gets out. An abandoned car stays where it stopped for
 * a few minutes, then the decoration returns to its stall. Cars never leave Level 1; quitting, dying, changing
 * world and disabling the plugin always put riders down safely and remove every entity.
 */
public final class VehicleManager implements Listener {
    /** How long an empty car stays where it was left before it is back in its stall (ticks). */
    public static final long ABANDON_TICKS = 20L * 60 * 5;

    private final BackroomsPlugin plugin;
    private final NamespacedKey tag;
    private final Map<String, Car> cars = new LinkedHashMap<>();
    private final Map<UUID, Car> byEntity = new HashMap<>();
    private final Map<UUID, Car> byRider = new HashMap<>();
    private final Map<UUID, Input> inputs = new HashMap<>();
    /** Parked-car footprints per chunk ({x, z, yaw, specKey}), for collisions between cars. */
    private final Map<Long, List<DecorSpec>> parked = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, List<DecorSpec>> e) {
            return size() > 256;
        }
    };
    private final Set<UUID> ejecting = new HashSet<>();
    private final SteamLeaks steam;
    private BukkitTask task;
    private long tick;

    public VehicleManager(BackroomsPlugin plugin) {
        this.plugin = plugin;
        this.tag = new NamespacedKey(plugin, "vehicle");
        this.steam = new SteamLeaks(plugin);
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
        steam.start();
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        steam.stop();
        for (Car c : new ArrayList<>(cars.values())) despawn(c);
        cars.clear();
        byEntity.clear();
        byRider.clear();
        inputs.clear();
        parked.clear();
    }

    // ------------------------------------------------------------------ queries (also used by tests)

    public int count() {
        return cars.size();
    }

    public boolean riding(Player p) {
        return byRider.containsKey(p.getUniqueId());
    }

    public boolean driving(Player p) {
        Car c = byRider.get(p.getUniqueId());
        return c != null && p.getUniqueId().equals(c.driver());
    }

    /** Position {x, y, z, yaw, speed} of the car this player is in, or null. */
    public double[] state(Player p) {
        Car c = byRider.get(p.getUniqueId());
        return c == null ? null : new double[]{c.phys.x, c.phys.y, c.phys.z, c.phys.yaw, c.phys.speed};
    }

    public boolean brakeLights(Player p) {
        Car c = byRider.get(p.getUniqueId());
        return c != null && c.brakeLightsOn();
    }

    /** Entities spawned by cars (body, seats, hitboxes) in a world. */
    public long entityCount(World w) {
        return w.getEntities().stream().filter(e -> e.getPersistentDataContainer().has(tag, PersistentDataType.STRING)).count();
    }

    private World level1() {
        return plugin.worlds().world(Level.LEVEL_1);
    }

    // ------------------------------------------------------------------ getting in

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        Entity clicked = e.getRightClicked();
        Car live = byEntity.get(clicked.getUniqueId());
        if (live == null) {
            String action = clicked.getPersistentDataContainer().get(Keys.ACTION, PersistentDataType.STRING);
            if (!"car".equals(action) || clicked.getWorld() != level1()) return;
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (byRider.containsKey(p.getUniqueId()) || p.isInsideVehicle()) return;
        if (live == null) {
            String key = clicked.getPersistentDataContainer().get(Keys.DECOR, PersistentDataType.STRING);
            DecorSpec spec = key == null ? null : findSpec(clicked.getLocation(), key);
            if (spec == null) return;
            live = spawn(spec);
            if (live == null) return;
            if (GarageProps.covered(spec.seed)) {
                clicked.getWorld().playSound(clicked.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 1f, 0.6f);
                clicked.getWorld().spawnParticle(Particle.WHITE_ASH, clicked.getLocation().add(0, 1, 0), 40, 1.0, 0.4, 2.0, 0.01);
            }
        }
        enter(p, live);
    }

    /** Turn a parked car decoration into a live car. */
    Car spawn(DecorSpec spec) {
        World w = level1();
        if (w == null || cars.containsKey(spec.key())) return cars.get(spec.key());
        plugin.decorations().hide(w, spec.key());
        Car c = new Car(spec, w, tag);
        cars.put(spec.key(), c);
        for (Entity e : c.body) byEntity.put(e.getUniqueId(), c);
        for (Entity e : c.seats) byEntity.put(e.getUniqueId(), c);
        for (Entity e : c.hitboxes) byEntity.put(e.getUniqueId(), c);
        c.abandonedAt = tick;
        return c;
    }

    private void enter(Player p, Car c) {
        int seat = c.driver() == null ? 0 : c.freeSeat();
        if (seat < 0) {
            p.sendActionBar(BackroomsConfig.mm("<gray>There is no room left in this car."));
            return;
        }
        if (!c.seats[seat].addPassenger(p)) return;
        c.riders[seat] = p.getUniqueId();
        byRider.put(p.getUniqueId(), c);
        c.abandonedAt = -1;
        sound(c, c.seatLocation(seat), null, "car.door", Sound.BLOCK_IRON_DOOR_CLOSE, 0.9f, 1.3f);
        if (seat == 0) {
            try {
                inputs.put(p.getUniqueId(), p.getCurrentInput());
            } catch (RuntimeException | AbstractMethodError ignored) {
                inputs.remove(p.getUniqueId());
            }
            if (!c.engine) {
                c.engine = true;
                c.nextEngineSound = tick + 30;
                sound(c, c.location(), null, "car.start", Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 0.6f);
            }
            p.sendActionBar(BackroomsConfig.mm("<gray>W/S drive · A/D steer · <white>Space</white> brake · <white>Sprint</white> horn · <white>Sneak</white> get out"));
        } else {
            p.sendActionBar(BackroomsConfig.mm("<gray>You are riding along. <white>Sneak</white> to get out."));
        }
    }

    /** The parked car spec behind a decoration hitbox (searched in the chunks around it). */
    private DecorSpec findSpec(Location at, String key) {
        LevelGenerator g = plugin.worlds().generator(Level.LEVEL_1);
        if (g == null) return null;
        int cx = at.getBlockX() >> 4, cz = at.getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (DecorSpec d : g.decorations(cx + dx, cz + dz)) if (d.key().equals(key)) return d;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ getting out

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Car c = byEntity.get(e.getDismounted().getUniqueId());
        if (c == null) return;
        int seat = c.seatOf(p.getUniqueId());
        release(p, c);
        if (ejecting.contains(p.getUniqueId()) || seat < 0) return;
        // place them beside the car once the dismount has finished, unless they went somewhere else meanwhile
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || p.isInsideVehicle() || p.getWorld() != c.world) return;
            Location seatAt = c.seatLocation(seat);
            if (p.getLocation().distanceSquared(seatAt) > 36) return;
            p.teleport(safeExit(c, seat));
        });
    }

    private void release(Player p, Car c) {
        int seat = c.seatOf(p.getUniqueId());
        if (seat >= 0) c.riders[seat] = null;
        byRider.remove(p.getUniqueId());
        inputs.remove(p.getUniqueId());
        if (seat >= 0) sound(c, c.seatLocation(seat), null, "car.door", Sound.BLOCK_IRON_DOOR_CLOSE, 0.9f, 1.3f);
        if (seat == 0) {
            // the next passenger does not take the wheel by magic: the car brakes to a stop
            stopEngineSound(c);
        }
        if (c.empty()) {
            c.abandonedAt = tick;
            c.engine = false;
            clearLights(c);
        }
    }

    /** Take a player out now (quit, death, world change, disable) and put them somewhere safe. */
    private void eject(Player p, Car c) {
        int seat = c.seatOf(p.getUniqueId());
        ejecting.add(p.getUniqueId());
        try {
            if (seat >= 0 && c.seats[seat] != null) c.seats[seat].removePassenger(p);
            if (p.isInsideVehicle()) p.leaveVehicle();
        } finally {
            ejecting.remove(p.getUniqueId());
        }
        release(p, c);
        if (seat >= 0 && p.getWorld() == c.world) p.teleport(safeExit(c, seat));
    }

    /** Beside the rider's door if there is room, else the other side, the front, the back, or the seat itself. */
    Location safeExit(Car c, int seat) {
        double sx = Car.SEATS[seat][0] > 0 ? 1 : -1, sz = Car.SEATS[seat][2];
        double[][] tries = {{sx * 1.6, sz}, {-sx * 1.6, sz}, {sx * 1.6, sz + 1.2}, {-sx * 1.6, sz - 1.2}, {0, 3.0}, {0, -3.0}};
        for (double[] t : tries) {
            double[] w = c.phys.toWorld(t[0], 0, t[1]);
            Location l = new Location(c.world, w[0], Math.floor(c.phys.y + 0.01), w[2], (float) c.phys.yaw + (float) (sx * 90), 0);
            Block feet = l.getBlock(), head = feet.getRelative(0, 1, 0), below = feet.getRelative(0, -1, 0);
            if (feet.isPassable() && head.isPassable() && !below.isPassable() && feet.getType() != Material.WATER) return l;
        }
        Location l = c.seatLocation(seat);
        l.setY(c.phys.y + 0.01);
        return l;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Car c = byRider.get(e.getPlayer().getUniqueId());
        if (c != null) eject(e.getPlayer(), c);
        inputs.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        forget(e.getPlayer());
    }

    /** Out of any car (death, or the Backrooms death screen). */
    public void forget(Player p) {
        Car c = byRider.get(p.getUniqueId());
        if (c != null) eject(p, c);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Car c = byRider.get(e.getPlayer().getUniqueId());
        if (c != null) {
            release(e.getPlayer(), c);
        }
    }

    @EventHandler
    public void onInput(PlayerInputEvent e) {
        Car c = byRider.get(e.getPlayer().getUniqueId());
        if (c == null || !e.getPlayer().getUniqueId().equals(c.driver())) return;
        Input in = e.getInput();
        inputs.put(e.getPlayer().getUniqueId(), in);
        if (in.isSprint() && !c.hornWas) {
            sound(c, c.location(), null, "car.horn", Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 2.0f, 1.0f);
        }
        c.hornWas = in.isSprint();
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        for (Car c : new ArrayList<>(cars.values())) {
            if (c.world == e.getWorld() && c.empty() && ((int) Math.floor(c.phys.x) >> 4) == e.getChunk().getX()
                    && ((int) Math.floor(c.phys.z) >> 4) == e.getChunk().getZ()) {
                despawn(c);
            }
        }
    }

    /** Car entities are never saved, but sweep any that a crash left behind. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (en.getPersistentDataContainer().has(tag, PersistentDataType.STRING) && !byEntity.containsKey(en.getUniqueId())) en.remove();
        }
    }

    // ------------------------------------------------------------------ driving

    private void tickAll() {
        tick++;
        for (Car c : new ArrayList<>(cars.values())) {
            if (!c.valid()) {
                despawn(c);
                continue;
            }
            // riders that vanished without an event (kicked mid-tick, etc.)
            for (int i = 0; i < 4; i++) {
                UUID r = c.riders[i];
                if (r == null) continue;
                Player p = plugin.getServer().getPlayer(r);
                if (p == null || !p.isOnline() || p.getWorld() != c.world) {
                    c.riders[i] = null;
                    byRider.remove(r);
                    if (c.empty()) c.abandonedAt = tick;
                }
            }
            if (c.empty() && c.abandonedAt >= 0 && tick - c.abandonedAt > ABANDON_TICKS && Math.abs(c.phys.speed) < 0.05) {
                despawn(c);
                continue;
            }
            Player driver = c.driver() == null ? null : plugin.getServer().getPlayer(c.driver());
            CarPhysics.Input in = driver == null ? CarPhysics.Input.PARK : toInput(inputs.get(driver.getUniqueId()));
            double yawBefore = c.phys.yaw;
            double y0 = c.phys.y;
            c.phys.step(in, 0.05, terrain(c));
            if (c.phys.impact > 2.5) {
                float vol = (float) Math.min(1.5, c.phys.impact / 6);
                sound(c, c.location(), null, "metal.clank", Sound.BLOCK_ANVIL_LAND, vol, 0.7f);
                if (c.phys.impact > 6) sound(c, c.location(), null, "metal.groan", Sound.ENTITY_IRON_GOLEM_DAMAGE, vol, 0.5f);
            }
            if (y0 - c.phys.y > 0.6 && c.phys.onGround) c.world.playSound(c.location(), Sound.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.5f, 0.6f);
            roadSounds(c, in);
            c.sync();
            c.lights();
            if (driver != null) {
                if (plugin.cfg().carCameraFollow) followCamera(driver, c.phys.yaw - yawBefore);
                if (tick % 10 == 0) {
                    driver.sendActionBar(BackroomsConfig.mm(String.format(java.util.Locale.ROOT, "<gray>%s<white>%d</white> km/h",
                            c.phys.speed < -0.05 ? "R  " : "", Math.round(c.phys.kmh()))));
                }
            }
            if (c.engine) {
                engineSound(c);
                if (tick % 2 == 0) headlights(c);
                if (Math.abs(c.phys.speed) > 3 && tick % 4 == 0 && c.phys.onGround) {
                    Block under = c.location().getBlock();
                    if (under.getType() == Material.WATER) {
                        c.world.spawnParticle(Particle.SPLASH, c.location().add(0, 0.2, 0), 12, 1.0, 0.1, 1.8, 0.2);
                    }
                }
            }
        }
    }

    private static CarPhysics.Input toInput(Input in) {
        if (in == null) return CarPhysics.Input.NONE;
        return new CarPhysics.Input(in.isForward(), in.isBackward(), in.isLeft(), in.isRight(), in.isJump());
    }

    /** Turn the driver's view with the car (a relative rotation, so their own mouse movement is kept). */
    @SuppressWarnings("removal") // relative flags: deprecated since 1.21.10 but still the only relative rotation
    /**
     * Turn the driver's view with the car. Never by teleporting: on Paper 1.21.11 any player teleport drops them out
     * of their seat (the old "retain vehicle" flag is ignored), which is what made A/D throw drivers out. lookAt only
     * rotates the camera.
     */
    private void followCamera(Player p, double dYaw) {
        if (Math.abs(dYaw) < 0.05 || Math.abs(dYaw) > 90) return;
        Location eye = p.getEyeLocation();
        double yaw = Math.toRadians(eye.getYaw() + dYaw), pitch = Math.toRadians(eye.getPitch());
        double x = -Math.sin(yaw) * Math.cos(pitch), y = -Math.sin(pitch), z = Math.cos(yaw) * Math.cos(pitch);
        try {
            p.lookAt(eye.getX() + x * 10, eye.getY() + y * 10, eye.getZ() + z * 10, io.papermc.paper.entity.LookAnchor.EYES);
        } catch (RuntimeException ignored) {
            // the view just does not follow
        }
    }

    private CarPhysics.Terrain terrain(Car self) {
        World w = self.world;
        return new CarPhysics.Terrain() {
            @Override
            public boolean blocked(double x, double y, double z) {
                int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
                if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return true;
                Block b = w.getBlockAt(bx, by, bz);
                if (!b.isPassable()) return true;
                return insideOtherCar(self, x, y, z);
            }

            @Override
            public double top(int x, int y, int z) {
                if (!w.isChunkLoaded(x >> 4, z >> 4)) return 1.0;
                Block b = w.getBlockAt(x, y, z);
                Material m = b.getType();
                if (m == Material.WATER) return 0.9; // puddles: drive through
                if (b.isPassable()) return -1;
                BlockData d = b.getBlockData();
                if (d instanceof Slab s && s.getType() == Slab.Type.BOTTOM) return 0.5;
                return 1.0;
            }
        };
    }

    /** Parked decoration cars and other live cars are solid too. */
    private boolean insideOtherCar(Car self, double x, double y, double z) {
        if (y > self.phys.y + 1.45 + 0.6) return false;
        for (Car o : cars.values()) {
            if (o == self || o.world != self.world) continue;
            if (inFootprint(o.phys.x, o.phys.z, o.phys.yaw, x, z) && y < o.phys.y + 1.45 && y > o.phys.y) return true;
        }
        int cx = (int) Math.floor(x) >> 4, cz = (int) Math.floor(z) >> 4;
        for (DecorSpec d : parkedIn(cx, cz)) {
            if (cars.containsKey(d.key())) continue;
            if (inFootprint(d.x, d.z, d.yaw, x, z) && y < d.y + 1.45 && y > d.y) return true;
        }
        return false;
    }

    private List<DecorSpec> parkedIn(int cx, int cz) {
        long k = ((long) cx << 32) ^ (cz & 0xffffffffL);
        List<DecorSpec> list = parked.get(k);
        if (list == null) {
            list = new ArrayList<>();
            LevelGenerator g = plugin.worlds().generator(Level.LEVEL_1);
            // a car anchored in a neighbouring chunk can reach into this one
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (g == null) continue;
                    for (DecorSpec d : g.decorations(cx + dx, cz + dz)) if (d.model.equals(HabitableGenerator.CAR_MODEL)) list.add(d);
                }
            }
            parked.put(k, list);
        }
        return list;
    }

    private static boolean inFootprint(double cx, double cz, double yaw, double x, double z) {
        double r = Math.toRadians(yaw);
        double dx = x - cx, dz = z - cz;
        double along = dx * -Math.sin(r) + dz * Math.cos(r);
        double across = dx * -Math.cos(r) + dz * -Math.sin(r);
        return Math.abs(along) < GarageProps.CAR_LENGTH / 2 && Math.abs(across) < GarageProps.CAR_WIDTH / 2;
    }

    // ------------------------------------------------------------------ headlights (client-side light blocks)

    private void headlights(Car c) {
        List<Location> want = new ArrayList<>();
        double[] o = c.phys.toWorld(0, 0.8, 2.4);
        double[] f = c.phys.toWorld(0, 0.8, 3.4);
        double dx = f[0] - o[0], dz = f[2] - o[2];
        Location last = null;
        for (double d = 0; d <= 9; d += 0.5) {
            Block b = c.world.getBlockAt((int) Math.floor(o[0] + dx * d), (int) Math.floor(o[1]), (int) Math.floor(o[2] + dz * d));
            if (b.getType() != Material.AIR) break;
            last = b.getLocation();
            if (Math.abs(d - 3.0) < 0.25) want.add(last);
        }
        if (last != null && (want.isEmpty() || !want.get(0).equals(last))) want.add(last);
        if (want.equals(c.fakeLights)) return;
        clearLights(c);
        Light light = (Light) Material.LIGHT.createBlockData();
        for (int i = 0; i < want.size(); i++) {
            light.setLevel(i == 0 ? 12 : 14);
            for (Player p : c.world.getPlayers()) {
                if (p.getLocation().distanceSquared(want.get(i)) < 48 * 48) {
                    p.sendBlockChange(want.get(i), light);
                    if (!c.fakeLightViewers.contains(p.getUniqueId())) c.fakeLightViewers.add(p.getUniqueId());
                }
            }
        }
        c.fakeLights.addAll(want);
    }

    private void clearLights(Car c) {
        for (Location l : c.fakeLights) {
            if (!c.world.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
            BlockData real = l.getBlock().getBlockData();
            for (UUID id : c.fakeLightViewers) {
                Player p = plugin.getServer().getPlayer(id);
                if (p != null && p.getWorld() == c.world) p.sendBlockChange(l, real);
            }
        }
        c.fakeLights.clear();
        if (c.fakeLights.isEmpty()) c.fakeLightViewers.clear();
    }

    // ------------------------------------------------------------------ sound

    private void engineSound(Car c) {
        float pitch = (float) (0.75 + 0.85 * Math.min(1, Math.abs(c.phys.speed) / CarPhysics.MAX_FORWARD));
        var def = plugin.audio().index().def("car.engine");
        int loop = def != null ? Math.max(20, def.durationTicks() - 4) : 20;
        boolean retune = Math.abs(pitch - c.enginePitch) > 0.12;
        if (tick < c.nextEngineSound && !retune) return;
        if (retune) stopEngineSound(c);
        c.enginePitch = pitch;
        c.nextEngineSound = tick + loop;
        Entity follow = c.seats[0];
        for (Player p : c.world.getPlayers()) {
            if (p.getLocation().distanceSquared(c.location()) > 40 * 40) continue;
            String ev = custom(p, "car.engine");
            if (ev != null) {
                p.playSound(net.kyori.adventure.sound.Sound.sound(Key.key("backrooms", ev), net.kyori.adventure.sound.Sound.Source.NEUTRAL, 0.9f, pitch), follow);
            } else if (Math.abs(c.phys.speed) > 0.3) {
                p.playSound(c.location(), Sound.ENTITY_MINECART_RIDING, SoundCategory.NEUTRAL, 0.35f, pitch);
            } else if (tick % 40 < 20) {
                p.playSound(c.location(), Sound.BLOCK_BEACON_AMBIENT, SoundCategory.NEUTRAL, 0.25f, 0.55f);
            }
        }
    }

    /** Brakes squealing at speed, the body scraping along a wall, tyres on the floor. */
    private void roadSounds(Car c, CarPhysics.Input in) {
        double v = Math.abs(c.phys.speed);
        if (c.phys.braking && v > 4 && c.phys.onGround && tick >= c.nextBrakeSound) {
            c.nextBrakeSound = tick + 14;
            sound(c, c.location(), null, "car.brake", Sound.BLOCK_GRINDSTONE_USE, (float) Math.min(1.0, v / 8), 1.6f);
        }
        if (c.phys.scraping && tick % 5 == 0) sound(c, c.location(), null, "metal.scrape", Sound.BLOCK_CHAIN_STEP, 1.0f, 0.6f);
        if (v > 2 && c.phys.onGround && tick % 8 == 0) {
            Material under = c.location().add(0, -0.5, 0).getBlock().getType();
            if (under != Material.WATER) c.world.playSound(c.location(), under.createBlockData().getSoundGroup().getStepSound(), SoundCategory.NEUTRAL,
                    (float) Math.min(0.6, 0.15 + v / 25), 0.55f);
        }
    }

    private void stopEngineSound(Car c) {
        c.nextEngineSound = 0;
        for (Player p : c.world.getPlayers()) {
            String ev = custom(p, "car.engine");
            if (ev != null) p.stopSound(SoundStop.named(Key.key("backrooms", ev)));
        }
    }

    /** The registered event to play for this player, or null when they should get the vanilla fallback. */
    private String custom(Player p, String id) {
        if (!plugin.audio().custom(p) || plugin.audio().index().def(id) == null) return null;
        return plugin.audio().index().resolve(id, null, false);
    }

    /** A one-shot at a point: the pack's sound for players who have it, the vanilla stand-in for everyone else. */
    private void sound(Car c, Location at, Entity follow, String id, Sound fallback, float vol, float pitch) {
        for (Player p : c.world.getPlayers()) {
            if (p.getLocation().distanceSquared(at) > 48 * 48) continue;
            String ev = custom(p, id);
            if (ev != null) p.playSound(at, "backrooms:" + ev, SoundCategory.NEUTRAL, vol, pitch);
            else if (fallback != null) p.playSound(at, fallback, SoundCategory.NEUTRAL, Math.min(1f, vol), pitch);
        }
    }

    // ------------------------------------------------------------------ removal

    private void despawn(Car c) {
        for (Player p : c.riderPlayers()) eject(p, c);
        stopEngineSound(c);
        clearLights(c);
        for (Entity e : c.body) byEntity.remove(e.getUniqueId());
        for (Entity e : c.seats) if (e != null) byEntity.remove(e.getUniqueId());
        for (Entity e : c.hitboxes) if (e != null) byEntity.remove(e.getUniqueId());
        c.removeEntities();
        cars.remove(c.spec.key());
        plugin.decorations().show(c.world, c.spec);
    }
}
