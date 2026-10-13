package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.BackroomsPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Optional Simple Voice Chat integration, done entirely through reflection against the classes of the installed
 * voicechat plugin, so nothing from Simple Voice Chat is compiled in or redistributed.
 * <ul>
 *   <li>Players inside the Backrooms have their recent speech kept in memory only (a short rolling buffer of Opus
 *   frames, split into utterances). Nothing is written to disk, and the buffer is dropped when they leave.</li>
 *   <li>A muted player's microphone packets are cancelled, so nobody hears them.</li>
 *   <li>An utterance can be played back from a moving point in the world through a locational audio channel.</li>
 * </ul>
 * If the voicechat plugin is absent or its API differs, every method is a harmless no-op.
 */
public final class VoiceBridge {
    private static final long FRAME_MS = 20;
    private static final long GAP_MS = 250;
    private static final int MAX_UTTERANCES = 12;

    private final BackroomsPlugin plugin;
    private final Set<UUID> recording = ConcurrentHashMap.newKeySet();
    private final Set<UUID> muted = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Recorder> recorders = new ConcurrentHashMap<>();
    private volatile Object api;
    // methods resolved on the public API interfaces (implementation classes may not be accessible)
    private Method mSender, mPacket, mCancel, mPlayer, mUuid, mOpus, mFromLevel, mCreatePos, mCreateChannel, mSetDistance,
            mUpdate, mSend, mFlush;
    private ScheduledExecutorService sender;
    private String state = "voicechat not installed";

    /** Utterances of one player: lists of Opus frames, newest last. */
    static final class Recorder {
        final Deque<List<byte[]>> utterances = new ArrayDeque<>();
        List<byte[]> current;
        long lastFrame;

        synchronized void frame(byte[] opus, long now) {
            if (current == null || now - lastFrame > GAP_MS) {
                flush();
                current = new ArrayList<>();
            }
            if (current.size() < 8000 / FRAME_MS) current.add(opus);
            lastFrame = now;
        }

        synchronized void flush() {
            if (current != null && current.size() >= 600 / FRAME_MS) {
                utterances.addLast(current);
                while (utterances.size() > MAX_UTTERANCES) utterances.removeFirst();
            }
            current = null;
        }

        synchronized List<byte[]> pick() {
            if (current != null && System.currentTimeMillis() - lastFrame > GAP_MS) flush();
            if (utterances.isEmpty()) return null;
            List<List<byte[]>> all = new ArrayList<>(utterances);
            return all.get(ThreadLocalRandom.current().nextInt(all.size()));
        }
    }

    public VoiceBridge(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Plugin vc = plugin.getServer().getPluginManager().getPlugin("voicechat");
        if (vc == null || !vc.isEnabled()) {
            state = "voicechat not installed";
            return;
        }
        try {
            ClassLoader cl = vc.getClass().getClassLoader();
            Class<?> serviceClass = Class.forName("de.maxhenkel.voicechat.api.BukkitVoicechatService", true, cl);
            Class<?> pluginIface = Class.forName("de.maxhenkel.voicechat.api.VoicechatPlugin", true, cl);
            Class<?> registration = Class.forName("de.maxhenkel.voicechat.api.events.EventRegistration", true, cl);
            Class<?> micEvent = Class.forName("de.maxhenkel.voicechat.api.events.MicrophonePacketEvent", true, cl);
            Object service = plugin.getServer().getServicesManager().load(serviceClass);
            if (service == null) {
                state = "voicechat service not available";
                return;
            }
            Method registerEvent = registration.getMethod("registerEvent", Class.class, Consumer.class, int.class);
            Class<?> packetEvent = Class.forName("de.maxhenkel.voicechat.api.events.PacketEvent", true, cl);
            Class<?> eventIface = Class.forName("de.maxhenkel.voicechat.api.events.Event", true, cl);
            Class<?> connection = Class.forName("de.maxhenkel.voicechat.api.VoicechatConnection", true, cl);
            Class<?> entity = Class.forName("de.maxhenkel.voicechat.api.Entity", true, cl);
            Class<?> micPacket = Class.forName("de.maxhenkel.voicechat.api.packets.MicrophonePacket", true, cl);
            Class<?> vcApi = Class.forName("de.maxhenkel.voicechat.api.VoicechatApi", true, cl);
            Class<?> serverApi = Class.forName("de.maxhenkel.voicechat.api.VoicechatServerApi", true, cl);
            Class<?> serverLevel = Class.forName("de.maxhenkel.voicechat.api.ServerLevel", true, cl);
            Class<?> position = Class.forName("de.maxhenkel.voicechat.api.Position", true, cl);
            Class<?> locChannel = Class.forName("de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel", true, cl);
            Class<?> audioChannel = Class.forName("de.maxhenkel.voicechat.api.audiochannel.AudioChannel", true, cl);
            mSender = packetEvent.getMethod("getSenderConnection");
            mPacket = packetEvent.getMethod("getPacket");
            mCancel = eventIface.getMethod("cancel");
            mPlayer = connection.getMethod("getPlayer");
            mUuid = entity.getMethod("getUuid");
            mOpus = micPacket.getMethod("getOpusEncodedData");
            mFromLevel = vcApi.getMethod("fromServerLevel", Object.class);
            mCreatePos = vcApi.getMethod("createPosition", double.class, double.class, double.class);
            mCreateChannel = serverApi.getMethod("createLocationalAudioChannel", UUID.class, serverLevel, position);
            mSetDistance = locChannel.getMethod("setDistance", float.class);
            mUpdate = locChannel.getMethod("updateLocation", position);
            mSend = audioChannel.getMethod("send", byte[].class);
            mFlush = audioChannel.getMethod("flush");
            Consumer<Object> onMic = this::onMicrophone;
            Object proxy = Proxy.newProxyInstance(cl, new Class<?>[]{pluginIface}, (self, m, args) -> switch (m.getName()) {
                case "getPluginId" -> "backrooms";
                case "initialize" -> {
                    api = args[0];
                    yield null;
                }
                case "registerEvents" -> {
                    registerEvent.invoke(args[0], micEvent, onMic, 100);
                    yield null;
                }
                case "hashCode" -> System.identityHashCode(self);
                case "equals" -> self == args[0];
                case "toString" -> "BackroomsVoicechatPlugin";
                default -> null;
            });
            serviceClass.getMethod("registerPlugin", pluginIface).invoke(service, proxy);
            sender = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Backrooms-voice");
                t.setDaemon(true);
                return t;
            });
            state = "connected to Simple Voice Chat";
            plugin.getLogger().info("[Skinwalker] Simple Voice Chat found: voice mimicry is available.");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            state = "voicechat API not compatible: " + ex;
            plugin.getLogger().warning("[Skinwalker] Simple Voice Chat is installed but its API could not be used: " + ex);
        }
    }

    public void stop() {
        if (sender != null) sender.shutdownNow();
        sender = null;
        recording.clear();
        muted.clear();
        recorders.clear();
    }

    public String state() {
        return state;
    }

    public boolean available() {
        return api != null && sender != null;
    }

    /** Called on the voicechat network thread for every microphone packet. */
    private void onMicrophone(Object event) {
        try {
            Object conn = mSender.invoke(event);
            if (conn == null) return;
            Object player = mPlayer.invoke(conn);
            UUID id = (UUID) mUuid.invoke(player);
            if (recording.contains(id)) {
                byte[] opus = (byte[]) mOpus.invoke(mPacket.invoke(event));
                if (opus != null && opus.length > 0) recorders.computeIfAbsent(id, k -> new Recorder()).frame(opus.clone(), System.currentTimeMillis());
            }
            if (muted.contains(id)) mCancel.invoke(event);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // never break voice chat for everyone else
        }
    }

    /** Keep a short in-memory buffer of this player's speech (only while they are inside the Backrooms). */
    public void record(UUID player, boolean on) {
        if (on) recording.add(player);
        else {
            recording.remove(player);
            recorders.remove(player);
        }
    }

    public void mute(UUID player, boolean on) {
        if (on) muted.add(player);
        else muted.remove(player);
    }

    public boolean hasSpeech(UUID player) {
        Recorder r = recorders.get(player);
        return r != null && r.pick() != null;
    }

    /**
     * Play one of the player's recent utterances from {@code where}, which the caller keeps updated as the source
     * moves. Returns false if there is nothing to play or voice chat is unavailable.
     */
    public boolean play(UUID player, World world, AtomicReference<double[]> where, float distance) {
        if (!available()) return false;
        Recorder r = recorders.get(player);
        List<byte[]> frames = r == null ? null : r.pick();
        if (frames == null) return false;
        try {
            Object a = api;
            double[] p = where.get();
            Object level = mFromLevel.invoke(a, world);
            Object channel = mCreateChannel.invoke(a, UUID.randomUUID(), level, mCreatePos.invoke(a, p[0], p[1], p[2]));
            if (channel == null) return false;
            mSetDistance.invoke(channel, distance);
            int[] i = {0};
            ScheduledExecutorService s = sender;
            Runnable[] task = new Runnable[1];
            task[0] = () -> {
                try {
                    if (i[0] >= frames.size()) {
                        mFlush.invoke(channel);
                        return;
                    }
                    if (i[0] % 10 == 0) {
                        double[] q = where.get();
                        mUpdate.invoke(channel, mCreatePos.invoke(a, q[0], q[1], q[2]));
                    }
                    mSend.invoke(channel, (Object) frames.get(i[0]++));
                    s.schedule(task[0], FRAME_MS, TimeUnit.MILLISECONDS);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // stop quietly
                }
            };
            s.execute(task[0]);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.debug("[Skinwalker] voice playback failed: " + ex);
            return false;
        }
    }

    /** Test hook: inject a fake utterance. */
    void inject(UUID player, List<byte[]> frames) {
        Recorder r = recorders.computeIfAbsent(player, k -> new Recorder());
        synchronized (r) {
            r.utterances.addLast(frames);
        }
    }

    /** Where a moving source is, readable off the main thread. */
    static AtomicReference<double[]> tracker(Location l) {
        return new AtomicReference<>(new double[]{l.getX(), l.getY() + 1.6, l.getZ()});
    }
}
