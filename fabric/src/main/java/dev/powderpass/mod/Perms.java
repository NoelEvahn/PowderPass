package dev.powderpass.mod;

import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.Method;

/**
 * Operator check that tolerates the permission-API changes around 1.21.11 (integer levels were replaced by
 * permission sets). Tries the known shapes reflectively and denies when none applies.
 */
final class Perms {
    private static volatile boolean warned;

    private Perms() {}

    static boolean admin(CommandSourceStack src) {
        // Console / command blocks without an entity.
        if (src.getEntity() == null) return true;
        try {
            Method m = src.getClass().getMethod("hasPermission", int.class);
            return (boolean) m.invoke(src, 2);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Object server = src.getServer();
            Object player = src.getPlayer();
            if (player != null) {
                Object profile = player.getClass().getMethod("getGameProfile").invoke(player);
                for (Method m : server.getClass().getMethods()) {
                    if (m.getName().equals("isSingleplayerOwner") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isInstance(profile) && (boolean) m.invoke(server, profile)) {
                        return true;
                    }
                }
                Object list = server.getClass().getMethod("getPlayerList").invoke(server);
                for (Method m : list.getClass().getMethods()) {
                    if (!m.getName().equals("isOp") || m.getParameterCount() != 1) continue;
                    Class<?> pt = m.getParameterTypes()[0];
                    Object arg = pt.isInstance(profile) ? profile : null;
                    if (arg == null) {
                        for (Method pm : player.getClass().getMethods()) {
                            if (pm.getParameterCount() == 0 && pt.isAssignableFrom(pm.getReturnType())) {
                                arg = pm.invoke(player);
                                break;
                            }
                        }
                    }
                    if (arg != null) return (boolean) m.invoke(list, arg);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        if (!warned) {
            warned = true;
            PowderPassMod.LOGGER.warn("PowderPass: could not determine operator status with this game version; commands are console-only");
        }
        return false;
    }
}
