package com.powderpass.backrooms;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.powderpass.backrooms.gen.CustomBlocks;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/** The textured blocks: registry, pack consistency, the note block freeze and the entry gate. */
class CustomBlocksTest {
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
    void everyCustomBlockHasAUniqueRarePoweredStateThatParses() {
        Set<String> states = new HashSet<>();
        for (CustomBlocks.Def d : CustomBlocks.all().values()) {
            assertTrue(states.add(d.instrument() + d.note()), "state shared: " + d.id());
            assertTrue(Set.of("piglin", "dragon", "wither_skeleton", "zombie", "skeleton", "creeper", "custom_head").contains(d.instrument()),
                    d.id() + " uses an instrument normal play produces without a mob head");
            BlockData bd = d.custom().data();
            assertEquals(Material.NOTE_BLOCK, bd.getMaterial());
            assertEquals(d, CustomBlocks.of(bd), "round trip through live block data for " + d.id());
            assertNotNull(d.fallback().data(), "fallback parses");
        }
        assertTrue(states.size() >= 18);
    }

    @Test
    void thePackRetexturesExactlyThoseStatesAndShipsEveryTexture() throws Exception {
        JsonObject variants = null;
        Set<String> files = new HashSet<>();
        try (InputStream in = plugin.getResource("backrooms-pack.zip"); ZipInputStream z = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                files.add(e.getName());
                if (e.getName().equals("assets/minecraft/blockstates/note_block.json")) {
                    variants = JsonParser.parseString(new String(z.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("variants");
                }
            }
        }
        assertNotNull(variants, "the pack overrides the note block blockstate");
        assertEquals(23 * 25 * 2, variants.size(), "every note block state is listed, so vanilla ones keep their model");
        int custom = 0;
        for (String k : variants.keySet()) {
            String model = variants.getAsJsonObject(k).get("model").getAsString();
            if (!model.startsWith("backrooms:")) {
                assertEquals("minecraft:block/note_block", model);
                continue;
            }
            custom++;
            String id = model.substring("backrooms:block/".length());
            CustomBlocks.Def d = CustomBlocks.all().get(id);
            assertNotNull(d, "model for an unknown block " + id);
            assertEquals("instrument=" + d.instrument() + ",note=" + d.note() + ",powered=true", k);
            assertTrue(files.contains("assets/backrooms/models/block/" + id + ".json"), "model file for " + id);
            assertTrue(files.contains("assets/backrooms/textures/block/" + id + ".png"), "texture for " + id);
        }
        assertEquals(CustomBlocks.all().size(), custom);
    }

    @Test
    void noteBlocksAreFrozenInsideTheBackroomsOnly() {
        World l0 = plugin.worlds().world(Level.LEVEL_0);
        Block in = l0.getBlockAt(3, 100, 3);
        in.setBlockData(CustomBlocks.custom("l0_wall").data(), false);
        BlockPhysicsEvent e = new BlockPhysicsEvent(in, in.getBlockData());
        server.getPluginManager().callEvent(e);
        assertTrue(e.isCancelled(), "a note block in the Backrooms ignores neighbour updates");
        Block out = server.getWorld("world").getBlockAt(3, 100, 3);
        out.setType(Material.NOTE_BLOCK);
        BlockPhysicsEvent e2 = new BlockPhysicsEvent(out, out.getBlockData());
        server.getPluginManager().callEvent(e2);
        assertFalse(e2.isCancelled(), "note blocks elsewhere behave normally");
    }

    @Test
    void nobodyIsLockedOutWhileThePackCannotBeDelivered() {
        TestPlayer p = new TestPlayer(server, "NoPack");
        server.addPlayer(p);
        p.teleport(new Location(server.getWorld("world"), 0.5, 70, 0.5));
        assertFalse(plugin.pack().deliverable(), "no reachable pack URL in tests");
        assertTrue(plugin.pack().mayEnter(p), "the gate never blocks while the pack itself is unavailable");
    }
}
