package com.powderpass.backrooms;

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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The skinwalker against MockBukkit: stalking, the take, the transformation, the possessed body carried by its
 * tentacles (the victim stays a normal survival player, never a spectator), the locks, release and recovery.
 */
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
        // only the skinwalker under test: no natural spawns; a short patience so tests stay quick
        plugin.getConfig().set("creatures.spawn-attempt-seconds", 3600);
        plugin.getConfig().set("skinwalker.patience-seconds", 2);
        plugin.saveConfig();
        plugin.reload();
        l0 = plugin.worlds().world(Level.LEVEL_0);
        for (int x = -24; x <= 24; x++) {
            for (int z = -24; z <= 24; z++) {
                l0.getBlockAt(x, 64, z).setType(Material.STONE);
                for (int y = 65; y <= 70; y++) l0.getBlockAt(x, y, z).setType(Material.AIR);
                l0.getBlockAt(x, 71, z).setType(Material.STONE);
            }
        }
        // a wall it must path round, never through
        for (int z = -24; z <= 6; z++) for (int y = 65; y <= 70; y++) l0.getBlockAt(12, y, z).setType(Material.STONE);
        for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) l0.getChunkAt(cx, cz);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private TestPlayer player(double x, double z) {
        TestPlayer p = new TestPlayer(server, "Walker" + (++n));
        p.clientPhysics = true;
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

    private NamespacedKey key(String k) {
        return new NamespacedKey(plugin, k);
    }

    /** Ticks until the predicate holds (or the limit runs out). */
    private boolean until(int maxTicks, java.util.function.BooleanSupplier ok) {
        for (int i = 0; i < maxTicks; i++) {
            if (ok.getAsBoolean()) return true;
            server.getScheduler().performTicks(1);
        }
        return ok.getAsBoolean();
    }

    private static double attr(TestPlayer p, Attribute a) {
        var inst = p.getAttribute(a);
        return inst == null ? Double.NaN : inst.getValue();
    }

    /** A takes the skinwalker (B keeps them company), through the whole transformation. */
    private Creature taken(TestPlayer a) {
        Creature c = skinwalkerAt(a.getX() + 1.2, a.getZ());
        assertTrue(until(20 * 6, () -> plugin.possessions().isVictim(a)), "a still player with company is taken: " + c.state());
        return c;
    }

    @Test
    void itWearsAnotherCreatureFirst() {
        player(10.5, 10.5);
        Creature c = skinwalkerAt(-10.5, -10.5);
        assertTrue(c.state().startsWith("skinwalker:mimic:"), c.state());
        String disguise = c.state().substring("skinwalker:mimic:".length());
        assertTrue(java.util.Set.of("bacteria", "watcher").contains(disguise), "Level 0 disguises: " + disguise);
    }

    @Test
    void itStalksInsteadOfWalkingStraightAtYou() {
        TestPlayer a = player(0.5, 0.5);
        Creature c = skinwalkerAt(-9.5, 0.5);
        // the player keeps moving, so the patience timer never runs out: it should never close to arm's reach
        double closest = 99;
        for (int i = 0; i < 200; i++) {
            Location l = a.getLocation();
            // a slow wander (about 3 blocks per second), always moving
            a.teleport(l.clone().add(Math.cos(i * 0.02) * 0.15, 0, Math.sin(i * 0.02) * 0.15));
            server.getScheduler().performTicks(1);
            closest = Math.min(closest, c.loc().distance(a.getLocation()));
        }
        assertTrue(closest > 2.5, "kept its distance from a moving player: " + closest);
        assertEquals(20.0, a.getHealth(), 1e-9, "a moving player is never attacked");
        assertFalse(plugin.possessions().isVictim(a));
        assertTrue(c.state().startsWith("skinwalker:mimic"), c.state());
    }

    @Test
    void aLonePlayerIsStruckNotTaken() {
        TestPlayer a = player(0.5, 0.5);
        double hp = a.getHealth();
        Creature c = skinwalkerAt(-6.5, 0.5);
        assertTrue(until(20 * 10, () -> a.getHealth() < hp), "standing still too long alone: it strikes");
        assertFalse(plugin.possessions().isVictim(a), "a lone player is never taken");
        assertEquals(GameMode.SURVIVAL, a.getGameMode());
        assertTrue(c.state().startsWith("skinwalker:mimic"), c.state());
    }

    @Test
    void withCompanyItTakesThemAndTheyStayInTheirBody() {
        TestPlayer a = player(0.5, 0.5);
        TestPlayer b = player(6.5, 0.5);
        server.getPluginManager().callEvent(new AsyncChatEvent(false, a, new HashSet<>(), null,
                Component.text("guys wait for me"), Component.text("guys wait for me"), null));
        Creature c = taken(a);
        assertEquals("skinwalker:transforming", c.state());
        assertEquals(Boolean.TRUE, plugin.possessions().transforming(a));

        // no spectator mode: still themselves, survival, with their state saved first
        assertEquals(GameMode.SURVIVAL, a.getGameMode(), "never put in spectator");
        assertNull(a.getSpectatorTarget());
        assertEquals("SURVIVAL", a.getPersistentDataContainer().get(key("skinwalker_victim"), PersistentDataType.STRING));
        assertTrue(a.getPersistentDataContainer().has(key("skinwalker_flight"), PersistentDataType.BYTE), "flight and gravity saved");
        // frozen: their own walking, jumping and gravity are gone; they cannot fly
        assertEquals(0, attr(a, Attribute.MOVEMENT_SPEED), 1e-9, "cannot walk");
        double jump = attr(a, Attribute.JUMP_STRENGTH); // not registered for players on MockBukkit
        if (!Double.isNaN(jump)) assertEquals(0, jump, 1e-9, "cannot jump");
        assertFalse(a.hasGravity());
        PlayerToggleFlightEvent fly = new PlayerToggleFlightEvent(a, true);
        server.getPluginManager().callEvent(fly);
        assertTrue(fly.isCancelled(), "cannot fly away");
        assertEquals(0f, a.getFlySpeed());

        // the organism rides them: every tentacle part is a passenger, so it can never drift off their back
        List<Entity> parts = plugin.possessions().organismOf(a);
        assertFalse(parts.isEmpty());
        for (Entity e : parts) assertTrue(a.getPassengers().contains(e), "attached: " + e.getType());
        long displays = parts.stream().filter(e -> e instanceof BlockDisplay).count();
        assertTrue(displays >= 60 && displays <= 110, "a detailed but bounded rig: " + displays);

        // the locks
        AsyncChatEvent chat = new AsyncChatEvent(false, a, new HashSet<>(), null, Component.text("help"), Component.text("help"), null);
        server.getPluginManager().callEvent(chat);
        assertTrue(chat.isCancelled(), "cannot type");
        PlayerCommandPreprocessEvent cmd = new PlayerCommandPreprocessEvent(a, "/spawn");
        server.getPluginManager().callEvent(cmd);
        assertTrue(cmd.isCancelled(), "cannot run commands");
        PlayerTeleportEvent tp = new PlayerTeleportEvent(a, a.getLocation(), a.getLocation().add(30, 0, 0), PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        server.getPluginManager().callEvent(tp);
        assertTrue(tp.isCancelled(), "no pearls or other teleports out");
        a.setGameMode(GameMode.CREATIVE);
        assertEquals(GameMode.SURVIVAL, a.getGameMode(), "cannot switch game mode out");

        // the transformation: tentacles grow out, plant, and lift them off the ground over ~5.5 s
        assertTrue(until(130, () -> c.state().equals("skinwalker:possessed")), c.state());
        assertTrue(a.getLocation().getY() > 65.3, "lifted off the ground: " + a.getLocation().getY());
        assertTrue(a.getLocation().getY() < 66.6, "only slightly: " + a.getLocation().getY());
        int visible = 0;
        for (Entity e : parts) {
            if (e instanceof BlockDisplay d && d.getTransformation().getScale().length() > 0.05f) visible++;
        }
        assertTrue(visible >= 55, "the tentacles have grown out: " + visible);
        // the legs reach the floor: some part of the rig comes down to the ground (feet hover ~0.7 above it)
        double lowest = 99;
        for (Entity e : parts) {
            if (!(e instanceof BlockDisplay d)) continue;
            var tr = d.getTransformation();
            org.joml.Matrix4f m = new org.joml.Matrix4f().translation(tr.getTranslation()).rotate(tr.getLeftRotation())
                    .scale(tr.getScale()).rotate(tr.getRightRotation());
            for (float cx = 0; cx <= 1; cx++) for (float cy = 0; cy <= 1; cy++) for (float cz = 0; cz <= 1; cz++) {
                Vector3f corner = m.transformPosition(new Vector3f(cx, cy, cz));
                lowest = Math.min(lowest, a.getLocation().getY() + a.getHeight() + corner.y);
            }
        }
        assertTrue(lowest < 65.4, "a leg is planted on the floor: " + lowest);

        // it repeats what A said, to the others
        boolean said = false;
        for (int i = 0; i < 100 && !said; i++) {
            server.getScheduler().performTicks(20);
            String msg;
            while ((msg = b.nextMessage()) != null) if (msg.contains("guys wait for me")) said = true;
        }
        assertTrue(said || !plugin.possessions().isVictim(a), "repeats the victim's words in chat");
    }

    @Test
    void itCarriesTheBodyOverRealGroundAndAttacksWhoeverStandsStill() {
        TestPlayer a = player(0.5, 0.5);
        TestPlayer b = player(4.5, 0.5);
        Creature c = taken(a);
        assertTrue(until(130, () -> c.state().equals("skinwalker:possessed")));
        double hp = b.getHealth();
        boolean hurt = false;
        for (int i = 0; i < 20 * 12 && !hurt; i++) {
            server.getScheduler().performTicks(1);
            Location root = c.loc();
            // the body never stands inside a block
            assertTrue(l0.getBlockAt(root.getBlockX(), root.getBlockY(), root.getBlockZ()).isPassable(), "inside a block at " + root);
            assertTrue(l0.getBlockAt(root.getBlockX(), root.getBlockY() + 1, root.getBlockZ()).isPassable(), "head in a block at " + root);
            // the victim is pulled along with it by velocity, hovering just above the body
            assertTrue(a.getLocation().distance(root) < 1.6, "victim stays with the body: " + a.getLocation().distance(root));
            assertEquals(65.0, root.getY(), 0.6, "it walks on the floor");
            hurt = b.getHealth() < hp;
        }
        assertTrue(hurt, "B stood still for too long: it went for them");
        assertTrue(plugin.possessions().isVictim(a) || a.isDead());
        assertEquals(a.getHealth(), a.getHealth()); // the victim takes nothing from its own attacks
    }

    @Test
    void hittingTheVictimHurtsTheOrganismAndKillingItFreesThem() {
        TestPlayer a = player(0.5, 0.5);
        TestPlayer b = player(4.5, 0.5);
        Creature c = taken(a);
        double hp = a.getHealth();
        PrePlayerAttackEntityEvent hit = new PrePlayerAttackEntityEvent(b, a, true);
        server.getPluginManager().callEvent(hit);
        assertTrue(hit.isCancelled(), "the victim's friends never hurt the victim");
        assertEquals(hp, a.getHealth());
        // the victim cannot fight either
        PrePlayerAttackEntityEvent own = new PrePlayerAttackEntityEvent(a, b, true);
        server.getPluginManager().callEvent(own);
        assertTrue(own.isCancelled());

        c.hurt(b, 500);
        assertTrue(c.dead());
        assertFalse(plugin.possessions().isVictim(a), "killing it frees them");
        assertEquals(GameMode.SURVIVAL, a.getGameMode());
        assertTrue(a.getPassengers().isEmpty(), "the organism is gone from their back");
        assertTrue(a.hasGravity(), "gravity back");
        assertFalse(a.getAllowFlight(), "no flight left over");
        assertTrue(attr(a, Attribute.MOVEMENT_SPEED) > 0, "can walk again");
        double jump = attr(a, Attribute.JUMP_STRENGTH);
        if (!Double.isNaN(jump)) assertTrue(jump > 0, "can jump again");
        assertFalse(a.getPersistentDataContainer().has(key("skinwalker_victim")));
        assertFalse(a.getPersistentDataContainer().has(key("skinwalker_flight")));
    }

    @Test
    void ifItLastsTheVictimIsTaken() {
        plugin.getConfig().set("skinwalker.possession-seconds", 10);
        plugin.saveConfig();
        plugin.reload();
        warmed = false;
        for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) l0.getChunkAt(cx, cz);
        TestPlayer a = player(0.5, 0.5);
        TestPlayer b = player(4.5, 0.5);
        Creature c = taken(a);
        b.setGameMode(GameMode.CREATIVE); // a witness it will not hurt
        server.getScheduler().performTicks(20 * 18);
        assertFalse(plugin.possessions().isVictim(a));
        // kept too long it kills them; nobody dies in the Backrooms, so that is the death screen, then waking up
        // wherever death.mode says (RETURN: out of the Backrooms)
        assertFalse(a.isDead(), "the death screen instead of a death");
        assertTrue(a.getWorld() != l0 || plugin.deaths().playing(a), "kept too long: it took them (death screen)");
        assertTrue(c.dead());
    }

    @Test
    void disconnectingOrAClearAlwaysGivesThePlayerBack() {
        TestPlayer a = player(0.5, 0.5);
        player(5.5, 0.5);
        Creature c = taken(a);
        a.disconnect();
        assertFalse(a.getPersistentDataContainer().has(key("skinwalker_victim")), "restored before their data is saved");
        assertTrue(a.hasGravity());
        assertTrue(attr(a, Attribute.MOVEMENT_SPEED) > 0);
        server.getScheduler().performTicks(6);
        assertTrue(c.dead(), "the organism dies without its body");

        // a second victim, then an admin clear
        TestPlayer e = player(-6.5, -6.5);
        player(-2.5, -6.5);
        plugin.creatures().clearAll();
        Creature c2 = taken(e);
        assertTrue(plugin.possessions().isVictim(e), c2.state());
        server.dispatchCommand(server.getConsoleSender(), "backrooms creature clear level level0");
        server.dispatchCommand(server.getConsoleSender(), "backrooms confirm");
        assertFalse(plugin.possessions().isVictim(e), "clearing creatures frees victims");
        assertTrue(e.getPassengers().isEmpty());
        assertTrue(e.hasGravity());
    }

    @Test
    void aCrashLeftoverIsRestoredOnJoin() {
        TestPlayer a = player(0.5, 0.5);
        // what a crash mid-possession leaves on a player: the flags and the attribute locks
        a.getPersistentDataContainer().set(key("skinwalker_victim"), PersistentDataType.STRING, "ADVENTURE");
        a.getPersistentDataContainer().set(key("skinwalker_flight"), PersistentDataType.BYTE, (byte) 4);
        var speed = a.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) speed.addModifier(new AttributeModifier(key("skinwalker_lock_speed"), -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        a.setGravity(false);
        a.setAllowFlight(true);
        server.getPluginManager().callEvent(new PlayerJoinEvent(a, Component.empty()));
        assertEquals(GameMode.ADVENTURE, a.getGameMode());
        assertFalse(a.getPersistentDataContainer().has(key("skinwalker_victim")));
        assertTrue(a.hasGravity());
        assertFalse(a.getAllowFlight());
        if (speed != null) assertTrue(speed.getValue() > 0, "the speed lock is gone");
    }

    @Test
    void voiceMimicryIsANoOpWithoutSimpleVoiceChat() {
        assertTrue(plugin.possessions().voiceState().contains("not installed"), plugin.possessions().voiceState());
    }
}
