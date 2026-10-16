package com.powderpass.backrooms.audio;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.gen.LevelGenerator;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.SoundStop;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The positional soundscape. All synthesis happened offline; this class only decides what to play, where and when.
 * <ul>
 *   <li><b>Emitters</b> ({@link Emitters}): physical loops (fixture hum, vents, water, machinery, radios). For each
 *   listener the nearest few within range are kept playing by re-triggering each sample just before it ends, at the
 *   source's position, so the client's own distance attenuation and panning make them locatable.</li>
 *   <li><b>Events</b>: one-shots chosen per level by weight, with per-event cooldowns, a recent-history penalty and
 *   deliberate silences between them (sometimes long ones).</li>
 *   <li><b>Acoustics</b> ({@link Acoustics}): the listener's room profile picks pre-reverberated variants, and a
 *   bounded ray test picks the pre-filtered "muffled" variant for sources behind walls.</li>
 * </ul>
 * Only players whose client loaded the pack get custom sounds; everyone else keeps the vanilla ambience.
 */
public final class AudioEngine implements Listener {
    private static final int TICK = 10;
    /** Overlap when re-triggering a loop, so its faded ends cross. */
    private static final int OVERLAP = 6;

    private final BackroomsPlugin plugin;
    private final PackManager pack;
    private final Emitters emitters = new Emitters();
    private final Map<UUID, State> states = new HashMap<>();
    private SoundIndex index = new SoundIndex();
    private BukkitTask task, bedTask;
    private long now;
    private String loadProblem = "";

    /** Per-listener state. Cleared on world change, teleport out, quit and disable. */
    static final class State {
        Level level;
        String profile = "dry";
        boolean underwater;
        long profileAt = -1000;
        final Map<String, Long> loops = new HashMap<>();
        /** Bed layers: event -> server tick at which to start its next pass. */
        final Map<String, Integer> beds = new HashMap<>();
        int bedsPausedUntil;
        final Map<String, Long> playing = new HashMap<>(); // event name -> tick it ends (for clean stops)
        final Map<String, Long> cooldown = new HashMap<>();
        final Deque<String> recent = new ArrayDeque<>();
        long nextEvent;
        long nextVoice;
        long nextMusic;
        long nextAnnounce;
        boolean debug;
        String lastEvent = "-";
    }

    public AudioEngine(BackroomsPlugin plugin, PackManager pack) {
        this.plugin = plugin;
        this.pack = pack;
    }

    private BackroomsConfig cfg() {
        return plugin.cfg();
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        stop();
        try (InputStream idx = plugin.getResource("pack-index.json"); InputStream zip = plugin.getResource(PackManager.EMBEDDED)) {
            index = SoundIndex.load(idx, zip);
            loadProblem = index.size() == 0 ? "pack-index.json missing or empty" : "";
        } catch (IOException | RuntimeException ex) {
            index = new SoundIndex();
            loadProblem = "could not read the sound index: " + ex.getMessage();
        }
        if (!loadProblem.isEmpty()) plugin.getLogger().warning("[Audio] " + loadProblem + "; custom audio is off.");
        if (!cfg().audioEnabled) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, TICK);
        // the bed needs tighter timing than everything else: a late re-trigger is an audible gap
        bedTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::beds, 22L, 2L);
    }

    public void stop() {
        if (task != null) task.cancel();
        if (bedTask != null) bedTask.cancel();
        task = null;
        bedTask = null;
        for (Player p : plugin.getServer().getOnlinePlayers()) silence(p);
        states.clear();
        emitters.clear();
    }

    /** Does this player get the custom soundscape (and therefore not the vanilla ambience)? */
    public boolean custom(Player p) {
        return cfg().audioEnabled && index.size() > 0 && pack.loaded(p);
    }

    public SoundIndex index() {
        return index;
    }

    // ------------------------------------------------------------------ main loop

    private void tick() {
        now += TICK;
        for (Map.Entry<Level, World> e : plugin.worlds().worlds().entrySet()) {
            for (Player p : e.getValue().getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR || !custom(p)) continue;
                State s = states.computeIfAbsent(p.getUniqueId(), k -> new State());
                if (s.level != e.getKey()) {
                    silence(p);
                    s.loops.clear();
                    s.level = e.getKey();
                    s.nextEvent = now + 100 + ThreadLocalRandom.current().nextInt(200);
                    s.nextMusic = now + 20L * (45 + ThreadLocalRandom.current().nextInt(45));
                    s.nextVoice = now + 20L * 45;
                    s.nextAnnounce = now + 20L * Math.max(10, cfg().announceSeconds) / 2;
                }
                try {
                    updateAcoustics(p, s);
                    if (cfg().audioAmbient) loops(p, s);
                    events(p, s);
                    announce(p, s);
                    music(p, s);
                    if (s.debug) debugBar(p, s);
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning("[Audio] " + p.getName() + ": " + ex);
                }
            }
        }
        // forget listeners who are gone
        states.keySet().removeIf(id -> {
            Player p = plugin.getServer().getPlayer(id);
            return p == null || plugin.worlds().levelOf(p.getWorld()) == null;
        });
    }

    private double intensity(Level level) {
        return cfg().audioLevelIntensity.getOrDefault(level, 1.0);
    }

    private void updateAcoustics(Player p, State s) {
        int every = cfg().audioQuality == 0 ? 80 : cfg().audioQuality == 2 ? 20 : 40;
        if (now - s.profileAt < every) return;
        s.profileAt = now;
        s.underwater = p.getEyeLocation().getBlock().getType() == Material.WATER;
        s.profile = Acoustics.profile(s.level, p);
    }

    // ------------------------------------------------------------------ loops

    private void loops(Player p, State s) {
        LevelGenerator gen = plugin.worlds().generator(s.level);
        Location pl = p.getLocation();
        World w = p.getWorld();
        int pcx = pl.getBlockX() >> 4, pcz = pl.getBlockZ() >> 4;
        List<Emitters.Emitter> near = new ArrayList<>();
        int lamps = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!w.isChunkLoaded(pcx + dx, pcz + dz)) continue;
                for (Emitters.Emitter em : emitters.chunk(w.getName(), s.level, gen, pcx + dx, pcz + dz)) {
                    if (em.kind() == Emitters.Kind.MUSIC) continue; // music plays on the listener (see events), never at a place
                    double d2 = dist2(pl, em);
                    if (d2 > em.radius() * em.radius() * cfg().audioDistanceScale * cfg().audioDistanceScale) continue;
                    if (em.kind() == Emitters.Kind.HUM && d2 < 100) lamps++;
                    near.add(em);
                }
            }
        }
        near.sort(Comparator.comparingDouble(em -> dist2(pl, em) / (em.kind() == Emitters.Kind.MUSIC ? 9 : 1)));
        int max = cfg().audioMaxSources;
        List<Emitters.Emitter> chosen = new ArrayList<>();
        boolean music = false;
        for (Emitters.Emitter em : near) {
            if (chosen.size() >= max) break;
            if (em.kind() == Emitters.Kind.MUSIC) {
                if (music) continue;
                music = true;
            }
            chosen.add(em);
        }
        // underwater: everything else is drowned out by the water bed
        if (s.underwater) {
            chosen.clear();
            loop(p, s, "underwater", pl, "water.underwater", 0.7f, false, null);
        }
        for (Emitters.Emitter em : chosen) {
            String id = em.sound();
            if (id.equals("hum.fluorescent") && lamps >= 4 && em == firstOfKind(chosen, Emitters.Kind.HUM)) id = "hum.fluorescent_dense";
            Location at = new Location(p.getWorld(), em.x(), em.y(), em.z());
            boolean muffled = (em.kind() == Emitters.Kind.MUSIC || em.kind() == Emitters.Kind.MACHINE)
                    && cfg().audioQuality > 0 && Acoustics.obstruction(p.getEyeLocation(), at, 32) >= 1;
            String profile = em.kind() == Emitters.Kind.MUSIC ? s.profile : null;
            loop(p, s, em.key(), at, id, (float) (em.volume() * intensity(s.level)), muffled, profile);
        }
        // forget loops that are no longer chosen (their current sample plays out; at most ~12 s)
        s.loops.keySet().removeIf(k -> !k.equals("underwater") && chosen.stream().noneMatch(em -> em.key().equals(k)));
    }

    private static Emitters.Emitter firstOfKind(List<Emitters.Emitter> l, Emitters.Kind k) {
        for (Emitters.Emitter e : l) if (e.kind() == k) return e;
        return null;
    }

    /** Keep one loop going for a listener: (re)trigger when the previous sample is about to end. */
    private void loop(Player p, State s, String key, Location at, String id, float volume, boolean muffled, String profile) {
        Long next = s.loops.get(key);
        if (next != null && next > now) return;
        SoundIndex.Def def = index.def(id);
        if (def == null) return;
        String event = index.resolve(id, profile, muffled);
        if (event == null) return;
        boolean isMusic = def.category().equals("music");
        play(p, at, event, isMusic ? SoundCategory.RECORDS : SoundCategory.AMBIENT, muffled ? volume * 0.8f : volume, 1f, def.durationTicks());
        // a radio plays its track again; other music sources rest between plays; loops cross-fade
        long gap = isMusic ? (key.contains("#radio") ? 20 : 20L * (40 + ThreadLocalRandom.current().nextInt(80))) : -OVERLAP;
        s.loops.put(key, now + def.durationTicks() + gap);
    }

    // ------------------------------------------------------------------ bed

    /**
     * The constant bed: each level's long seamless loops, played on the listener (the sound follows them, so it is
     * everywhere and never fades with distance), re-triggered 300 ms before the end so the faded ends cross.
     */
    private void beds() {
        if (!cfg().audioBed) return;
        int tick = plugin.getServer().getCurrentTick();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            State s = states.get(p.getUniqueId());
            if (s == null || s.level == null || p.getGameMode() == GameMode.SPECTATOR || !custom(p)) continue;
            if (tick < s.bedsPausedUntil) continue;
            for (Soundscape.Bed b : Soundscape.beds(s.level)) {
                String id = bedId(b.sound());
                SoundIndex.Def def = index.def(id);
                if (def == null) continue;
                Integer next = s.beds.get(id);
                if (next != null && next > tick) continue;
                float vol = (float) (b.volume() * cfg().audioBedVolume * intensity(s.level));
                if (vol <= 0.001f) continue;
                p.playSound(net.kyori.adventure.sound.Sound.sound(Key.key("backrooms", id), net.kyori.adventure.sound.Sound.Source.AMBIENT, vol, 1f),
                        net.kyori.adventure.sound.Sound.Emitter.self());
                s.playing.put(id, now + def.durationTicks());
                s.beds.put(id, tick + Math.max(20, def.durationTicks() - OVERLAP));
            }
        }
    }

    /** Level 0's bed is the supplied recording unless configured (or missing): then the synthesised one. */
    private String bedId(String id) {
        if (id.equals("bed.level0") && (!cfg().audioBedRecording || index.def(id) == null)) return "bed.level0_synth";
        return id;
    }

    /** Stop a listener's bed now and keep it silent for {@code ticks} (power cut: the buzz dies with the lights). */
    private void pauseBed(Player p, State s, int ticks) {
        for (String id : s.beds.keySet()) p.stopSound(SoundStop.named(Key.key("backrooms", id)));
        s.beds.clear();
        s.bedsPausedUntil = plugin.getServer().getCurrentTick() + ticks;
    }

    // ------------------------------------------------------------------ events

    private void events(Player p, State s) {
        if (now < s.nextEvent) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double density = Math.max(0.05, cfg().audioDensity);
        List<Soundscape.Ev> table = Soundscape.events(s.level);
        double total = 0;
        double[] w = new double[table.size()];
        for (int i = 0; i < table.size(); i++) {
            Soundscape.Ev ev = table.get(i);
            if (!allowed(s, ev)) continue;
            double wt = ev.weight() * (ev.rare() ? cfg().audioRare : 1.0) * (ev.kind() == Soundscape.Kind.MUSIC ? 3.0 : 1.0);
            if (s.recent.contains(ev.id())) wt *= 0.15;
            w[i] = wt;
            total += wt;
        }
        // silence is still part of the design: a gap after every event, now and then a longer one
        long base = (long) ((20 * 8 + r.nextInt(20 * 20)) / density);
        if (r.nextDouble() < 0.05) base += 20L * (60 + r.nextInt(60));
        s.nextEvent = now + base;
        if (total <= 0) return;
        double pick = r.nextDouble() * total;
        for (int i = 0; i < table.size(); i++) {
            if (w[i] <= 0) continue;
            pick -= w[i];
            if (pick <= 0) {
                fire(p, s, table.get(i), false);
                return;
            }
        }
    }

    /** About every {@code audio.announce-seconds}: a PA announcement, or a voice, picked by weight for the level. */
    private void announce(Player p, State s) {
        int every = cfg().announceSeconds;
        if (every <= 0 || !cfg().audioVoices || now < s.nextAnnounce) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        s.nextAnnounce = now + (long) (20L * every * (0.75 + r.nextDouble() * 0.5));
        List<Soundscape.Ev> table = Soundscape.announcements(s.level);
        double total = 0;
        for (Soundscape.Ev ev : table) if (index.def(ev.sound()) != null) total += ev.weight();
        if (total <= 0) return;
        double pick = r.nextDouble() * total;
        for (Soundscape.Ev ev : table) {
            if (index.def(ev.sound()) == null) continue;
            pick -= ev.weight();
            if (pick <= 0) {
                long keepVoice = s.nextVoice;
                fire(p, s, ev, true);
                s.nextVoice = keepVoice; // announcements do not hold back the rarer voice events
                return;
            }
        }
    }

    private boolean allowed(State s, Soundscape.Ev ev) {
        Long cd = s.cooldown.get(ev.id());
        if (cd != null && cd > now) return false;
        if (ev.kind() == Soundscape.Kind.VOICE && (!cfg().audioVoices || now < s.nextVoice)) return false;
        if (ev.kind() == Soundscape.Kind.MUSIC) return false; // music has its own schedule (see music())
        if (ev.underwaterOnly() && !s.underwater) return false;
        SoundIndex.Def def = index.def(ev.sound());
        return def != null;
    }

    /**
     * Play one event. PA announcements ({@code announcement}), voices and music are attached to the listener: they
     * follow the player and never fade with distance. Everything else comes from a place around them.
     */
    private void fire(Player p, State s, Soundscape.Ev ev, boolean announcement) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        s.cooldown.put(ev.id(), now + 20L * ev.cooldownSeconds());
        s.recent.addLast(ev.id());
        while (s.recent.size() > 6) s.recent.removeFirst();
        s.lastEvent = ev.id();
        if (ev.kind() == Soundscape.Kind.VOICE) s.nextVoice = now + 20L * (60 + r.nextInt(120));
        if (ev.kind() == Soundscape.Kind.MUSIC) s.nextMusic = now + (long) (20L * (120 + r.nextInt(240)) / Math.max(0.05, cfg().audioMusicFrequency));
        Location at = place(p, ev, r);
        float vol = (float) (ev.volume() * intensity(s.level));
        switch (ev.kind()) {
            case STEPS -> steps(p, s, ev, at, vol);
            case MUSIC -> {
                // non-positional, distant: played at the listener, stereo where the pack has an ambient mix
                SoundIndex.Def def = index.def(ev.sound());
                String event = index.resolve(ev.sound() + ".ambient", null, false);
                if (event == null) event = index.resolve(ev.sound(), null, false);
                if (event != null) playOnListener(p, event, net.kyori.adventure.sound.Sound.Source.MUSIC, vol, def.durationTicks());
            }
            default -> {
                if (announcement && ev.sound().equals("voice.announce")) {
                    pa(p, s, ev);
                    return;
                }
                if (announcement || ev.kind() == Soundscape.Kind.VOICE) {
                    String event = index.resolve(ev.sound(), s.profile, s.underwater);
                    if (event == null) return;
                    if (ev.placement() == Soundscape.Place.LISTENER) {
                        playOnListener(p, event, net.kyori.adventure.sound.Sound.Source.VOICE, vol, index.def(ev.sound()).durationTicks());
                    } else {
                        // a voice from somewhere: positional, and loud enough to be heard from where it is
                        play(p, at, event, SoundCategory.VOICE, Audibility.volumeFor(Math.min(1, vol * 1.4), at.distance(p.getEyeLocation())), 1f,
                                index.def(ev.sound()).durationTicks());
                    }
                    return;
                }
                boolean muffled = ev.placement() != Soundscape.Place.LISTENER && cfg().audioQuality > 0
                        && Acoustics.obstruction(p.getEyeLocation(), at.clone().add(0, 1, 0), 32) >= 1;
                String event = index.resolve(ev.sound(), s.profile, muffled || s.underwater);
                if (event == null) return;
                SoundIndex.Def def = index.def(ev.sound());
                SoundCategory cat = ev.kind() == Soundscape.Kind.VOICE ? SoundCategory.VOICE : SoundCategory.AMBIENT;
                float pitch = 0.94f + r.nextFloat() * 0.12f;
                // the volume that makes it heard at the wanted level from where it is (vanilla falls to 0 at 16 blocks)
                float sent = Audibility.volumeFor(vol, at.distance(p.getEyeLocation()));
                play(p, at, event, cat, sent, pitch, def.durationTicks());
                // one early reflection for hard rooms, never for voices or quiet things
                if (ev.reflect() && cfg().audioQuality > 0 && (s.profile.equals("concrete") || s.profile.equals("pool") || s.profile.equals("corridor"))) {
                    long delay = s.profile.equals("pool") ? 9 : s.profile.equals("concrete") ? 6 : 4;
                    Location mirror = p.getLocation().add(p.getLocation().toVector().subtract(at.toVector()).multiply(0.6));
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline() && p.getWorld() == at.getWorld())
                            play(p, mirror, event, cat, Audibility.volumeFor(vol * 0.3f, mirror.distance(p.getEyeLocation())), pitch * 0.98f, def.durationTicks());
                    }, delay);
                }
            }
        }
    }

    /**
     * A PA announcement: clearly audible on the listener (it is the building talking, everywhere at once), with
     * two quieter, later copies from ceiling speakers further along, which is what makes it sound like a PA in a
     * big room rather than a voice in your head. Harder rooms echo longer.
     */
    private void pa(Player p, State s, Soundscape.Ev ev) {
        SoundIndex.Def def = index.def(ev.sound());
        if (def == null) return;
        String event = index.resolve(ev.sound(), s.profile, s.underwater);
        if (event == null) return;
        float vol = (float) (cfg().audioAnnounceVolume * Math.min(1.0, intensity(s.level)));
        playOnListener(p, event, net.kyori.adventure.sound.Sound.Source.VOICE, vol, def.durationTicks());
        if (s.underwater || cfg().audioQuality == 0) return;
        boolean hard = s.profile.equals("pool") || s.profile.equals("concrete") || s.profile.equals("corridor");
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location base = p.getLocation();
        double yaw = r.nextDouble(Math.PI * 2);
        int[] delays = hard ? new int[]{4, 9} : new int[]{3, 6};
        float[] gains = hard ? new float[]{0.45f, 0.25f} : new float[]{0.35f, 0.15f};
        for (int i = 0; i < 2; i++) {
            double a = yaw + i * 2.4, d = 9 + i * 6;
            Location spk = base.clone().add(Math.cos(a) * d, 3.5, Math.sin(a) * d);
            float g = gains[i] * vol;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && p.getWorld() == spk.getWorld())
                    play(p, spk, event, SoundCategory.VOICE, Audibility.volumeFor(g, spk.distance(p.getEyeLocation())), 0.995f, def.durationTicks());
            }, delays[i]);
        }
    }

    /**
     * Music on its own schedule (it used to compete with every other event and was almost never picked): the first
     * track 45-90 s after arriving, then one about every {@code audio.music-interval-seconds}, never two at once.
     * It plays on the listener in the Jukebox category by default ({@code audio.music-category}), because many
     * players turn the Music slider down for the vanilla soundtrack.
     */
    private void music(Player p, State s) {
        if (!cfg().audioMusic || now < s.nextMusic) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Soundscape.Ev> tracks = new ArrayList<>();
        for (Soundscape.Ev ev : Soundscape.events(s.level)) {
            if (ev.kind() == Soundscape.Kind.MUSIC && index.def(ev.sound()) != null && !s.recent.contains(ev.id())) tracks.add(ev);
        }
        if (tracks.isEmpty()) for (Soundscape.Ev ev : Soundscape.events(s.level)) if (ev.kind() == Soundscape.Kind.MUSIC && index.def(ev.sound()) != null) tracks.add(ev);
        double gap = cfg().audioMusicInterval * (0.6 + r.nextDouble() * 0.8) / Math.max(0.05, cfg().audioMusicFrequency);
        if (tracks.isEmpty() || s.underwater) {
            s.nextMusic = now + 20L * 30;
            return;
        }
        Soundscape.Ev ev = tracks.get(r.nextInt(tracks.size()));
        SoundIndex.Def def = index.def(ev.sound());
        String event = index.resolve(ev.sound() + ".ambient", null, false);
        if (event == null) event = index.resolve(ev.sound(), null, false);
        if (event == null) return;
        float vol = (float) Math.min(1.0, Math.max(0.6, ev.volume() * 2.0) * intensity(s.level));
        playOnListener(p, event, cfg().audioMusicRecords ? net.kyori.adventure.sound.Sound.Source.RECORD : net.kyori.adventure.sound.Sound.Source.MUSIC,
                vol, def.durationTicks());
        s.recent.addLast(ev.id());
        while (s.recent.size() > 6) s.recent.removeFirst();
        s.lastEvent = ev.id();
        s.nextMusic = now + def.durationTicks() + (long) (20 * gap);
    }

    /**
     * A sound from a place in the world, for everyone near it: the pack's version (with the listener's room profile
     * and a muffled take behind walls) for players who loaded the pack, a vanilla stand-in for everyone else. It
     * falls off naturally to silence at {@code range} blocks, and is never cut short by the vanilla 16-block limit.
     * Creatures, lifts, cars and cutscenes use this so their sounds are actually heard.
     */
    public void broadcast(Location at, String id, org.bukkit.Sound fallback, double gain, float pitch, double range, SoundCategory cat) {
        if (at.getWorld() == null) return;
        boolean has = cfg().audioEnabled && id != null && index.has(id);
        SoundIndex.Def def = id == null ? null : index.def(id);
        for (Player p : at.getWorld().getPlayers()) {
            double d = p.getEyeLocation().distance(at);
            if (d > range) continue;
            double heard = gain * (1 - d / range);
            if (heard < 0.01) continue;
            float vol = Audibility.volumeFor(heard, d);
            if (has && custom(p)) {
                String event = id;
                if (def != null) {
                    State s = states.get(p.getUniqueId());
                    boolean muffled = def.muffled() && cfg().audioQuality > 0 && d > 3 && Acoustics.obstruction(p.getEyeLocation(), at, 32) >= 1;
                    String r = index.resolve(id, s == null ? null : s.profile, muffled || (s != null && s.underwater));
                    if (r != null) event = r;
                }
                // pack sounds are authored at their pitch; only small variations are passed on
                p.playSound(at, "backrooms:" + event, cat, vol, pitch >= 0.8f && pitch <= 1.25f ? pitch : 1f);
            } else if (fallback != null) {
                p.playSound(at, fallback, cat, vol, pitch);
            }
        }
    }

    /** Where an event comes from, relative to the listener. */
    private Location place(Player p, Soundscape.Ev ev, ThreadLocalRandom r) {
        Location pl = p.getLocation();
        double scale = cfg().audioDistanceScale;
        double min, max;
        switch (ev.placement()) {
            case LISTENER -> {
                return pl.clone();
            }
            case BEHIND -> {
                Vector back = pl.getDirection().setY(0).normalize().multiply(-1);
                double d = (3 + r.nextDouble() * 4) * scale;
                Vector side = new Vector(-back.getZ(), 0, back.getX()).multiply(r.nextDouble(-2, 2));
                return pl.clone().add(back.multiply(d)).add(side).add(0, 1.2, 0);
            }
            case NEAR -> {
                min = 6;
                max = 12;
            }
            case MID -> {
                min = 10;
                max = 20;
            }
            default -> {
                min = 18;
                max = 32;
            }
        }
        double a = r.nextDouble() * Math.PI * 2, d = (min + r.nextDouble() * (max - min)) * scale;
        return pl.clone().add(Math.cos(a) * d, 1 + r.nextDouble() * 1.5, Math.sin(a) * d);
    }

    /** Footsteps that keep pace somewhere behind you, then stop. */
    private void steps(Player p, State s, Soundscape.Ev ev, Location start, float vol) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String surface = s.underwater ? "step.water" : ev.sound();
        SoundIndex.Def def = index.def(surface);
        if (def == null) return;
        int n = 4 + r.nextInt(6);
        int interval = 8 + r.nextInt(4);
        Vector dir = p.getLocation().toVector().subtract(start.toVector()).setY(0);
        if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
        dir.normalize().multiply(0.6);
        for (int i = 0; i < n; i++) {
            Location at = start.clone().add(dir.clone().multiply(i)).add(0, -1, 0);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline() || p.getWorld() != at.getWorld()) return;
                boolean muffled = cfg().audioQuality > 0 && Acoustics.obstruction(p.getEyeLocation(), at.clone().add(0, 1, 0), 24) >= 1;
                String event = index.resolve(surface, s.profile, muffled);
                if (event != null) play(p, at, event, SoundCategory.AMBIENT, Audibility.volumeFor(vol, at.distance(p.getEyeLocation())), 0.95f + r.nextFloat() * 0.1f, def.durationTicks());
            }, (long) i * interval);
        }
    }

    // ------------------------------------------------------------------ hooks from other systems

    /** A fixture flickers: crackle at the lamp for listeners with the pack. */
    public void onFlicker(Location c) {
        for (Player p : listeners(c, 24)) {
            String event = index.resolve("light.flicker", null, false);
            if (event != null) play(p, c, event, SoundCategory.AMBIENT, 0.6f, 1f, 30);
            if (ThreadLocalRandom.current().nextDouble() < 0.25) {
                String pop = index.resolve("light.pop", null, false);
                if (pop != null) play(p, c, pop, SoundCategory.AMBIENT, 0.5f, 1f, 10);
            }
        }
    }

    /** The power dies around {@code c}, and comes back after {@code ticks}. */
    public void onBlackout(Location c, int ticks) {
        List<Player> ps = listeners(c, 48);
        for (Player p : ps) {
            String down = index.resolve("power.down", null, false);
            if (down != null) play(p, c, down, SoundCategory.AMBIENT, 0.9f, 1f, 50);
            State s = states.get(p.getUniqueId());
            if (s != null) {
                s.loops.replaceAll((k, v) -> now + ticks); // the hum stops with the lights
                if (s.level == Level.LEVEL_0 || s.level == Level.LEVEL_4) pauseBed(p, s, ticks);
            }
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Player p : ps) {
                if (!p.isOnline()) continue;
                String up = index.resolve("power.up", null, false);
                if (up != null) play(p, c, up, SoundCategory.AMBIENT, 0.8f, 1f, 40);
            }
        }, ticks);
    }

    private List<Player> listeners(Location c, double radius) {
        List<Player> out = new ArrayList<>();
        if (c.getWorld() == null) return out;
        for (Player p : c.getWorld().getPlayers()) {
            if (custom(p) && p.getLocation().distanceSquared(c) <= radius * radius) out.add(p);
        }
        return out;
    }

    // ------------------------------------------------------------------ playback primitives

    private void play(Player p, Location at, String event, SoundCategory cat, float volume, float pitch, int durationTicks) {
        p.playSound(at, "backrooms:" + event, cat, Math.max(0.01f, volume), pitch);
        State s = states.get(p.getUniqueId());
        if (s != null) s.playing.put(event, now + index.ticks(event, durationTicks));
    }

    /** A sound attached to the listener: it moves with them instead of staying where it started. */
    private void playOnListener(Player p, String event, net.kyori.adventure.sound.Sound.Source src, float volume, int durationTicks) {
        p.playSound(net.kyori.adventure.sound.Sound.sound(Key.key("backrooms", event), src, Math.max(0.01f, volume), 1f),
                net.kyori.adventure.sound.Sound.Emitter.self());
        State s = states.get(p.getUniqueId());
        if (s != null) s.playing.put(event, now + index.ticks(event, durationTicks));
    }

    /** Stop every custom sound this listener may still be hearing. */
    public void silence(Player p) {
        State s = states.get(p.getUniqueId());
        if (s == null) return;
        for (Iterator<Map.Entry<String, Long>> it = s.playing.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> e = it.next();
            if (e.getValue() > now) p.stopSound(SoundStop.named(Key.key("backrooms", e.getKey())));
            it.remove();
        }
        s.loops.clear();
        s.beds.clear();
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        silence(e.getPlayer());
        State s = states.get(e.getPlayer().getUniqueId());
        if (s != null) s.level = null;
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getFrom().getWorld() == e.getTo().getWorld() && e.getFrom().distanceSquared(e.getTo()) < 64 * 64) return;
        silence(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        states.remove(e.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ admin

    /** Play one sound id to a player now (test command). Returns a description of what was sent. */
    /** How {@code /backrooms audio play} places a sound. */
    public enum Mode {
        /** attached to the listener: follows them, never fades (PA, music, voices) */
        DIRECT,
        /** where the listener stands now: fades as they walk away */
        AROUND,
        /** a few blocks away in a random direction */
        NEAR,
        /** far off in a random direction */
        FAR;

        public static Mode parse(String s) {
            try {
                return s == null ? null : valueOf(s.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public String test(Player p, String id) {
        return play(p, id, 1f, 1f);
    }

    /** Play one sound id to a player, placed by {@code mode}. Returns what was played or why not. */
    public String play(Player p, String id, Mode mode, float volume, float pitch) {
        SoundIndex.Def def = index.def(id);
        String event = def != null ? index.resolve(id, states.containsKey(p.getUniqueId()) ? states.get(p.getUniqueId()).profile : null, false)
                : index.has(id) ? id : null;
        if (event == null) return "unknown sound id '" + id + "'";
        int ticks = def == null ? 40 : def.durationTicks();
        boolean music = def != null && def.category().equals("music");
        if (mode == Mode.DIRECT) {
            p.playSound(net.kyori.adventure.sound.Sound.sound(Key.key("backrooms", event),
                    music ? net.kyori.adventure.sound.Sound.Source.MUSIC : net.kyori.adventure.sound.Sound.Source.VOICE, Math.max(0.01f, volume), pitch),
                    net.kyori.adventure.sound.Sound.Emitter.self());
            State s = states.get(p.getUniqueId());
            if (s != null) s.playing.put(event, now + index.ticks(event, ticks));
        } else {
            Location at = p.getLocation().add(0, 1, 0);
            if (mode != Mode.AROUND) {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                double a = r.nextDouble(Math.PI * 2), d = mode == Mode.NEAR ? 5 + r.nextDouble(6) : 18 + r.nextDouble(12);
                at.add(Math.sin(a) * d, 0, Math.cos(a) * d);
            }
            play(p, at, event, music ? SoundCategory.RECORDS : SoundCategory.AMBIENT, volume, pitch, ticks);
        }
        return "backrooms:" + event + " (" + mode.name().toLowerCase(java.util.Locale.ROOT) + ")"
                + (pack.loaded(p) ? "" : " (your client has not loaded the pack, so you will hear nothing)");
    }

    /** Play one sound id to a player (positional ones a few blocks in front of them). Returns what was played or why not. */
    public String play(Player p, String id, float volume, float pitch) {
        SoundIndex.Def def = index.def(id);
        String event = def != null ? index.resolve(id, states.containsKey(p.getUniqueId()) ? states.get(p.getUniqueId()).profile : null, false)
                : index.has(id) ? id : null;
        if (event == null) return "unknown sound id '" + id + "'";
        Location at = def != null && def.positional() ? p.getLocation().add(p.getLocation().getDirection().multiply(4)).add(0, 1, 0) : p.getLocation();
        play(p, at, event, def != null && def.category().equals("music") ? SoundCategory.RECORDS : SoundCategory.AMBIENT, volume, pitch,
                def == null ? 40 : def.durationTicks());
        return "backrooms:" + event + (pack.loaded(p) ? "" : " (your client has not loaded the pack, so you will hear nothing)");
    }

    /** Stop everything this plugin is playing to a player (they keep hearing it again as the soundscape moves on). */
    public void stop(Player p) {
        silence(p);
        p.stopAllSounds();
    }

    public boolean toggleDebug(Player p) {
        State s = states.computeIfAbsent(p.getUniqueId(), k -> new State());
        s.debug = !s.debug;
        return s.debug;
    }

    private void debugBar(Player p, State s) {
        p.sendActionBar(BackroomsConfig.mm("<gray>audio <white>" + s.level.key() + "</white> profile <white>" + s.profile
                + (s.underwater ? " (underwater)" : "") + "</white> loops <white>" + s.loops.size() + "</white> next event <white>"
                + Math.max(0, (s.nextEvent - now) / 20) + "s</white> last <white>" + s.lastEvent));
    }

    public String describe() {
        int listening = 0, loops = 0;
        for (State s : states.values()) {
            listening++;
            loops += s.loops.size();
        }
        return (cfg().audioEnabled ? "on" : "off") + (loadProblem.isEmpty() ? "" : " (" + loadProblem + ")") + ", " + index.size() + " sounds / "
                + index.events().size() + " events, " + listening + " listeners, " + loops + " active loops";
    }

    private static double dist2(Location l, Emitters.Emitter e) {
        double dx = l.getX() - e.x(), dy = l.getY() - e.y(), dz = l.getZ() - e.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
