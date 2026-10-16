package com.powderpass.backrooms;

import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.LevelGenerator;
import com.powderpass.backrooms.item.Items;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Supplies: picking one up gives its item, hides it for everyone, and stays taken across a restart. */
class SupplyTest {
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

    private Map<String, DecorSpec> supplies(LevelGenerator g) {
        Map<String, DecorSpec> out = new HashMap<>();
        for (int cx = -20; cx < 20; cx++) for (int cz = -20; cz < 20; cz++)
            for (DecorSpec d : g.decorations(cx, cz)) if (d.model.startsWith("supply:")) out.putIfAbsent(d.model, d);
        return out;
    }

    private int count(TestPlayer p, String id) {
        int n = 0;
        for (ItemStack s : p.getInventory().getContents()) if (s != null && id.equals(plugin.items().id(s))) n += s.getAmount();
        return n;
    }

    @Test
    void pickingUpGivesTheItemAndItStaysTaken() {
        World w = plugin.worlds().world(Level.LEVEL_0);
        Map<String, DecorSpec> found = supplies(plugin.worlds().generator(Level.LEVEL_0));
        for (String kind : new String[]{"supply:almond", "supply:battery", "supply:crate"}) assertTrue(found.containsKey(kind), "no " + kind + " near spawn");
        TestPlayer p = new TestPlayer(server, "Scav");
        server.addPlayer(p);
        p.teleport(new Location(w, 0.5, 65, 0.5));

        DecorSpec bottle = found.get("supply:almond");
        assertTrue(plugin.supplies().take(p, w, bottle));
        assertEquals(1, count(p, Items.ALMOND));
        assertTrue(plugin.decorations().hidden(w, bottle.key()), "gone for everyone");
        assertFalse(plugin.supplies().take(p, w, bottle), "only once");
        assertEquals(1, count(p, Items.ALMOND));

        DecorSpec battery = found.get("supply:battery");
        assertTrue(plugin.supplies().take(p, w, battery));
        assertTrue(count(p, Items.BATTERY) >= 1);

        int before = 0;
        for (ItemStack s : p.getInventory().getContents()) if (s != null) before++;
        assertTrue(plugin.supplies().take(p, w, found.get("supply:crate")));
        int after = 0;
        for (ItemStack s : p.getInventory().getContents()) if (s != null) after++;
        assertTrue(after > before, "the crate had something in it");

        // a restart: the service reloads what was taken and hides it again
        plugin.supplies().stop();
        plugin.decorations().unhide(w, bottle.key());
        plugin.supplies().start();
        assertTrue(plugin.decorations().hidden(w, bottle.key()), "remembered across restarts");
        assertTrue(plugin.supplies().taken(p, w, bottle));
    }
}
