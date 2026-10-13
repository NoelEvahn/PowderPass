package com.powderpass.backrooms;

import com.powderpass.backrooms.audio.PackManager;
import com.powderpass.backrooms.audio.SoundIndex;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.LevelGenerator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/** Fog, resource pack delivery and the audio engine, against MockBukkit. */
class AtmosphereTest {
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

    private TestPlayer player(String name, Location at) {
        TestPlayer p = new TestPlayer(server, name);
        server.addPlayer(p);
        p.teleport(at);
        return p;
    }

    @Test
    void fogFollowsTheLevelAndIsRestoredOnLeaving() {
        World pool = plugin.worlds().world(Level.POOLROOMS);
        TestPlayer p = player("Fog", new Location(overworld, 0.5, 70, 0.5));
        p.teleport(new Location(pool, 0.5, 70, 0.5));
        server.getScheduler().performTicks(45);
        assertEquals(7, p.sendViewDistance, "Poolrooms resting fog");
        assertTrue(plugin.fog().bank(p, 30));
        assertEquals(3, p.sendViewDistance, "fog bank pulls the fog in");
        assertTrue(plugin.fog().inBank(p));
        p.teleport(new Location(plugin.worlds().world(Level.LEVEL_0), 0.5, 70, 0.5));
        server.getScheduler().performTicks(25);
        // Level 0 has no resting fog; the bank (per player) persists only while it lasts
        p.teleport(new Location(overworld, 0.5, 70, 0.5));
        server.getScheduler().performTicks(25);
        assertEquals(-1, p.sendViewDistance, "leaving the Backrooms restores the world's view distance");
    }

    @Test
    void embeddedPackIsExtractedValidatedAndHashed() throws Exception {
        PackManager pm = plugin.pack();
        assertTrue(pm.valid(), pm.describe());
        File f = pm.packFile();
        assertTrue(f.exists());
        byte[] embedded;
        try (InputStream in = plugin.getResource(PackManager.EMBEDDED)) {
            embedded = in.readAllBytes();
        }
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(embedded));
        assertEquals(sha, pm.sha1());
        assertArrayEquals(embedded, Files.readAllBytes(f.toPath()), "extracted file is the embedded pack");
        assertEquals("https://download.mc-packs.net/pack/" + sha + ".zip", pm.url());
        File mc = new File(plugin.getDataFolder(), "resourcepack/Backrooms-bedrock.mcpack");
        assertTrue(mc.exists(), "Bedrock pack assembled too");
        try (var z = new java.util.zip.ZipFile(mc); var j = new java.util.zip.ZipFile(f)) {
            assertNotNull(z.getEntry("manifest.json"));
            assertNotNull(z.getEntry("sounds/sound_definitions.json"));
            long javaOggs = j.stream().filter(e -> e.getName().endsWith(".ogg")).count();
            long bedOggs = z.stream().filter(e -> e.getName().startsWith("sounds/backrooms/") && e.getName().endsWith(".ogg")).count();
            assertEquals(javaOggs, bedOggs, "every sound is in the Bedrock pack");
            String defs = new String(z.getInputStream(z.getEntry("sounds/sound_definitions.json")).readAllBytes(), StandardCharsets.UTF_8);
            var m = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"(sounds/backrooms/[^\"]+)\"").matcher(defs);
            int refs = 0;
            while (m.find()) {
                refs++;
                assertNotNull(z.getEntry(m.group(1) + ".ogg"), "Bedrock definition without a file: " + m.group(1));
            }
            assertTrue(refs > 400);
        }

        // a version bump (or a damaged file) re-extracts it
        Files.writeString(new File(f.getParentFile(), "pack.version").toPath(), "0.0.1 old", StandardCharsets.UTF_8);
        Files.write(f.toPath(), new byte[]{1, 2, 3});
        pm.start();
        assertArrayEquals(embedded, Files.readAllBytes(f.toPath()));
        assertTrue(Files.readString(new File(f.getParentFile(), "pack.version").toPath()).endsWith(sha));
    }

    @Test
    void packIsOnlyOfferedWhenTheUrlAnswers() throws Exception {
        // an address that refuses connections: never offered
        plugin.getConfig().set("resource-pack.url", "http://127.0.0.1:1/{sha1}.zip");
        plugin.saveConfig();
        plugin.reload();
        awaitUrlCheck();
        assertEquals(PackManager.UrlState.UNREACHABLE, plugin.pack().urlState());
        TestPlayer p = player("Java", new Location(overworld, 0.5, 70, 0.5));
        plugin.pack().offer(p);
        assertEquals(0, p.packRequests, "not offered without a reachable URL");

        // the optional built-in host on a free local port: reachable, so offered
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        plugin.getConfig().set("resource-pack.host.enabled", true);
        plugin.getConfig().set("resource-pack.host.bind", "127.0.0.1");
        plugin.getConfig().set("resource-pack.host.port", port);
        plugin.getConfig().set("resource-pack.host.public-address", "127.0.0.1");
        plugin.saveConfig();
        plugin.reload();
        awaitUrlCheck();
        assertEquals(PackManager.UrlState.REACHABLE, plugin.pack().urlState(), plugin.pack().describe());
        assertTrue(p.packRequests >= 1, "offered to online players once reachable");
        // the host serves exactly the pack
        var client = java.net.http.HttpClient.newHttpClient();
        var resp = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(plugin.pack().url())).build(),
                java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, resp.statusCode());
        assertArrayEquals(Files.readAllBytes(plugin.pack().packFile().toPath()), resp.body());
        var other = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/plugins/")).build(),
                java.net.http.HttpResponse.BodyHandlers.discarding());
        assertEquals(404, other.statusCode(), "nothing but the pack is served");

        // status tracking: only a successful load enables custom audio
        assertFalse(plugin.pack().loaded(p));
        server.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(p, plugin.pack().packId(), PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        assertTrue(plugin.pack().loaded(p));
        server.getPluginManager().callEvent(new PlayerResourcePackStatusEvent(p, plugin.pack().packId(), PlayerResourcePackStatusEvent.Status.FAILED_RELOAD));
        assertFalse(plugin.pack().loaded(p));
    }

    private void awaitUrlCheck() throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            server.getScheduler().waitAsyncTasksFinished();
            server.getScheduler().performTicks(1);
            var st = plugin.pack().urlState();
            if (st != PackManager.UrlState.CHECKING && st != PackManager.UrlState.UNCHECKED) return;
            Thread.sleep(20);
        }
    }

    @Test
    void everySoundTheEngineCanPlayExistsInThePack() {
        SoundIndex idx = plugin.audio().index();
        assertTrue(idx.size() > 40, "sound index loaded: " + idx.size());
        for (String id : idx.ids()) {
            assertNotNull(idx.resolve(id, null, false), "pack-index id without a sounds.json event: " + id);
            SoundIndex.Def d = idx.def(id);
            for (String prof : d.profiles()) assertTrue(idx.has(id + "." + prof), "missing profile variant " + id + "." + prof);
        }
        for (String id : java.util.List.of("hum.fluorescent", "hum.fluorescent_dense", "water.lap", "water.circulation", "room.damp",
                "vent.hvac", "transformer.hum", "office.ac", "vent.small", "machine.distant", "water.underwater",
                "music.lobby_1", "music.lobby_2", "music.pool_1", "music.pool_2", "music.office_radio_1", "music.office_radio_2",
                "music.garage_1", "music.plains_1", "music.plains_2", "light.flicker", "light.pop", "power.down", "power.up",
                "knock.distant", "step.carpet", "step.concrete", "step.tile", "step.water", "voice.whisper", "voice.murmur",
                "voice.conversation", "voice.announce", "voice.phrase", "voice.cry", "water.drip", "water.splash", "water.bubbles",
                "metal.creak", "pipe.knock", "machine.start", "impact.distant", "movement.distant", "office.click", "office.printer",
                "office.phone", "speaker.broken", "grass.rustle", "plains.distant", "light.arc",
                "bed.level0_synth", "bed.poolrooms", "bed.garage", "bed.office", "bed.plains", "steam.burst", "metal.clank", "metal.groan")) {
            assertNotNull(idx.def(id), "engine uses '" + id + "' but the pack has no such sound");
        }
    }

    @Test
    void customAudioOnlyForPlayersWhoLoadedThePackAndIsPositional() {
        World l0 = plugin.worlds().world(Level.LEVEL_0);
        LevelGenerator g = plugin.worlds().generator(Level.LEVEL_0);
        DecorSpec lamp = null;
        for (int c = 0; c < 16 && lamp == null; c++) {
            for (DecorSpec d : g.decorations(c, 0)) {
                if (d.model.equals("panel_light")) {
                    lamp = d;
                    break;
                }
            }
        }
        assertNotNull(lamp, "a lit fixture near the origin");
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) l0.getChunkAt((((int) lamp.x) >> 4) + dx, (((int) lamp.z) >> 4) + dz);
        Location under = new Location(l0, lamp.x, g.floorY[0] + 1, lamp.z);
        TestPlayer p = player("Listener", under);
        server.getScheduler().performTicks(60);
        assertTrue(p.customSounds.isEmpty(), "no custom sounds without the pack: " + p.customSounds);

        plugin.pack().markLoaded(p, true);
        assertTrue(plugin.audio().custom(p));
        server.getScheduler().performTicks(60);
        assertTrue(p.customSounds.stream().anyMatch(s -> s.startsWith("backrooms:hum.fluorescent")), "fixture hum plays: " + p.customSounds);
        String hum = p.customSounds.stream().filter(s -> s.startsWith("backrooms:hum.fluorescent")).findFirst().orElseThrow();
        // the constant bed plays on the listener, not at a place
        assertTrue(p.customSounds.stream().anyMatch(s -> s.startsWith("backrooms:bed.level0") && s.endsWith("@self")), "Level 0 bed on the listener: " + p.customSounds);
        long beds = p.customSounds.stream().filter(s -> s.startsWith("backrooms:bed.")).count();
        assertEquals(1, beds, "one bed pass, not restarted every tick: " + p.customSounds);
        String[] xyz = hum.substring(hum.indexOf('@') + 1).split(",");
        assertTrue(Math.abs(Integer.parseInt(xyz[0]) - lamp.x) < 12 && Math.abs(Integer.parseInt(xyz[2]) - lamp.z) < 12, "hum comes from a fixture, not the listener");
        for (String s : p.customSounds) {
            String event = s.substring("backrooms:".length(), s.indexOf('@'));
            assertTrue(plugin.audio().index().has(event), "sent an unregistered sound id " + event);
        }
        // loops are re-triggered, not stacked: within one loop length a source plays once
        long humCount = p.customSounds.stream().filter(s -> s.equals(hum)).count();
        assertTrue(humCount <= 2, "loop restarted too often: " + humCount);

        // leaving the level stops what is playing
        p.customSounds.clear();
        p.teleport(new Location(overworld, 0.5, 70, 0.5));
        assertFalse(p.stopped.isEmpty(), "custom sounds stopped on leaving");
        server.getScheduler().performTicks(40);
        assertTrue(p.customSounds.isEmpty(), "nothing plays outside the Backrooms");
    }

    @Test
    void audioCommandsAreGatedAndReport() {
        TestPlayer p = player("Guest", new Location(overworld, 0.5, 70, 0.5));
        p.performCommand("backrooms audio status");
        assertTrue(p.nextMessage() != null);
        server.dispatchCommand(server.getConsoleSender(), "backrooms audio status");
        plugin.pack().markLoaded(p, true);
        server.dispatchCommand(server.getConsoleSender(), "backrooms audio test knock.distant " + p.getName());
        assertTrue(p.customSounds.stream().anyMatch(s -> s.startsWith("backrooms:knock.distant")), p.customSounds.toString());
    }
}
