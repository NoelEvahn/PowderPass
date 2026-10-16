package com.powderpass.backrooms.listener;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.display.Keys;
import com.powderpass.backrooms.item.Items;
import com.powderpass.backrooms.player.Session;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/** Player lifecycle inside and around the Backrooms. */
public final class PlayerListener implements Listener {
    private final BackroomsPlugin plugin;

    public PlayerListener(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    private BackroomsConfig cfg() {
        return plugin.cfg();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.sessions().load(p);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            boolean inside = plugin.sessions().inside(p);
            if (!inside && plugin.sessions().insideFlag(p)) {
                // stranded: e.g. their level was disabled while they were offline
                plugin.getLogger().info(p.getName() + " was stranded outside the Backrooms; returning them home.");
                plugin.sessions().leave(p, null);
                return;
            }
            if (inside) {
                Level level = plugin.worlds().levelOf(p.getWorld());
                if (!plugin.teleports().isSafe(p.getLocation().getBlock())) {
                    Location safe = plugin.teleports().findSafe(p.getLocation(), 6);
                    if (safe != null) p.teleportAsync(safe);
                    else plugin.teleports().teleportSafe(p, plugin.worlds().levelSpawn(level), null);
                }
                if (!plugin.sessions().hasReturn(p)) {
                    plugin.getLogger().info(p.getName() + " is inside without a return point; the fallback world will be used on exit.");
                }
                plugin.sessions().get(p).cooldownUntil = plugin.sessions().now() + cfg().transitionCooldownTicks;
            }
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.flashlights().clear(e.getPlayer());
        plugin.sessions().unload(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom(), to = e.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) return;
        Player p = e.getPlayer();
        if (!plugin.worlds().isBackrooms(to.getWorld())) return;
        plugin.transitions().onMove(p, to);
        Session s = plugin.sessions().get(p);
        long now = plugin.sessions().now();
        if (now >= s.noiseCooldownUntil) {
            double loud = p.isSprinting() ? 16 : p.isSneaking() ? 2.5 : 7;
            if (p.isInWater()) loud += 3;
            plugin.creatures().noise(to, loud);
            s.noiseCooldownUntil = now + 10;
        }
    }

    /** Return-point bookkeeping for every way in or out (commands, other plugins, admins). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null || e.getFrom().getWorld() == e.getTo().getWorld()) return;
        boolean fromBr = plugin.worlds().isBackrooms(e.getFrom().getWorld());
        boolean toBr = plugin.worlds().isBackrooms(e.getTo().getWorld());
        Player p = e.getPlayer();
        if (!fromBr && toBr && !plugin.sessions().insideFlag(p)) {
            plugin.sessions().saveReturn(p, e.getFrom());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onTeleportRestrict(PlayerTeleportEvent e) {
        if (!plugin.worlds().isBackrooms(e.getFrom().getWorld())) return;
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if ((c == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && cfg().blockPearls)
                || (c == PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT && cfg().blockChorus)) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(cfg().raw("protected"));
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        boolean fromBr = plugin.worlds().isBackrooms(e.getFrom());
        boolean nowBr = plugin.worlds().isBackrooms(p.getWorld());
        if (fromBr && !nowBr) {
            plugin.flashlights().clear(p);
            plugin.sessions().clearInside(p);
        }
        if (nowBr && cfg().suppressMusic) p.stopSound(SoundCategory.MUSIC);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) // the death screen cancels deaths inside
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!plugin.worlds().isBackrooms(p.getWorld())) return;
        Session s = plugin.sessions().get(p);
        s.diedInside = true;
        e.deathMessage(plugin.sessions().deathMessage(p));
        if (cfg().keepInventory) {
            e.setKeepInventory(true);
            e.setKeepLevel(true);
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Session s = plugin.sessions().get(p);
        if (!s.diedInside) return;
        s.diedInside = false;
        s.inTransition = false;
        Level level = plugin.worlds().levelOf(p.getWorld());
        Location target;
        switch (cfg().deathMode) {
            case "LEVEL0" -> target = plugin.worlds().levelSpawn(Level.LEVEL_0);
            case "STAY" -> target = level == null ? null : plugin.worlds().levelSpawn(level);
            default -> target = null;
        }
        if (target == null) {
            Location home = plugin.sessions().getReturn(p);
            if (home != null) {
                e.setRespawnLocation(home);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!p.isOnline()) return;
                    plugin.sessions().clearInside(p);
                    if (!plugin.teleports().isSafe(p.getLocation().getBlock())) plugin.teleports().teleportSafe(p, p.getLocation(), null);
                }, 2L);
            }
        } else {
            e.setRespawnLocation(target);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) plugin.sessions().arrived(p, plugin.worlds().levelOf(target.getWorld()));
            }, 2L);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!plugin.worlds().isBackrooms(p.getWorld()) || p.hasPermission("backrooms.bypass.commands")) return;
        String label = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        if (cfg().blockedCommands.contains(label)) {
            e.setCancelled(true);
            p.sendMessage(cfg().raw("blocked-command"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        String id = plugin.items().id(e.getItem());
        if (Items.ALMOND.equals(id)) {
            plugin.sessions().addSanity(e.getPlayer(), 35);
            e.getPlayer().setFoodLevel(Math.min(20, e.getPlayer().getFoodLevel() + 4));
            e.getPlayer().setSaturation(Math.min(20f, e.getPlayer().getSaturation() + 2f));
            e.getPlayer().removePotionEffect(PotionEffectType.NAUSEA);
            e.getPlayer().sendMessage(cfg().raw("almond-water"));
        } else if (Items.RATION.equals(id)) {
            plugin.sessions().addSanity(e.getPlayer(), 5);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = e.getItem();
        if (item != null && Items.BATTERY.equals(plugin.items().id(item))
                && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            for (ItemStack it : p.getInventory().getContents()) {
                if (it != null && Items.FLASHLIGHT.equals(plugin.items().id(it))) {
                    plugin.items().setCharge(it, cfg().flashlightSeconds);
                    plugin.sessions().get(p).flashlightCharge = -1;
                    item.setAmount(item.getAmount() - 1);
                    p.playSound(p.getLocation(), Sound.BLOCK_COPPER_BULB_TURN_ON, SoundCategory.PLAYERS, 1f, 1.2f);
                    p.sendActionBar(BackroomsConfig.mm("<#ffe9a8>Flashlight recharged."));
                    e.setCancelled(true);
                    return;
                }
            }
            p.sendActionBar(BackroomsConfig.mm("<gray>You have nothing to put it in."));
            return;
        }
        if (!plugin.worlds().isBackrooms(p.getWorld()) || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (b.getType().name().endsWith("_DOOR")) plugin.creatures().noise(b.getLocation(), 10);
        if (b.getType() == Material.BARREL && !p.isSneaking()) fillLoot(b);
    }

    private void fillLoot(Block b) {
        BlockState st = b.getState();
        if (!(st instanceof Barrel barrel)) return;
        var pdc = barrel.getPersistentDataContainer();
        if (pdc.has(Keys.LOOTED, PersistentDataType.BYTE) || pdc.has(Keys.PLACED, PersistentDataType.BYTE)) return;
        pdc.set(Keys.LOOTED, PersistentDataType.BYTE, (byte) 1);
        barrel.update();
        Level level = plugin.worlds().levelOf(b.getWorld());
        int tier = plugin.worlds().generator(level).lootTier(b.getX(), b.getY(), b.getZ());
        if (b.getState() instanceof Barrel fresh) {
            plugin.loot().fill(fresh.getInventory(), tier, level);
        }
    }

    public static void arrivalEffects(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false));
    }
}
