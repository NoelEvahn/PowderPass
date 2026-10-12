package dev.powderpass.pass;

import dev.powderpass.config.PluginConfig;
import dev.powderpass.util.ItemBuilder;
import dev.powderpass.util.Keys;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Lift tickets. A pass is a physical item bound to its buyer: copies are worthless to anyone else and every
 * pass expires, so duplicating or trading them gains nothing.
 */
public final class PassService {

    /** Data read from a pass item. */
    public record PassInfo(UUID owner, long expiry, PassType type, String serial) {
        public boolean validFor(UUID player, long now) {
            return owner.equals(player) && expiry > now;
        }
    }

    public enum Purchase { OK, CANNOT_AFFORD }

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private final Supplier<PluginConfig> config;

    public PassService(Supplier<PluginConfig> config) {
        this.config = config;
    }

    public PluginConfig.PassType settings(PassType type) {
        return type == PassType.DAY ? config.get().dayPass : config.get().seasonPass;
    }

    public ItemStack create(Player owner, PassType type) {
        long now = System.currentTimeMillis();
        long expiry = now + settings(type).durationMillis();
        String serial = "PP-" + Long.toString(ThreadLocalRandom.current().nextLong(36L * 36 * 36 * 36 * 36 * 36), 36)
                .toUpperCase(Locale.ROOT);
        return ItemBuilder.of(Material.PAPER)
                .name("<gradient:#7dd3fc:#e0f2fe><bold>PowderPass</bold></gradient> <white>" + type.display())
                .lore("<dark_gray>━━━━━━━━━━━━━━━━━━",
                        "<gray>Holder: <white>" + owner.getName(),
                        "<gray>Valid until: <white>" + FORMAT.format(Instant.ofEpochMilli(expiry)),
                        "<gray>Serial: <dark_gray>" + serial,
                        "<dark_gray>━━━━━━━━━━━━━━━━━━",
                        "<#bae6fd>Keep it in your inventory to ride lifts.",
                        "<dark_gray>Non-transferable.")
                .glow(true)
                .stackSize(1)
                .data(Keys.PASS_OWNER, PersistentDataType.STRING, owner.getUniqueId().toString())
                .data(Keys.PASS_EXPIRY, PersistentDataType.LONG, expiry)
                .data(Keys.PASS_TYPE, PersistentDataType.STRING, type.id())
                .data(Keys.PASS_SERIAL, PersistentDataType.STRING, serial)
                .build();
    }

    public static PassInfo read(ItemStack stack) {
        if (stack == null || stack.getType() != Material.PAPER || !stack.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String owner = pdc.get(Keys.PASS_OWNER, PersistentDataType.STRING);
        Long expiry = pdc.get(Keys.PASS_EXPIRY, PersistentDataType.LONG);
        PassType type = PassType.byId(pdc.get(Keys.PASS_TYPE, PersistentDataType.STRING));
        if (owner == null || expiry == null || type == null) {
            return null;
        }
        try {
            return new PassInfo(UUID.fromString(owner), expiry, type, pdc.getOrDefault(Keys.PASS_SERIAL, PersistentDataType.STRING, "?"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isPass(ItemStack stack) {
        return read(stack) != null;
    }

    /** The longest-lasting valid pass the player carries, or null. */
    public PassInfo validPass(Player player) {
        long now = System.currentTimeMillis();
        PassInfo best = null;
        PlayerInventory inv = player.getInventory();
        for (ItemStack item : inv.getContents()) {
            PassInfo info = read(item);
            if (info != null && info.validFor(player.getUniqueId(), now) && (best == null || info.expiry() > best.expiry())) {
                best = info;
            }
        }
        return best;
    }

    /** Takes the price and hands over a pass. All-or-nothing. */
    public Purchase purchase(Player player, PassType type) {
        PluginConfig.PassType cost = settings(type);
        PlayerInventory inv = player.getInventory();
        if (cost.costAmount() > 0) {
            ItemStack unit = new ItemStack(cost.costMaterial());
            if (!inv.containsAtLeast(unit, cost.costAmount())) {
                return Purchase.CANNOT_AFFORD;
            }
            int remaining = cost.costAmount();
            int removed = 0;
            int maxStack = cost.costMaterial().getMaxStackSize();
            while (remaining > 0) {
                int chunk = Math.min(maxStack, remaining);
                Map<Integer, ItemStack> left = inv.removeItem(new ItemStack(cost.costMaterial(), chunk));
                int notRemoved = left.values().stream().mapToInt(ItemStack::getAmount).sum();
                removed += chunk - notRemoved;
                remaining -= chunk;
                if (notRemoved > 0) {
                    refund(player, cost.costMaterial(), removed);
                    return Purchase.CANNOT_AFFORD;
                }
            }
        }
        ItemStack pass = create(player, type);
        for (ItemStack overflow : inv.addItem(pass).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
        return Purchase.OK;
    }

    private static void refund(Player player, Material material, int amount) {
        int maxStack = material.getMaxStackSize();
        while (amount > 0) {
            int chunk = Math.min(maxStack, amount);
            for (ItemStack overflow : player.getInventory().addItem(new ItemStack(material, chunk)).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
            amount -= chunk;
        }
    }

    public static String formatExpiry(long expiry) {
        return FORMAT.format(Instant.ofEpochMilli(expiry));
    }
}
