package dev.powderpass.display;

import dev.powderpass.util.Keys;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/**
 * Marks entities spawned by PowderPass. All of them are non-persistent; the marker lets us sweep up any that
 * survive an unclean shutdown or a plugin reload.
 */
public final class Managed {

    private Managed() {
    }

    public static void mark(Entity entity) {
        entity.setPersistent(false);
        entity.getPersistentDataContainer().set(Keys.MANAGED, PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean is(Entity entity) {
        return entity.getPersistentDataContainer().has(Keys.MANAGED, PersistentDataType.BYTE);
    }
}
