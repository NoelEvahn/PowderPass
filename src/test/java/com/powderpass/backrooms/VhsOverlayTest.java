package com.powderpass.backrooms;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

/** The camcorder overlay item: worn inside with the pack, moved helmet, gone on leaving. */
class VhsOverlayTest {
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

    @Test
    void wornInsideRemovedOutside() {
        TestPlayer p = new TestPlayer(server, "Cam");
        server.addPlayer(p);
        plugin.pack().markLoaded(p, true);
        p.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        p.teleport(new Location(plugin.worlds().world(Level.LEVEL_0), 0.5, 65, 0.5));
        server.getScheduler().performTicks(30);
        assertTrue(plugin.vhs().wearing(p), "the overlay item is on their head");
        assertTrue(p.getInventory().contains(Material.IRON_HELMET), "their helmet went into the inventory");
        p.teleport(new Location(overworld, 0.5, 70, 0.5));
        server.getScheduler().performTicks(5);
        assertFalse(plugin.vhs().wearing(p), "gone outside");
        for (ItemStack it : p.getInventory().getContents()) assertFalse(plugin.vhs().isVhs(it));
    }

    @Test
    void notWithoutThePack() {
        TestPlayer p = new TestPlayer(server, "NoPack");
        server.addPlayer(p);
        p.teleport(new Location(plugin.worlds().world(Level.LEVEL_0), 0.5, 65, 0.5));
        server.getScheduler().performTicks(30);
        assertFalse(plugin.vhs().wearing(p));
    }
}
