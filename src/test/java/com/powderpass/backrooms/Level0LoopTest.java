package com.powderpass.backrooms;

import com.powderpass.backrooms.gen.levels.Level0Generator;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

/** Level 0's endless fall: below the loop height in a shaft you are carried up over the upper floor, unhurt. */
class Level0LoopTest {
    private ServerMock server;
    private BackroomsPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock(new TestServer());
        server.createWorld(new org.bukkit.WorldCreator("world"));
        plugin = MockBukkit.load(BackroomsPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void fallingDownAShaftComesBackOutOfTheUpperCeiling() {
        World w = plugin.worlds().world(Level.LEVEL_0);
        Level0Generator g = (Level0Generator) plugin.worlds().generator(Level.LEVEL_0);
        int[] shaft = null;
        for (int x = -400; x < 400 && shaft == null; x++) for (int z = -400; z < 400; z++) if (g.shaftAt(x, z)) {
            shaft = new int[]{x, z};
            break;
        }
        assertNotNull(shaft);
        TestPlayer p = new TestPlayer(server, "Faller");
        server.addPlayer(p);
        p.setGameMode(GameMode.SURVIVAL);
        w.getChunkAt(shaft[0] >> 4, shaft[1] >> 4);
        Location from = new Location(w, shaft[0] + 0.5, Level0Generator.LOOP_Y + 0.4, shaft[1] + 0.5, 37f, 20f);
        p.teleport(from);
        Location to = from.clone().add(0, -0.8, 0);
        server.getPluginManager().callEvent(new PlayerMoveEvent(p, from, to));
        assertEquals(Level0Generator.loopTarget(to.getY()), p.getLocation().getY(), 0.01, "carried up into the shaft over the upper floor");
        assertEquals(shaft[0] + 0.5, p.getLocation().getX(), 0.01, "same column");
        assertTrue(p.getLocation().getY() > g.floorY[Level0Generator.UP] + 5);
        EntityDamageEvent fall = new EntityDamageEvent(p, EntityDamageEvent.DamageCause.FALL,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FALL).build(), 12);
        server.getPluginManager().callEvent(fall);
        assertTrue(fall.isCancelled(), "falls in Level 0 never hurt");
    }
}
