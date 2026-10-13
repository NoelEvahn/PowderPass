package com.powderpass.backrooms;

import com.powderpass.backrooms.display.Keys;
import com.powderpass.backrooms.gen.LevelGenerator;
import com.powderpass.backrooms.gen.levels.OfficeGenerator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Interaction;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/** Level 4's lifts on MockBukkit: a ride goes to the chosen floor; a stray ride opens onto another level. */
class OfficeTowersLiftTest {
    private ServerMock server;
    private BackroomsPlugin plugin;
    private World overworld;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock(new TestServer());
        overworld = server.createWorld(new org.bukkit.WorldCreator("world"));
        plugin = MockBukkit.load(BackroomsPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock riderInCar(OfficeGenerator.Lift lift, int floor) {
        PlayerMock p = new TestPlayer(server, "Rider");
        server.addPlayer(p);
        p.teleport(new Location(overworld, 0.5, 70, 0.5));
        p.teleport(new Location(plugin.worlds().world(Level.LEVEL_4), lift.carX() + 1.5, lift.feetY(floor), lift.carZ() + 1.5, 30f, 0f));
        plugin.sessions().get(p).cooldownUntil = 0;
        return p;
    }

    private OfficeGenerator.Lift lift() {
        OfficeGenerator g = (OfficeGenerator) plugin.worlds().generator(Level.LEVEL_4);
        return g.lift(0, 0);
    }

    @Test
    void rideGoesToTheChosenFloor() {
        OfficeGenerator.Lift lift = lift();
        assertTrue(lift.floors() >= 3);
        PlayerMock p = riderInCar(lift, 0);
        plugin.lifts().setRoll(() -> 0.99); // never strays
        int target = lift.floors() - 1;
        assertTrue(plugin.lifts().press(p, p.getLocation(), target));
        assertTrue(plugin.sessions().get(p).inTransition, "riders are held while the car moves");
        assertFalse(plugin.lifts().press(p, p.getLocation(), 1), "one ride at a time per car");
        server.getScheduler().performTicks(40L + 10L * target);
        assertEquals(plugin.worlds().world(Level.LEVEL_4), p.getWorld());
        assertEquals(lift.feetY(target), p.getLocation().getY(), 1e-6, "arrives on the chosen floor");
        assertTrue(lift.inCar(p.getLocation().getX(), p.getLocation().getZ()), "still in the car");
        assertEquals(30f, p.getLocation().getYaw(), 1e-3);
        assertFalse(plugin.sessions().get(p).inTransition);
        // and back down by a floor button (an Interaction hitbox of the panel)
        Interaction button = p.getWorld().spawn(new Location(p.getWorld(), lift.carX() + 2.9, lift.feetY(target) + 1, lift.carZ() + 0.9), Interaction.class,
                e -> e.getPersistentDataContainer().set(Keys.ACTION, PersistentDataType.STRING, "lift:0"));
        server.getPluginManager().callEvent(new PlayerInteractEntityEvent(p, button));
        server.getScheduler().performTicks(40L + 10L * target);
        assertEquals(lift.feetY(0), p.getLocation().getY(), 1e-6, "the panel button takes the car to the lobby");
        assertEquals(2, plugin.lifts().rides());
    }

    @Test
    void aStrayRideOpensOntoAnotherLevel() {
        OfficeGenerator.Lift lift = lift();
        PlayerMock p = riderInCar(lift, 1);
        plugin.sessions().saveReturn(p, new Location(overworld, 3, 70, 4));
        Location ret = plugin.sessions().getReturn(p);
        // prepare a floor at the arrival spot in every level the car could open onto
        for (Level l : Level.values()) {
            if (l == Level.LEVEL_4) continue;
            World w = plugin.worlds().world(l);
            LevelGenerator g = plugin.worlds().generator(l);
            int[] s = g.safeSpot(p.getLocation().getBlockX(), p.getLocation().getBlockZ(), 0);
            for (int x = s[0] - 2; x <= s[0] + 2; x++) for (int z = s[2] - 2; z <= s[2] + 2; z++) w.getBlockAt(x, s[1] - 1, z).setType(Material.STONE);
        }
        plugin.lifts().setRoll(() -> 0.0); // the 10% roll comes up
        assertTrue(plugin.lifts().press(p, p.getLocation(), 0));
        server.getScheduler().performTicks(80);
        Level now = plugin.worlds().levelOf(p.getWorld());
        assertNotNull(now, "still inside the Backrooms");
        assertNotEquals(Level.LEVEL_4, now, "the doors opened somewhere else entirely");
        int[] s = plugin.worlds().generator(now).safeSpot(lift.carX() + 1, lift.carZ() + 1, 0);
        assertEquals(s[0], p.getLocation().getBlockX());
        assertEquals(s[1], p.getLocation().getBlockY());
        assertEquals(s[2], p.getLocation().getBlockZ());
        assertFalse(plugin.sessions().get(p).inTransition);
        assertTrue(plugin.sessions().get(p).cooldownUntil > plugin.sessions().now(), "normal arrival cooldown");
        assertTrue(plugin.sessions().insideFlag(p));
        assertEquals(ret, plugin.sessions().getReturn(p), "the return point is untouched");
    }

    @Test
    void fallingFarBelowTheTowersIsFatal() {
        PlayerMock p = new TestPlayer(server, "Faller");
        server.addPlayer(p);
        World l4 = plugin.worlds().world(Level.LEVEL_4);
        p.teleport(new Location(l4, 30.5, OfficeGenerator.BASE - 40, 30.5));
        server.getScheduler().performTicks(60);
        assertTrue(p.isDead() || p.getHealth() <= 0, "the drop takes you");
    }
}
