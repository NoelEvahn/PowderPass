package com.powderpass.backrooms;

import com.powderpass.backrooms.creature.Creature;
import com.powderpass.backrooms.creature.CreatureType;
import com.powderpass.backrooms.display.Keys;
import com.powderpass.backrooms.gen.Feature;
import com.powderpass.backrooms.gen.LevelGenerator;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plugin lifecycle against MockBukkit. MockBukkit worlds do not run chunk generators, so tests that teleport
 * prepare a small floor at the destination; everything else (commands, permissions, persistence, listeners,
 * display/creature lifecycles) runs through the real plugin code.
 */
class PluginIntegrationTest {
    private ServerMock server;
    private BackroomsPlugin plugin;
    private WorldMock overworld;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock(new TestServer());
        overworld = (WorldMock) server.createWorld(new org.bukkit.WorldCreator("world"));
        plugin = MockBukkit.load(BackroomsPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static void floor(World w, int cx, int y, int cz, int r) {
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                w.getBlockAt(x, y, z).setType(Material.STONE);
                w.getBlockAt(x, y + 1, z).setType(Material.AIR);
                w.getBlockAt(x, y + 2, z).setType(Material.AIR);
            }
        }
    }

    private int players;

    private PlayerMock playerAt(int x, int y, int z) {
        PlayerMock p = new TestPlayer(server, "Wanderer" + (++players));
        server.addPlayer(p);
        floor(overworld, x, y - 1, z, 2);
        p.teleport(new Location(overworld, x + 0.5, y, z + 0.5));
        return p;
    }

    @Test
    void createsSevenSeparateWorlds() {
        assertEquals(Level.values().length, plugin.worlds().worlds().size());
        for (Level l : Level.values()) {
            World w = plugin.worlds().world(l);
            assertNotNull(w, l + " world");
            assertEquals("backrooms_" + l.key(), w.getName());
            assertNotSame(overworld, w);
            assertEquals(l, plugin.worlds().levelOf(w));
        }
        assertFalse(plugin.worlds().isBackrooms(overworld));
        assertTrue(plugin.data().hasBaseSeed());
    }

    @Test
    void lockedSnapshotSurvivesConfigChanges() {
        long seed = plugin.data().baseSeed();
        plugin.getConfig().set("seed", 12345L);
        plugin.getConfig().set("levels.level0.generation.hall-chance", 0.9);
        plugin.reload();
        assertEquals(seed, plugin.data().baseSeed(), "base seed must not change once worlds exist");
        assertEquals(0.08, plugin.data().levelGen(Level.LEVEL_0).get("hall-chance", -1), 1e-9);
        assertEquals(Level.values().length, plugin.worlds().worlds().size(), "reload must not unload or duplicate worlds");
    }

    @Test
    void adminCommandsArePermissionGated() {
        PlayerMock p = playerAt(0, 70, 0);
        p.performCommand("backrooms level 1");
        assertEquals(overworld, p.getWorld());
        assertTrue(p.nextComponentMessage() != null);
        p.performCommand("backrooms entity spawn hound");
        p.performCommand("backrooms reload");
        p.performCommand("backrooms setspawn level0");
        assertEquals(overworld, p.getWorld());
        assertTrue(plugin.data().spawn(Level.LEVEL_0) == null);
        // the leave command is closed to ordinary players by default
        assertTrue(p.hasPermission("backrooms.use"));
        assertFalse(p.hasPermission("backrooms.admin"));
    }

    @Test
    void enterSavesReturnPointAndLeaveRestoresIt() {
        PlayerMock p = playerAt(10, 70, -4);
        Location target = plugin.sessions().entryTarget(p, false);
        floor(target.getWorld(), target.getBlockX(), target.getBlockY() - 1, target.getBlockZ(), 3);
        assertTrue(p.performCommand("backrooms enter"));
        server.getScheduler().performTicks(10);
        assertEquals(plugin.worlds().world(Level.LEVEL_0), p.getWorld(), "player should be in Level 0");
        assertTrue(plugin.sessions().insideFlag(p));
        Location ret = plugin.sessions().getReturn(p);
        assertEquals(overworld, ret.getWorld());
        assertEquals(10, ret.getBlockX());
        assertEquals(-4, ret.getBlockZ());

        // players cannot simply walk out by command...
        p.performCommand("backrooms leave");
        server.getScheduler().performTicks(10);
        assertEquals(plugin.worlds().world(Level.LEVEL_0), p.getWorld());
        // ...but an admin (console) can send them home
        server.dispatchCommand(server.getConsoleSender(), "backrooms leave " + p.getName());
        server.getScheduler().performTicks(10);
        assertEquals(overworld, p.getWorld());
        assertEquals(10, p.getLocation().getBlockX());
        assertFalse(plugin.sessions().insideFlag(p), "inside flag cleared after leaving");
    }

    @Test
    void reEnteringWhileInsideKeepsOriginalReturnPoint() {
        PlayerMock p = playerAt(3, 70, 3);
        p.setOp(true);
        Location t1 = plugin.sessions().entryTarget(p, false);
        floor(t1.getWorld(), t1.getBlockX(), t1.getBlockY() - 1, t1.getBlockZ(), 3);
        p.performCommand("backrooms enter");
        server.getScheduler().performTicks(10);
        Location t2 = plugin.sessions().entryTarget(p, false);
        floor(t2.getWorld(), t2.getBlockX(), t2.getBlockY() - 1, t2.getBlockZ(), 3);
        p.performCommand("backrooms enter");
        server.getScheduler().performTicks(10);
        assertEquals(3, plugin.sessions().getReturn(p).getBlockX(), "return point must not be overwritten from inside");
    }

    @Test
    void playersArriveInDifferentPlaces() {
        PlayerMock a = playerAt(0, 70, 0);
        PlayerMock b = playerAt(5, 70, 5);
        Location ta = plugin.sessions().entryTarget(a, false), tb = plugin.sessions().entryTarget(b, false);
        assertNotEquals(ta.getBlockX() + "," + ta.getBlockZ(), tb.getBlockX() + "," + tb.getBlockZ());
        LevelGenerator g = plugin.worlds().generator(Level.LEVEL_0);
        assertTrue(g.isSafeCell(0, g.cellOf(ta.getBlockX()), g.cellOf(ta.getBlockZ())));
        assertNull(g.triggerAt(ta.getBlockX(), ta.getBlockY(), ta.getBlockZ()));
    }

    @Test
    void restrictionsApplyOnlyInside() {
        PlayerMock p = playerAt(0, 70, 0);
        BlockBreakEvent outside = new BlockBreakEvent(overworld.getBlockAt(0, 69, 0), p);
        server.getPluginManager().callEvent(outside);
        assertFalse(outside.isCancelled());

        World l0 = plugin.worlds().world(Level.LEVEL_0);
        floor(l0, 0, 64, 0, 2);
        p.teleport(new Location(l0, 0.5, 65, 0.5));
        BlockBreakEvent inside = new BlockBreakEvent(l0.getBlockAt(0, 64, 0), p);
        server.getPluginManager().callEvent(inside);
        assertTrue(inside.isCancelled(), "blocks are protected inside");

        PlayerCommandPreprocessEvent home = new PlayerCommandPreprocessEvent(p, "/home base");
        server.getPluginManager().callEvent(home);
        assertTrue(home.isCancelled(), "escape commands are blocked inside");
        PlayerCommandPreprocessEvent other = new PlayerCommandPreprocessEvent(p, "/msg friend hi");
        server.getPluginManager().callEvent(other);
        assertFalse(other.isCancelled());
        p.setOp(true);
        PlayerCommandPreprocessEvent opHome = new PlayerCommandPreprocessEvent(p, "/essentials:home");
        server.getPluginManager().callEvent(opHome);
        assertFalse(opHome.isCancelled(), "bypass permission allows blocked commands");
    }

    @Test
    void transitionTriggerMovesPlayerToTheNextLevel() {
        LevelGenerator g0 = plugin.worlds().generator(Level.POOLROOMS);
        Feature stair = null;
        for (int d = 0; d < 9 && stair == null; d++) {
            for (Feature f : g0.districtFeatures(d % 3, d / 3)) {
                if (f.id().equals("service_door")) {
                    stair = f;
                    break;
                }
            }
        }
        assertNotNull(stair);
        World l0 = plugin.worlds().world(Level.POOLROOMS);
        int x = stair.cx() * g0.S + 6, z = stair.cz() * g0.S + 6, y = g0.floorY[stair.floor()] + 1;
        assertNotNull(g0.triggerAt(x, y, z), "standing in the booth is inside the trigger");
        PlayerMock p = playerAt(0, 70, 0);
        floor(l0, x, y - 1, z, 1);
        p.teleport(new Location(l0, x + 0.5, y, z + 0.5));
        // destination: prepare the floor around every candidate spot near the target
        World l1 = plugin.worlds().world(Level.LEVEL_1);
        LevelGenerator g1 = plugin.worlds().generator(Level.LEVEL_1);
        floor(l1, x, g1.floorY[0], z, 130);
        plugin.sessions().get(p).cooldownUntil = 0;
        plugin.transitions().onMove(p, p.getLocation());
        assertTrue(plugin.sessions().get(p).inTransition);
        server.getScheduler().performTicks(60);
        assertEquals(l1, p.getWorld(), "the Poolrooms service door leads to Level 1");
        assertFalse(plugin.sessions().get(p).inTransition);
        assertTrue(plugin.sessions().get(p).cooldownUntil > plugin.sessions().now(), "arrival cooldown prevents bouncing back");
    }

    @Test
    void creaturesAreHittableAndLeaveNoEntitiesBehind() {
        World l1 = plugin.worlds().world(Level.LEVEL_1);
        floor(l1, 0, 64, 0, 6);
        PlayerMock p = playerAt(0, 70, 0);
        p.teleport(new Location(l1, 0.5, 65, 0.5));
        Creature hound = plugin.creatures().spawn(CreatureType.HOUND, new Location(l1, 3.5, 65, 3.5), null);
        assertEquals(1, plugin.creatures().size());
        long tagged = l1.getEntities().stream().filter(e -> e.getPersistentDataContainer().has(Keys.CREATURE, PersistentDataType.STRING)).count();
        assertEquals(hound.entityCount(), tagged, "every body part and the hitbox are tagged");
        assertTrue(l1.getEntities().stream().filter(e -> e.getPersistentDataContainer().has(Keys.CREATURE, PersistentDataType.STRING)).noneMatch(Entity::isPersistent),
                "creature entities are never saved to disk");
        Interaction hitbox = (Interaction) l1.getEntities().stream().filter(e -> e instanceof Interaction).findFirst().orElseThrow();
        assertSame(hound, plugin.creatures().byHitbox(hitbox));
        for (int i = 0; i < 40 && !hound.dead(); i++) {
            server.getPluginManager().callEvent(new PrePlayerAttackEntityEvent(p, hitbox, true));
        }
        assertTrue(hound.dead(), "hound dies after enough hits");
        assertEquals(0, plugin.creatures().size());
        assertEquals(0, l1.getEntities().stream().filter(e -> e.getPersistentDataContainer().has(Keys.CREATURE, PersistentDataType.STRING)).count());

        plugin.creatures().spawn(CreatureType.HOUND, new Location(l1, 2.5, 65, 2.5), null);
        plugin.creatures().spawn(CreatureType.SMILER, new Location(l1, -2.5, 65, 2.5), null);
        server.dispatchCommand(server.getConsoleSender(), "backrooms entity clear level level1");
        assertEquals(2, plugin.creatures().size(), "level-wide clear waits for confirmation");
        server.dispatchCommand(server.getConsoleSender(), "backrooms confirm");
        assertEquals(0, plugin.creatures().size());
    }

    @Test
    void decorationsSpawnNearPlayersAndAreCleanedUp() {
        World l4 = plugin.worlds().world(Level.LEVEL_4);
        PlayerMock p = playerAt(0, 70, 0);
        floor(l4, 0, 64, 0, 3);
        p.teleport(new Location(l4, 0.5, 65, 0.5));
        int radius = plugin.cfg().activationRadius;
        for (int cx = -radius; cx <= radius; cx++) for (int cz = -radius; cz <= radius; cz++) l4.getChunkAt(cx, cz).load();
        server.getScheduler().performTicks(200);
        int managed = plugin.decorations().entityCount(l4);
        assertTrue(managed > 0, "props should spawn around the player");
        assertTrue(managed <= plugin.cfg().maxPerWorld);
        long tagged = l4.getEntities().stream().filter(e -> e.getPersistentDataContainer().has(Keys.DECOR, PersistentDataType.STRING)).count();
        assertEquals(managed, tagged);
        assertTrue(l4.getEntities().stream().filter(e -> e.getPersistentDataContainer().has(Keys.DECOR, PersistentDataType.STRING)).noneMatch(Entity::isPersistent));
        // leaving the area despawns everything
        p.teleport(new Location(overworld, 0.5, 70, 0.5));
        server.getScheduler().performTicks(40);
        assertEquals(0, plugin.decorations().entityCount(l4));
        assertEquals(0, l4.getEntities().stream().filter(e -> e.getPersistentDataContainer().has(Keys.DECOR, PersistentDataType.STRING)).count());
    }

    @Test
    void disableRemovesAllRuntimeEntities() {
        World l3 = plugin.worlds().world(Level.LEVEL_1);
        PlayerMock p = playerAt(0, 70, 0);
        floor(l3, 0, 64, 0, 4);
        p.teleport(new Location(l3, 0.5, 65, 0.5));
        for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) l3.getChunkAt(cx, cz).load();
        plugin.creatures().spawn(CreatureType.HOUND, new Location(l3, 2.5, 65, 2.5), null);
        server.getScheduler().performTicks(100);
        server.getPluginManager().disablePlugin(plugin);
        long leftovers = l3.getEntities().stream().filter(e -> e.getPersistentDataContainer().has(Keys.DECOR, PersistentDataType.STRING)
                || e.getPersistentDataContainer().has(Keys.CREATURE, PersistentDataType.STRING)).count();
        assertEquals(0, leftovers);
    }

    @Test
    void tabCompletionOffersLevelsAndHidesAdminForPlayers() {
        PlayerMock p = playerAt(0, 70, 0);
        var plain = server.getCommandTabComplete(p, "backrooms ");
        assertTrue(plain.contains("levels"));
        assertFalse(plain.contains("entity"));
        p.setOp(true);
        var op = server.getCommandTabComplete(p, "backrooms level ");
        assertTrue(op.containsAll(java.util.List.of("0", "1", "poolrooms", "4", "plains")));
    }

    @Test
    void enteringIsAFallThroughAnOpenedCeiling() {
        PlayerMock p = playerAt(10, 70, -4);
        Location target = plugin.sessions().entryTarget(p, false);
        World w = target.getWorld();
        LevelGenerator g = plugin.worlds().generator(Level.LEVEL_0);
        int x = target.getBlockX(), y = target.getBlockY(), z = target.getBlockZ();
        floor(w, x, y - 1, z, 3);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                w.getBlockAt(x + dx, y + 4, z + dz).setType(Material.WHITE_TERRACOTTA);
                w.getBlockAt(x + dx, g.roofY, z + dz).setType(Material.BEDROCK);
            }
        }
        assertTrue(p.performCommand("backrooms enter"));
        server.getScheduler().performTicks(5);
        assertEquals(w, p.getWorld());
        assertTrue(p.getLocation().getY() > g.roofY + 20, "starts high above the roof, was " + p.getLocation().getY());
        assertTrue(plugin.falls().falling(p));

        // falling: sideways movement is locked and the shaft opens before the roof
        Location from = p.getLocation();
        Location mid = new Location(w, x + 4.5, g.roofY + 10, z - 3.5);
        org.bukkit.event.player.PlayerMoveEvent move = new org.bukkit.event.player.PlayerMoveEvent(p, from, mid);
        server.getPluginManager().callEvent(move);
        assertEquals(x + 0.5, move.getTo().getX(), 1e-6);
        assertEquals(z + 0.5, move.getTo().getZ(), 1e-6);
        assertEquals(Material.AIR, w.getBlockAt(x, g.roofY, z).getType(), "roof opened");
        assertEquals(Material.AIR, w.getBlockAt(x + 1, y + 4, z - 1).getType(), "ceiling opened 3x3");
        assertEquals(Material.WHITE_TERRACOTTA, w.getBlockAt(x + 2, y + 4, z).getType(), "only 3x3");

        // fall damage is cancelled
        org.bukkit.event.entity.EntityDamageEvent fall = new org.bukkit.event.entity.EntityDamageEvent(p,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FALL).build(), 40);
        server.getPluginManager().callEvent(fall);
        assertTrue(fall.isCancelled());

        // landing
        p.teleport(move.getTo());
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerMoveEvent(p, move.getTo(), new Location(w, x + 0.5, y, z + 0.5)));
        assertFalse(plugin.falls().falling(p), "landed");
        assertTrue(plugin.sessions().insideFlag(p));
        org.bukkit.event.entity.EntityDamageEvent late = new org.bukkit.event.entity.EntityDamageEvent(p,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FALL).build(), 40);
        server.getPluginManager().callEvent(late);
        assertTrue(late.isCancelled(), "landing grace");

        server.getScheduler().performTicks(60);
        assertEquals(Material.BEDROCK, w.getBlockAt(x, g.roofY, z).getType(), "roof restored");
        assertEquals(Material.WHITE_TERRACOTTA, w.getBlockAt(x + 1, y + 4, z - 1).getType(), "ceiling restored");
        var shafts = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new java.io.File(plugin.getDataFolder(), "data/open-shafts.yml"));
        assertTrue(shafts.getKeys(false).isEmpty(), "no pending shafts after restore");
    }

    @Test
    void suffocatingInSandSwallowsYou() throws InterruptedException {
        plugin.getConfig().set("entry.suffocation.seconds", 0.2);
        plugin.saveConfig();
        plugin.reload();
        PlayerMock p = playerAt(0, 70, 0);
        Location target = plugin.sessions().entryTarget(p, false);
        floor(target.getWorld(), target.getBlockX(), target.getBlockY() - 1, target.getBlockZ(), 3);
        p.getEyeLocation().getBlock().setType(Material.SAND);
        boolean cancelled = true;
        for (int i = 0; i < 12 && p.getWorld() == overworld; i++) {
            org.bukkit.event.entity.EntityDamageEvent e = new org.bukkit.event.entity.EntityDamageEvent(p,
                    org.bukkit.event.entity.EntityDamageEvent.DamageCause.SUFFOCATION,
                    org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.IN_WALL).build(), 1);
            server.getPluginManager().callEvent(e);
            cancelled &= e.isCancelled();
            Thread.sleep(50);
            server.getScheduler().performTicks(2);
        }
        server.getScheduler().performTicks(5);
        assertTrue(cancelled, "no suffocation damage while it counts");
        assertEquals(plugin.worlds().world(Level.LEVEL_0), p.getWorld(), "swallowed into Level 0");

        // ordinary suffocation in stone is left alone
        PlayerMock q = playerAt(20, 70, 20);
        q.getEyeLocation().getBlock().setType(Material.STONE);
        org.bukkit.event.entity.EntityDamageEvent e = new org.bukkit.event.entity.EntityDamageEvent(q,
                org.bukkit.event.entity.EntityDamageEvent.DamageCause.SUFFOCATION,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.IN_WALL).build(), 1);
        server.getPluginManager().callEvent(e);
        assertFalse(e.isCancelled());
    }

    @Test
    void creaturesStayAroundCreativePlayersButNeverHurtThem() {
        World l0 = plugin.worlds().world(Level.LEVEL_0);
        floor(l0, 0, 64, 0, 12);
        PlayerMock p = playerAt(0, 70, 0);
        p.setGameMode(org.bukkit.GameMode.CREATIVE);
        p.teleport(new Location(l0, 0.5, 65, 0.5));
        Creature faceling = plugin.creatures().spawn(CreatureType.FACELING, new Location(l0, 9.5, 65, 9.5), null);
        Creature watcher = plugin.creatures().spawn(CreatureType.WATCHER, new Location(l0, -8.5, 65, 6.5), null);
        server.getScheduler().performTicks(30);
        assertFalse(faceling.dead(), "a creative player nearby keeps creatures alive");
        assertFalse(watcher.dead());
        double hp = p.getHealth();
        server.getScheduler().performTicks(200);
        assertEquals(hp, p.getHealth(), 1e-9, "creative players are never hurt");
        assertTrue(plugin.creatures().counts().size() <= 2);
    }
}
