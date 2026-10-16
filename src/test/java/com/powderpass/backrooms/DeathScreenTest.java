package com.powderpass.backrooms;

import com.powderpass.backrooms.player.DeathScreen;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

/** Nobody dies in the Backrooms: a killing blow plays the glitch screen and they wake up per death.mode. */
class DeathScreenTest {
    private ServerMock server;
    private BackroomsPlugin plugin;
    private World overworld, l0;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock(new TestServer());
        overworld = server.createWorld(new org.bukkit.WorldCreator("world"));
        plugin = MockBukkit.load(BackroomsPlugin.class);
        l0 = plugin.worlds().world(Level.LEVEL_0);
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            l0.getBlockAt(x, 64, z).setType(Material.STONE);
            l0.getBlockAt(x, 65, z).setType(Material.AIR);
            l0.getBlockAt(x, 66, z).setType(Material.AIR);
            overworld.getBlockAt(x + 3, 69, z + 4).setType(Material.STONE);
        }
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aKillingBlowPlaysTheScreenInsteadOfDeath() {
        TestPlayer p = new TestPlayer(server, "Doomed");
        server.addPlayer(p);
        p.setGameMode(GameMode.SURVIVAL);
        p.teleport(new Location(overworld, 3.5, 70, 4.5));
        plugin.sessions().saveReturn(p, new Location(overworld, 3.5, 70, 4.5));
        p.teleport(new Location(l0, 0.5, 65, 0.5));
        p.damage(500);
        server.getScheduler().performTicks(1); // (the mock skips the damage event; the death safety net heals a tick later)
        assertFalse(p.isDead(), "not dead");
        assertTrue(p.getHealth() > 0, "healed");
        assertTrue(plugin.deaths().playing(p), "the death screen plays");
        p.damage(500);
        assertTrue(plugin.deaths().playing(p) && !p.isDead(), "untouchable while it plays");
        server.getScheduler().performTicks(DeathScreen.LENGTH + 40);
        assertFalse(plugin.deaths().playing(p));
        assertEquals(overworld, p.getWorld(), "death.mode RETURN: woke up at the return point");
        assertEquals(20.0, p.getHealth(), 1e-6);
    }

    @Test
    void outsideTheBackroomsDeathIsVanilla() {
        TestPlayer p = new TestPlayer(server, "Mortal");
        server.addPlayer(p);
        p.setGameMode(GameMode.SURVIVAL);
        p.teleport(new Location(overworld, 3.5, 70, 4.5));
        p.damage(500);
        assertTrue(p.isDead() || p.getHealth() <= 0);
        assertFalse(plugin.deaths().playing(p));
    }

    @Test
    void theGlitchLineKeepsItsWordsWhenSettled() {
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(DeathScreenAccess.glitch("YOU TRIED", 0.0));
        assertEquals("YOU TRIED", plain.trim());
    }
}
