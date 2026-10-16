package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.BackroomsPlugin;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

/**
 * Keeps a worn player (who really is in spectator mode, see {@link Possession}) looking like an ordinary player in
 * everybody else's tab list. The client draws spectators faded, and the only thing it goes by is the game mode in
 * the player-info packets, so the server tells the other players that the victim is in survival mode. Their own
 * client is never sent this: it also derives its own spectator state from that entry and would stop spectating.
 *
 * <p>Paper has no API for this, so it builds the packet through reflection on the server's Mojang-named classes
 * (the way Paper ships them since 1.20.5): the real update packet is created, its entry copied with the game mode
 * swapped, and sent. Anything unexpected (a different server, a changed class) is caught: it logs once and the tab
 * list simply stays faded, nothing else is affected.</p>
 */
final class TabList {
    private static final String PKG = "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket";
    private static volatile Handles handles;
    private static volatile boolean broken;

    private TabList() {
    }

    private record Handles(Method getHandle, Constructor<?> real, Method entries, Constructor<?> entryCtor, RecordComponent[] comps,
                           int modeIndex, Constructor<?> fake, Object action, Object survival, Class<?> actionClass,
                           Field connection, Method send) {
    }

    /** Tell {@code viewers} (except the subject) that {@code subject} is in survival mode. Cheap; safe to repeat. */
    static void showAsSurvival(BackroomsPlugin plugin, Player subject, Collection<? extends Player> viewers) {
        if (broken) return;
        try {
            Handles h = handles();
            Object handle = h.getHandle.invoke(subject);
            EnumSet<?> actions = actions(h);
            Object real = h.real.newInstance(actions, List.of(handle));
            Object entry = ((List<?>) h.entries.invoke(real)).get(0);
            Object[] values = new Object[h.comps.length];
            for (int i = 0; i < values.length; i++) values[i] = h.comps[i].getAccessor().invoke(entry);
            values[h.modeIndex] = h.survival;
            Object packet = h.fake.newInstance(actions, List.of(h.entryCtor.newInstance(values)));
            for (Player viewer : viewers) {
                if (viewer.getUniqueId().equals(subject.getUniqueId())) continue;
                Object conn = h.connection.get(h.getHandle.invoke(viewer));
                if (conn != null) h.send.invoke(conn, packet);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            broken = true;
            plugin.getLogger().warning("[Skinwalker] Could not keep worn players looking normal in the tab list (they will show faded): " + ex);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static EnumSet<?> actions(Handles h) {
        EnumSet set = EnumSet.noneOf((Class) h.actionClass);
        set.add(h.action);
        return set;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Handles handles() throws ReflectiveOperationException {
        Handles h = handles;
        if (h != null) return h;
        Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
        Method getHandle = craftPlayer.getMethod("getHandle");
        Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
        Class<?> packet = Class.forName(PKG);
        Class<?> action = Class.forName(PKG + "$Action");
        Class<?> entry = Class.forName(PKG + "$Entry");
        Class<?> gameType = Class.forName("net.minecraft.world.level.GameType");
        Class<?> basePacket = Class.forName("net.minecraft.network.protocol.Packet");
        RecordComponent[] comps = entry.getRecordComponents();
        if (comps == null) throw new NoSuchMethodException(entry + " is not a record");
        Class<?>[] types = new Class<?>[comps.length];
        int mode = -1;
        for (int i = 0; i < comps.length; i++) {
            types[i] = comps[i].getType();
            if (comps[i].getName().equals("gameMode")) mode = i;
        }
        if (mode < 0) throw new NoSuchMethodException("no gameMode in " + entry);
        Constructor<?> entryCtor = entry.getDeclaredConstructor(types);
        entryCtor.setAccessible(true);
        Constructor<?> fake = null;
        for (Constructor<?> c : packet.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 2 && p[0] == EnumSet.class && p[1] == List.class) fake = c;
        }
        if (fake == null) throw new NoSuchMethodException("no (EnumSet, List) constructor in " + packet);
        fake.setAccessible(true);
        Field connection = serverPlayer.getField("connection");
        Method send = connection.getType().getMethod("send", basePacket);
        h = new Handles(getHandle, packet.getConstructor(EnumSet.class, Collection.class), packet.getMethod("entries"), entryCtor,
                comps, mode, fake, Enum.valueOf((Class) action, "UPDATE_GAME_MODE"), Enum.valueOf((Class) gameType, "SURVIVAL"),
                action, connection, send);
        handles = h;
        return h;
    }
}
