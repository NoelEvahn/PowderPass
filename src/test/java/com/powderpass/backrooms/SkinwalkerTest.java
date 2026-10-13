package com.powderpass.backrooms;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import com.powderpass.backrooms.creature.Creature;
import com.powderpass.backrooms.creature.CreatureType;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pose;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/** The skinwalker against MockBukkit (with a test Mannequin): every lock, the human act, true form and release. */
class SkinwalkerTest {
    private ServerMock server;
    private BackroomsPlugin plugin;
    private World l0;
    private int n;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock(new TestServer());
        server.createWorld(new org.bukkit.WorldCreator("world"));
        plugin = MockBukkit.load(BackroomsPlugin.class);
        // only the skinwalker under test: no natural spawns
        plugin.getConfig().set("creatures.spawn-attempt-seconds", 3600);
        plugin.saveConfig();
        plugin.reload();
        l0 = plugin.worlds().world(Level.LEVEL_0);
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                l0.getBlockAt(x, 64, z).setType(Material.STONE);
                for (int y = 65; y <= 68; y++) l0.getBlockAt(x, y, z).setType(Material.AIR);
            }
        }
        for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) l0.getChunkAt(cx, cz);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private TestPlayer player(double x, double z) {
        TestPlayer p = new TestPlayer(server, "Walker" + (++n));
        server.addPlayer(p);
        p.setGameMode(GameMode.SURVIVAL);
        p.teleport(new Location(l0, x, 65, z));
        return p;
    }

    private Creature skinwalkerAt(double x, double z) {
        Creature c = plugin.creatures().spawn(CreatureType.SKINWALKER, new Location(l0, x, 65, z), null);
        warm();
        return c;
    }

    /** The creature tick starts 40 ticks after the plugin enables. */
    private boolean warmed;

    private void warm() {
        if (!warmed) {
            warmed = true;
            server.getScheduler().performTicks(40);
        }
    }

    private NamespacedKey victimKey() {
        return new NamespacedKey(plugin, "skinwalker_victim");
    }

    @Test
    void itWearsAnotherCreatureFirst() {
        player(10.5, 10.5);
        Creature c = skinwalkerAt(-10.5, -10.5);
        assertTrue(c.state().startsWith("skinwalker:mimic:"), c.state());
        String disguise = c.state().substring("skinwalker:mimic:".length());
        assertTrue(java.util.Set.of("faceling", "smiler", "watcher").contains(disguise), "Level 0 disguises: " + disguise);
    }

    @Test
    void aLonePlayerIsNotWornButHunted() {
        TestPlayer a = player(0.5, 0.5);
        Creature c = skinwalkerAt(1.5, 0.5);
        server.getScheduler().performTicks(10);
        assertEquals("skinwalker:true", c.state(), "alone: straight to its true form");
        assertEquals(GameMode.SURVIVAL, a.getGameMode());
        assertFalse(plugin.possessions().isVictim(a));
        double hp = a.getHealth();
        server.getScheduler().performTicks(80);
        assertTrue(a.getHealth() < hp || a.isDead(), "the true form attacks");
    }

    @Test
    void withCompanyItLocksThePlayerAndActsAsThem() {
        TestPlayer a = player(0.5, 0.5);
        TestPlayer b = player(6.5, 0.5);
        // something A said earlier, inside the Backrooms
        server.getPluginManager().callEvent(new AsyncChatEvent(false, a, new HashSet<>(), null,
                Component.text("guys wait for me"), Component.text("guys wait for me"), null));
        Creature c = skinwalkerAt(1.5, 0.5);
        server.getScheduler().performTicks(10);
        assertEquals("skinwalker:wearing", c.state());
        assertTrue(plugin.possessions().isVictim(a));
        Entity body = plugin.possessions().bodyOf(a);
        assertNotNull(body);
        assertEquals(GameMode.SPECTATOR, a.getGameMode(), "locked in spectator");
        assertSame(body, a.getSpectatorTarget(), "watching through its own eyes");
        assertEquals("SURVIVAL", a.getPersistentDataContainer().get(victimKey(), PersistentDataType.STRING), "original mode saved first");
        assertEquals(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(a.name()),
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(body.customName()), "wears their name");

        // the locks
        AsyncChatEvent chat = new AsyncChatEvent(false, a, new HashSet<>(), null, Component.text("help"), Component.text("help"), null);
        server.getPluginManager().callEvent(chat);
        assertTrue(chat.isCancelled(), "cannot type");
        PlayerCommandPreprocessEvent cmd = new PlayerCommandPreprocessEvent(a, "/spawn");
        server.getPluginManager().callEvent(cmd);
        assertTrue(cmd.isCancelled(), "cannot run commands");
        PlayerStopSpectatingEntityEvent stop = new PlayerStopSpectatingEntityEvent(a, body);
        server.getPluginManager().callEvent(stop);
        assertTrue(stop.isCancelled(), "cannot stop spectating");
        a.setGameMode(GameMode.SURVIVAL);
        assertEquals(GameMode.SPECTATOR, a.getGameMode(), "cannot switch game mode out");

        // it follows the group
        b.teleport(new Location(l0, 12.5, 65, 0.5));
        double before = body.getLocation().distance(b.getLocation());
        server.getScheduler().performTicks(80);
        assertTrue(body.getLocation().distance(b.getLocation()) < before - 2, "keeps up with the group");

        // punched by a friend: no damage, it punches back a moment later
        MannequinMock m = (MannequinMock) body;
        PrePlayerAttackEntityEvent hit = new PrePlayerAttackEntityEvent(b, body, true);
        b.teleport(body.getLocation().clone().add(1.5, 0, 0));
        server.getPluginManager().callEvent(hit);
        assertTrue(hit.isCancelled());
        server.getScheduler().performTicks(20);
        assertTrue(m.swings >= 1, "punches back");

        // it repeats what A said
        boolean said = false;
        for (int i = 0; i < 80 && !said; i++) {
            server.getScheduler().performTicks(20);
            String msg;
            while ((msg = b.nextMessage()) != null) if (msg.contains("guys wait for me")) said = true;
        }
        assertTrue(said, "repeats the victim's words in chat");
    }

    @Test
    void tooManyPunchesRevealItAndKillingItFreesTheVictim() {
        TestPlayer a = player(0.5, 0.5);
        TestPlayer b = player(4.5, 0.5);
        Creature c = skinwalkerAt(1.5, 0.5);
        server.getScheduler().performTicks(10);
        Entity body = plugin.possessions().bodyOf(a);
        assertNotNull(body);
        for (int i = 0; i < 6; i++) server.getPluginManager().callEvent(new PrePlayerAttackEntityEvent(b, body, true));
        server.getScheduler().performTicks(10);
        assertEquals("skinwalker:true", c.state(), "exposed");
        assertEquals(GameMode.SPECTATOR, a.getGameMode(), "the victim is still inside, carried");
        assertEquals(Pose.SWIMMING, ((MannequinMock) body).getPose(), "the body hangs limp under it");
        assertFalse(body.isCustomNameVisible());

        c.hurt(b, 500);
        assertTrue(c.dead());
        assertEquals(GameMode.SURVIVAL, a.getGameMode(), "killing it frees them");
        assertNull(a.getSpectatorTarget());
        assertFalse(a.getPersistentDataContainer().has(victimKey()));
        assertFalse(plugin.possessions().isVictim(a));
        assertFalse(body.isValid(), "the stolen body is gone");
    }

    @Test
    void ifItLastsTheVictimDies() {
        plugin.getConfig().set("skinwalker.true-form-seconds", 10);
        plugin.saveConfig();
        plugin.reload();
        warmed = false;
        for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) l0.getChunkAt(cx, cz);
        TestPlayer a = player(0.5, 0.5);
        TestPlayer b = player(4.5, 0.5);
        b.setGameMode(GameMode.CREATIVE); // a witness it will not hurt
        TestPlayer d = player(5.5, 0.5);
        Creature c = skinwalkerAt(1.5, 0.5);
        server.getScheduler().performTicks(10);
        assertTrue(plugin.possessions().isVictim(a));
        Entity body = plugin.possessions().bodyOf(a);
        for (int i = 0; i < 6; i++) server.getPluginManager().callEvent(new PrePlayerAttackEntityEvent(b, body, true));
        d.teleport(new Location(l0, 15.5, 65, 15.5));
        d.setGameMode(GameMode.CREATIVE);
        server.getScheduler().performTicks(20 * 12);
        assertFalse(plugin.possessions().isVictim(a));
        assertTrue(a.isDead() || a.getHealth() <= 0, "kept too long: they die");
        assertNotEquals(GameMode.SPECTATOR, a.getGameMode());
    }

    @Test
    void disconnectingOrAClearAlwaysGivesThePlayerBack() {
        TestPlayer a = player(0.5, 0.5);
        player(5.5, 0.5);
        Creature c = skinwalkerAt(1.5, 0.5);
        server.getScheduler().performTicks(10);
        assertTrue(plugin.possessions().isVictim(a));
        a.disconnect();
        assertEquals(GameMode.SURVIVAL, a.getGameMode(), "restored before their data is saved");
        assertFalse(a.getPersistentDataContainer().has(victimKey()));
        server.getScheduler().performTicks(6);
        assertEquals("skinwalker:true", c.state(), "nothing left to wear");

        // a second victim, then an admin clear
        TestPlayer e = player(0.5, 0.5);
        player(5.5, 0.5);
        plugin.creatures().clearAll();
        Creature c2 = skinwalkerAt(1.5, 0.5);
        server.getScheduler().performTicks(10);
        assertTrue(plugin.possessions().isVictim(e), c2.state());
        server.dispatchCommand(server.getConsoleSender(), "backrooms entity clear level level0");
        server.dispatchCommand(server.getConsoleSender(), "backrooms confirm");
        assertEquals(GameMode.SURVIVAL, e.getGameMode(), "clearing creatures frees victims");
    }

    @Test
    void aCrashLeftoverIsRestoredOnJoin() {
        TestPlayer a = player(0.5, 0.5);
        a.setGameMode(GameMode.SPECTATOR);
        a.getPersistentDataContainer().set(victimKey(), PersistentDataType.STRING, "ADVENTURE");
        server.getPluginManager().callEvent(new PlayerJoinEvent(a, Component.empty()));
        assertEquals(GameMode.ADVENTURE, a.getGameMode());
        assertFalse(a.getPersistentDataContainer().has(victimKey()));
    }

    @Test
    void voiceMimicryIsANoOpWithoutSimpleVoiceChat() {
        assertTrue(plugin.possessions().voiceState().contains("not installed"), plugin.possessions().voiceState());
    }
}
