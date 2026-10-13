package com.powderpass.backrooms;

import com.powderpass.backrooms.display.Keys;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.LevelGenerator;
import com.powderpass.backrooms.gen.levels.HabitableGenerator;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Level 1's drivable cars against MockBukkit: getting in through the parked decoration's hitbox, seats for four,
 * driving by input events into a wall, getting out safely, and a clean disable. (MockBukkit refuses to move an
 * entity that has passengers, so riders' camera smoothness and seat motion are only checkable on a real server;
 * the car's own pose is checked instead.)
 */
class VehicleTest {
    private ServerMock server;
    private BackroomsPlugin plugin;
    private World l1;
    private DecorSpec car;
    private double wallZ;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock(new TestServer());
        server.createWorld(new org.bukkit.WorldCreator("world"));
        plugin = MockBukkit.load(BackroomsPlugin.class);
        l1 = plugin.worlds().world(Level.LEVEL_1);
        plugin.creatures().stop(); // MockBukkit cannot spawn creatures (line of sight, hidden entities); not what is tested here
        LevelGenerator g = plugin.worlds().generator(Level.LEVEL_1);
        // a parked car near the origin that faces +Z (the north stall row), with its stall clear in front
        search:
        for (int r = 0; r < 20; r++) {
            for (int cx = -r; cx <= r; cx++) for (int cz = -r; cz <= r; cz++) {
                if (Math.max(Math.abs(cx), Math.abs(cz)) != r) continue;
                for (DecorSpec d : g.decorations(cx, cz)) {
                    if (d.model.equals(HabitableGenerator.CAR_MODEL) && d.yaw == 0f) {
                        car = d;
                        break search;
                    }
                }
            }
        }
        assertNotNull(car, "no parked car found");
        // a concrete floor around it and a wall across the aisle 9 blocks ahead of its centre
        int x0 = (int) Math.floor(car.x), z0 = (int) Math.floor(car.z);
        wallZ = z0 + 9;
        for (int x = x0 - 10; x <= x0 + 10; x++) {
            for (int z = z0 - 8; z <= z0 + 14; z++) {
                l1.getBlockAt(x, 64, z).setType(Material.STONE);
                if (z == (int) wallZ) for (int y = 65; y <= 67; y++) l1.getBlockAt(x, y, z).setType(Material.STONE);
            }
        }
        for (int cx = (x0 >> 4) - 2; cx <= (x0 >> 4) + 2; cx++) for (int cz = (z0 >> 4) - 2; cz <= (z0 >> 4) + 2; cz++) l1.getChunkAt(cx, cz).load();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private int n;

    private PlayerMock player() {
        PlayerMock p = new TestPlayer(server, "Driver" + (++n));
        server.addPlayer(p);
        p.teleport(new Location(l1, car.x + 1.8, 65, car.z - 1 + n * 0.3));
        return p;
    }

    private List<Interaction> carHitboxes() {
        List<Interaction> out = new ArrayList<>();
        for (Entity e : l1.getEntities()) {
            if (e instanceof Interaction i && "car".equals(e.getPersistentDataContainer().get(Keys.ACTION, PersistentDataType.STRING))
                    && car.key().equals(e.getPersistentDataContainer().get(Keys.DECOR, PersistentDataType.STRING))) {
                out.add(i);
            }
        }
        return out;
    }

    private Entity anyCarHitbox() {
        // a live car's hitboxes are tagged by the vehicle manager; take whichever entity is nearest the car's centre
        for (Entity e : l1.getEntities()) {
            if (e instanceof Interaction && e.getLocation().distanceSquared(new Location(l1, car.x, car.y, car.z)) < 9
                    && e.getPersistentDataContainer().getKeys().stream().anyMatch(k -> k.getKey().equals("vehicle"))) {
                return e;
            }
        }
        return null;
    }

    private void click(Player p, Entity target) {
        server.getPluginManager().callEvent(new PlayerInteractEntityEvent(p, target, EquipmentSlot.HAND));
    }

    private static Input input(boolean fwd, boolean back, boolean jump, boolean sneak) {
        return new Input() {
            public boolean isForward() { return fwd; }
            public boolean isBackward() { return back; }
            public boolean isLeft() { return false; }
            public boolean isRight() { return false; }
            public boolean isJump() { return jump; }
            public boolean isSneak() { return sneak; }
            public boolean isSprint() { return false; }
        };
    }

    private void spawnDecoration(PlayerMock p) {
        server.getScheduler().performTicks(60);
        assertFalse(carHitboxes().isEmpty(), "the parked car's decoration (with its hitbox) should be spawned near the player");
    }

    @Test
    void fourSeatsDriveIntoAWallAndGetOutSafely() {
        PlayerMock driver = player();
        spawnDecoration(driver);
        click(driver, carHitboxes().get(0));
        assertTrue(plugin.vehicles().driving(driver), "the first to click drives");
        assertTrue(driver.isInsideVehicle());
        assertEquals(1, plugin.vehicles().count());
        assertTrue(carHitboxes().isEmpty(), "the parked decoration is hidden while the car is live");

        // three passengers fit, a fifth person does not
        Entity hit = anyCarHitbox();
        assertNotNull(hit, "the live car has its own hitboxes");
        List<PlayerMock> riders = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            PlayerMock p = player();
            click(p, hit);
            assertTrue(plugin.vehicles().riding(p) && p.isInsideVehicle(), "passenger " + i + " seated");
            assertFalse(plugin.vehicles().driving(p));
            riders.add(p);
        }
        PlayerMock fifth = player();
        click(fifth, hit);
        assertFalse(plugin.vehicles().riding(fifth), "only four seats");
        assertFalse(fifth.isInsideVehicle());

        // throttle: the car drives forward (+Z) and stops at the wall
        double[] s0 = plugin.vehicles().state(driver);
        server.getPluginManager().callEvent(new PlayerInputEvent(driver, input(true, false, false, false)));
        server.getScheduler().performTicks(20);
        double[] s1 = plugin.vehicles().state(driver);
        assertTrue(s1[2] > s0[2] + 0.5, "moving forward: " + s0[2] + " -> " + s1[2]);
        assertTrue(s1[4] > 1, "picking up speed: " + s1[4]);
        server.getScheduler().performTicks(200);
        double[] s2 = plugin.vehicles().state(driver);
        assertTrue(s2[2] + 2.15 <= wallZ + 1e-6, "the nose stops at the wall: " + s2[2] + " wall " + wallZ);
        assertTrue(s2[2] > s0[2] + 3, "after getting close to it: " + s2[2]);
        assertTrue(Math.abs(s2[4]) < 1.5, "and it stopped: " + s2[4]);
        // the body moved with it
        long bodyNear = l1.getEntities().stream().filter(e -> e instanceof org.bukkit.entity.BlockDisplay
                && Math.abs(e.getLocation().getZ() - s2[2]) < 0.01).count();
        assertTrue(bodyNear >= 20, "body displays follow the car: " + bodyNear);

        // brake: brake lights
        server.getPluginManager().callEvent(new PlayerInputEvent(driver, input(false, true, false, false)));
        server.getScheduler().performTicks(30);
        server.getPluginManager().callEvent(new PlayerInputEvent(driver, input(false, false, true, false)));
        server.getScheduler().performTicks(2);
        assertTrue(plugin.vehicles().brakeLights(driver), "brake lights on while braking");

        // getting out (sneak makes the client dismount): beside the car, on its feet, in the open
        double[] s3 = plugin.vehicles().state(driver);
        driver.leaveVehicle();
        server.getScheduler().performTicks(2);
        assertFalse(plugin.vehicles().riding(driver));
        assertFalse(driver.isInsideVehicle());
        Location at = driver.getLocation();
        assertTrue(at.getBlock().isPassable() && at.clone().add(0, 1, 0).getBlock().isPassable(), "standing in the open at " + at);
        assertFalse(at.clone().add(0, -1, 0).getBlock().isPassable(), "on solid ground at " + at);
        double dx = at.getX() - s3[0], dz = at.getZ() - s3[2];
        assertTrue(Math.hypot(dx, dz) > 1.0 && Math.hypot(dx, dz) < 4.0, "next to the car: " + Math.hypot(dx, dz));

        // disabling the plugin puts everyone down and leaves nothing behind
        server.getPluginManager().disablePlugin(plugin);
        for (PlayerMock p : riders) assertFalse(p.isInsideVehicle(), "rider still seated after disable");
        long tagged = l1.getEntities().stream().filter(e -> e.getPersistentDataContainer().getKeys().stream()
                .anyMatch(k -> k.getKey().equals("vehicle"))).count();
        assertEquals(0, tagged, "car entities left after disable");
    }

    @Test
    void anAbandonedCarGoesBackToItsStall() {
        PlayerMock driver = player();
        spawnDecoration(driver);
        click(driver, carHitboxes().get(0));
        server.getPluginManager().callEvent(new PlayerInputEvent(driver, input(true, false, false, false)));
        server.getScheduler().performTicks(15);
        server.getPluginManager().callEvent(new PlayerInputEvent(driver, input(false, false, false, false)));
        driver.leaveVehicle();
        server.getScheduler().performTicks(200);
        assertEquals(1, plugin.vehicles().count(), "an abandoned car stays where it was left for a while");
        assertTrue(plugin.vehicles().entityCount(l1) > 0);
        server.getScheduler().performTicks((int) com.powderpass.backrooms.vehicle.VehicleManager.ABANDON_TICKS + 20);
        assertEquals(0, plugin.vehicles().count());
        assertEquals(0, plugin.vehicles().entityCount(l1));
        assertFalse(carHitboxes().isEmpty(), "the parked car is back in its stall");
    }

    @Test
    void quittingWhileDrivingLeavesNothingBehindOnceAbandoned() {
        PlayerMock driver = player();
        spawnDecoration(driver);
        click(driver, carHitboxes().get(0));
        assertTrue(plugin.vehicles().riding(driver));
        driver.disconnect();
        server.getScheduler().performTicks(2);
        assertFalse(plugin.vehicles().riding(driver));
        assertFalse(driver.isInsideVehicle());
    }
}
