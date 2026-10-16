package com.powderpass.backrooms;

import com.powderpass.backrooms.cutscene.Cutscene;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** The cutscene engine: camera ride, fades, the world change behind black, skip, disconnect, protection. */
class CutsceneTest {
    private ServerMock server;
    private BackroomsPlugin plugin;
    private World from, to;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock(new TestServer());
        server.createWorld(new org.bukkit.WorldCreator("world"));
        plugin = MockBukkit.load(BackroomsPlugin.class);
        from = plugin.worlds().world(Level.LEVEL_0);
        to = plugin.worlds().world(Level.POOLROOMS);
        for (World w : new World[]{from, to}) {
            for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
                w.getBlockAt(x, 64, z).setType(Material.STONE);
                for (int y = 65; y <= 70; y++) w.getBlockAt(x, y, z).setType(Material.AIR);
            }
        }
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private TestPlayer player() {
        TestPlayer p = new TestPlayer(server, "Viewer");
        server.addPlayer(p);
        p.setGameMode(GameMode.SURVIVAL);
        p.teleport(new Location(from, 0.5, 65, 0.5));
        return p;
    }

    private Cutscene scene(AtomicInteger at, AtomicBoolean finished) {
        Location dest = new Location(to, 2.5, 65, 2.5);
        return new Cutscene("test_scene")
                .camera(0, 0.5, 66.6, 0.5).camera(20, 0.5, 66.0, 3.5)
                .shake(0, 10, 2)
                .at(5, q -> at.set(5))
                .fadeOut(10, 8)
                .travel(20, q -> dest)
                .camera(20, 0, 4, 0).camera(40, 0, 1.62, 0)
                .fadeIn(24, 8)
                .length(44)
                .finish(q -> finished.set(true));
    }

    @Test
    void itRidesTheCameraChangesWorldBehindBlackAndEndsAtTheDestination() {
        TestPlayer p = player();
        AtomicInteger at = new AtomicInteger();
        AtomicBoolean done = new AtomicBoolean();
        assertTrue(plugin.cutscenes().play(p, scene(at, done)), "it plays");
        assertTrue(plugin.cutscenes().playing(p));
        assertTrue(p.isInsideVehicle(), "riding the camera");
        assertFalse(plugin.cutscenes().play(p, scene(at, done)), "one at a time");
        server.getScheduler().performTicks(8);
        assertEquals(5, at.get(), "actions fire on their tick");
        // protected while it plays
        EntityDamageEvent hit = new EntityDamageEvent(p, EntityDamageEvent.DamageCause.FALL,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FALL).build(), 5);
        server.getPluginManager().callEvent(hit);
        assertTrue(hit.isCancelled());
        server.getScheduler().performTicks(8);
        assertTrue(p.hasPotionEffect(PotionEffectType.BLINDNESS), "faded to black before the world changes");
        assertEquals(from, p.getWorld());
        server.getScheduler().performTicks(6);
        assertEquals(to, p.getWorld(), "the world changed behind black");
        assertTrue(p.isInsideVehicle(), "and they ride a new camera there");
        server.getScheduler().performTicks(8);
        assertFalse(p.hasPotionEffect(PotionEffectType.BLINDNESS), "faded back in");
        server.getScheduler().performTicks(30);
        assertFalse(plugin.cutscenes().playing(p));
        assertTrue(done.get());
        assertFalse(p.isInsideVehicle(), "off the camera");
        assertEquals(to, p.getWorld());
        assertEquals(2.5, p.getLocation().getX(), 0.01);
        assertEquals(65, p.getLocation().getY(), 0.01, "standing at the destination");
        long cameras = to.getEntities().stream().filter(com.powderpass.backrooms.cutscene.CutsceneEngine::isCamera).count()
                + from.getEntities().stream().filter(com.powderpass.backrooms.cutscene.CutsceneEngine::isCamera).count();
        assertEquals(0, cameras, "no camera entity left behind");
    }

    @Test
    void sneakingSkipsStraightToTheDestination() {
        TestPlayer p = player();
        AtomicBoolean done = new AtomicBoolean();
        plugin.cutscenes().play(p, scene(new AtomicInteger(), done));
        server.getScheduler().performTicks(12);
        server.getPluginManager().callEvent(new PlayerToggleSneakEvent(p, true));
        server.getScheduler().performTicks(2);
        assertFalse(plugin.cutscenes().playing(p));
        assertTrue(done.get());
        assertEquals(to, p.getWorld(), "skipping still takes you there");
        assertFalse(p.hasPotionEffect(PotionEffectType.BLINDNESS));
    }

    @Test
    void disconnectingMidwayLeavesThemAtTheDestination() {
        TestPlayer p = player();
        plugin.cutscenes().play(p, scene(new AtomicInteger(), new AtomicBoolean()));
        server.getScheduler().performTicks(6);
        p.disconnect();
        assertFalse(plugin.cutscenes().playing(p));
        assertFalse(p.isInsideVehicle());
        assertEquals(to, p.getWorld(), "never stranded in a camera path");
    }

    @Test
    void transitionsPlayTheirSequence() {
        TestPlayer p = player();
        // the Poolrooms' service door leads to Level 1 with its own sequence
        var g = plugin.worlds().generator(Level.POOLROOMS);
        com.powderpass.backrooms.gen.Feature door = null;
        for (int d = 0; d < 9 && door == null; d++) for (var f : g.districtFeatures(d % 3, d / 3)) if (f.id().equals("service_door")) door = f;
        assertNotNull(door);
        p.teleport(new Location(to, 0.5, 65, 0.5));
        var cs = com.powderpass.backrooms.cutscene.Transitions.forFeature(plugin, p, Level.POOLROOMS, door);
        assertNotNull(cs);
        assertEquals("service_door", cs.id());
        assertTrue(cs.travelTick() > 0 && cs.duration() > cs.travelTick());
        assertTrue(plugin.cutscenes().play(p, cs), "the transition plays as a cutscene");
        server.getScheduler().performTicks(cs.duration() + 5);
        assertEquals(plugin.worlds().world(Level.LEVEL_1), p.getWorld());
        assertFalse(plugin.sessions().get(p).inTransition, "arrived");
    }
}
