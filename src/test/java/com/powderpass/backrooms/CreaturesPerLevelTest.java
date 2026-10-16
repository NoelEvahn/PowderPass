package com.powderpass.backrooms;

import com.powderpass.backrooms.creature.Creature;
import com.powderpass.backrooms.creature.CreatureType;
import com.powderpass.backrooms.event.EventType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Which creatures live where, the Bacteria's hunt, blackout Smilers, light events everywhere, the announcements. */
class CreaturesPerLevelTest {
    private ServerMock server;
    private BackroomsPlugin plugin;
    private World l0;
    private int n;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock(new TestServer());
        server.createWorld(new org.bukkit.WorldCreator("world"));
        plugin = MockBukkit.load(BackroomsPlugin.class);
        plugin.getConfig().set("creatures.spawn-attempt-seconds", 3600);
        plugin.getConfig().set("events.light-event-chance", 0.0);
        plugin.saveConfig();
        plugin.reload();
        l0 = plugin.worlds().world(Level.LEVEL_0);
        flat(l0);
    }

    private static void flat(World w) {
        for (int x = -24; x <= 24; x++) {
            for (int z = -24; z <= 24; z++) {
                w.getBlockAt(x, 64, z).setType(Material.STONE);
                for (int y = 65; y <= 69; y++) w.getBlockAt(x, y, z).setType(Material.AIR);
            }
        }
        for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) w.getChunkAt(cx, cz);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private TestPlayer player(World w, double x, double z) {
        TestPlayer p = new TestPlayer(server, "P" + (++n));
        server.addPlayer(p);
        p.setGameMode(GameMode.SURVIVAL);
        p.teleport(new Location(w, x, 65, z));
        return p;
    }

    private Set<CreatureType> livingIn(Level level) {
        Set<CreatureType> out = EnumSet.noneOf(CreatureType.class);
        for (CreatureType t : CreatureType.values()) {
            if (plugin.cfg().creature(t.id).weights().getOrDefault(level, 0.0) > 0) out.add(t);
        }
        return out;
    }

    @Test
    void eachLevelHasExactlyItsCreatures() {
        Map<Level, Set<CreatureType>> want = Map.of(
                Level.LEVEL_0, EnumSet.of(CreatureType.BACTERIA, CreatureType.WATCHER, CreatureType.SKINWALKER),
                Level.POOLROOMS, EnumSet.of(CreatureType.DRIFTER, CreatureType.WATCHER, CreatureType.SKINWALKER),
                Level.LEVEL_1, EnumSet.of(CreatureType.HOUND, CreatureType.WATCHER, CreatureType.SMILER, CreatureType.SKINWALKER),
                Level.LEVEL_4, EnumSet.of(CreatureType.HOUND, CreatureType.FACELING, CreatureType.SKINWALKER),
                Level.PLAINS, EnumSet.of(CreatureType.WATCHER, CreatureType.SMILER, CreatureType.SKINWALKER));
        for (Map.Entry<Level, Set<CreatureType>> e : want.entrySet()) assertEquals(e.getValue(), livingIn(e.getKey()), e.getKey().name());
    }

    private void warm() {
        server.getScheduler().performTicks(41);
    }

    @Test
    void bacteriaHuntsWhatMovesAndLosesWhatKeepsStill() {
        TestPlayer p = player(l0, 0.5, 0.5);
        Creature b = plugin.creatures().spawn(CreatureType.BACTERIA, new Location(l0, 5.5, 65, 0.5), null);
        assertEquals("bacteria:emerge", b.state(), "with someone near, it comes up out of the floor first");
        server.getScheduler().performTicks(com.powderpass.backrooms.creature.BacteriaAccess.EMERGE_TICKS + 50); // + the manager's start delay
        assertNotEquals("bacteria:emerge", b.state(), "and then it is here");
        // walking about near it: it stops to listen, then it is on you
        for (int i = 0; i < 40 && !b.state().equals("bacteria:hunt"); i++) {
            p.teleport(new Location(l0, 0.5, 65, 0.5 + (i % 2) * 0.6));
            server.getScheduler().performTicks(2);
        }
        assertEquals("bacteria:hunt", b.state(), "a moving player is hunted");
        // keep it from killing the test player, then stand completely still
        p.setHealth(20);
        p.setInvulnerable(true);
        Location still = p.getLocation().clone();
        String lost = null;
        for (int t = 0; t < 200 && lost == null; t += 2) {
            p.teleport(still);
            p.setHealth(20);
            server.getScheduler().performTicks(2);
            if (!b.state().equals("bacteria:hunt")) lost = b.state();
        }
        assertNotNull(lost, "standing still, it loses you");
        assertTrue(lost.equals("bacteria:loom") || lost.equals("bacteria:search"), lost);
    }

    @Test
    void aLevel1BlackoutBringsSmilersThatLeaveWithTheDark() {
        World l1 = plugin.worlds().world(Level.LEVEL_1);
        flat(l1);
        TestPlayer p = player(l1, 0.5, 0.5);
        warm();
        int before = plugin.creatures().countLevel(l1);
        plugin.environment().blackout(p.getLocation(), 12, 60);
        int during = plugin.creatures().countLevel(l1);
        assertTrue(during > before, "Smilers appear in the blackout: " + before + " -> " + during);
        server.getScheduler().performTicks(140);
        assertEquals(before, plugin.creatures().countLevel(l1), "they are gone when the lights return");
    }

    @Test
    void lightsMisbehaveInEveryLevel() {
        for (EventType t : new EventType[]{EventType.FLICKER, EventType.BLACKOUT, EventType.LIGHTS_OUT}) {
            assertEquals(EnumSet.allOf(Level.class), t.levels, t.name());
        }
    }

    @Test
    void announcementsComeAboutEveryFortySeconds() {
        TestPlayer p = player(l0, 0.5, 0.5);
        plugin.pack().markLoaded(p, true);
        server.getScheduler().performTicks(20 * 75);
        long voices = p.customSounds.stream().filter(s -> s.startsWith("backrooms:voice.")).count();
        assertTrue(voices >= 2, "voices/announcements within 75 s: " + p.customSounds.stream().filter(s -> s.startsWith("backrooms:voice.")).toList());
    }
}
