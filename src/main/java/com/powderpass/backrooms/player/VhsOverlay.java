package com.powderpass.backrooms.player;

import com.powderpass.backrooms.BackroomsPlugin;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A camcorder look over everything inside the Backrooms: grain, scanlines, a vignette and a blinking REC.
 *
 * <p>How: since 1.21.4 an item worn on the head can carry a {@code camera_overlay}, a full-screen texture (it is how
 * the carved pumpkin's blur works). While a player with the resource pack is inside, their head slot holds an
 * invisible "VHS" item (an empty item model, so nothing shows on their head) whose overlay is one of the pack's
 * {@code misc/vhs_<n>} frames, cycled every few ticks so the grain moves. With a lit flashlight in hand the frames
 * switch to {@code misc/vhs_light_<n>}: the same tape, seen down a torch beam.</p>
 *
 * <p>The item never leaves the head slot (clicks, drags, drops and armour swaps on it are refused) and is removed
 * the moment the player leaves, quits, or the plugin stops. A helmet they were wearing is moved into their
 * inventory while they are inside (if there is no room, they keep it and simply do not get the overlay).</p>
 */
public final class VhsOverlay implements Listener {
    public static final int FRAMES = 4;
    private static final Key SILENT = Key.key("minecraft", "intentionally_empty");

    private final BackroomsPlugin plugin;
    private final NamespacedKey tag;
    private final Map<UUID, Integer> frame = new HashMap<>();
    private final Set<UUID> warned = new HashSet<>();
    private BukkitTask task;

    public VhsOverlay(BackroomsPlugin plugin) {
        this.plugin = plugin;
        this.tag = new NamespacedKey(plugin, "vhs");
    }

    public void start() {
        stop();
        int every = Math.max(1, plugin.cfg().vhsFrameTicks);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, every);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (Player p : plugin.getServer().getOnlinePlayers()) remove(p);
        frame.clear();
    }

    /** Is this the overlay item? */
    public boolean isVhs(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(tag, PersistentDataType.BYTE);
    }

    public boolean wearing(Player p) {
        return isVhs(p.getInventory().getHelmet());
    }

    private boolean wants(Player p) {
        return plugin.cfg().vhsOverlay && plugin.worlds().isBackrooms(p.getWorld()) && plugin.pack().loaded(p)
                && p.getGameMode() != org.bukkit.GameMode.SPECTATOR;
    }

    private void tick() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!wants(p)) {
                remove(p);
                continue;
            }
            ItemStack head = p.getInventory().getHelmet();
            if (head != null && !head.getType().isAir() && !isVhs(head)) {
                // their own helmet goes into the inventory while they are inside
                if (p.getInventory().firstEmpty() < 0) {
                    if (warned.add(p.getUniqueId())) p.sendActionBar(Component.text("Take off your helmet to see through the camera."));
                    continue;
                }
                p.getInventory().addItem(head);
                p.getInventory().setHelmet(null);
            }
            int f = (frame.getOrDefault(p.getUniqueId(), 0) + 1) % FRAMES;
            frame.put(p.getUniqueId(), f);
            boolean light = plugin.flashlights() != null && plugin.flashlights().lit(p);
            p.getInventory().setHelmet(item((light ? "misc/vhs_light_" : "misc/vhs_") + f));
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private ItemStack item(String overlay) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.itemName(Component.text("Camcorder"));
        m.setItemModel(new NamespacedKey("backrooms", "vhs"));
        m.setMaxStackSize(1);
        m.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        it.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD)
                .cameraOverlay(Key.key("backrooms", overlay))
                .equipSound(SILENT)
                .dispensable(false)
                .swappable(false)
                .damageOnHurt(false)
                .equipOnInteract(false)
                .build());
        return it;
    }

    /** Take the overlay item away (wherever it ended up). */
    public void remove(Player p) {
        frame.remove(p.getUniqueId());
        warned.remove(p.getUniqueId());
        if (isVhs(p.getInventory().getHelmet())) p.getInventory().setHelmet(null);
        ItemStack[] all = p.getInventory().getContents();
        for (int i = 0; i < all.length; i++) if (isVhs(all[i])) p.getInventory().setItem(i, null);
        if (isVhs(p.getItemOnCursor())) p.setItemOnCursor(null);
    }

    // ------------------------------------------------------------------ keep it where it is

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !wearing(p) && !isVhs(e.getCurrentItem()) && !isVhs(e.getCursor())) return;
        boolean headSlot = e.getSlotType() == InventoryType.SlotType.ARMOR && e.getSlot() == 39;
        if (headSlot || isVhs(e.getCurrentItem()) || isVhs(e.getCursor())
                || (e.getClick().isShiftClick() && e.getCurrentItem() != null && isHelmetLike(e.getCurrentItem()))) {
            e.setCancelled(true);
        }
    }

    private static boolean isHelmetLike(ItemStack it) {
        String n = it.getType().name();
        return n.endsWith("_HELMET") || n.equals("CARVED_PUMPKIN") || n.endsWith("_HEAD") || n.endsWith("_SKULL") || n.equals("TURTLE_HELMET");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && wearing(p) && (isVhs(e.getOldCursor()) || e.getRawSlots().contains(5))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (isVhs(e.getItemDrop().getItemStack())) {
            e.getItemDrop().remove();
            e.setCancelled(false);
        }
    }

    /** Right-clicking a helmet would swap it onto the head. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent e) {
        if (e.getItem() == null || !wearing(e.getPlayer())) return;
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) && isHelmetLike(e.getItem())) {
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        if (!wants(e.getPlayer())) remove(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        remove(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        remove(e.getPlayer()); // a crash may have left one behind; the next tick puts it back if they are inside
    }
}
