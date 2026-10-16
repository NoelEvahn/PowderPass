package com.powderpass.backrooms.audio;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Detects Bedrock players connected through Geyser/Floodgate without a compile-time dependency: Floodgate's API is
 * used reflectively when installed; otherwise Floodgate's UUID convention (most significant bits 0) is used.
 * Bedrock clients never receive the Java pack; whether they hear custom sounds depends on the Bedrock pack being
 * installed in Geyser (see {@code bedrock.custom-sounds}).
 */
final class Bedrock {
    private static Method instance, check;
    private static boolean looked;

    private Bedrock() {
    }

    static boolean is(Player p) {
        UUID id = p.getUniqueId();
        if (!looked) {
            looked = true;
            try {
                Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                instance = api.getMethod("getInstance");
                check = api.getMethod("isFloodgatePlayer", UUID.class);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                instance = null;
            }
        }
        if (instance != null) {
            try {
                return (Boolean) check.invoke(instance.invoke(null), id);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // fall through to the UUID convention
            }
        }
        return id.getMostSignificantBits() == 0;
    }
}
