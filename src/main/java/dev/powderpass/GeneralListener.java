package dev.powderpass;

import dev.powderpass.display.Managed;
import dev.powderpass.gear.GearItems;
import dev.powderpass.gui.TicketMenu;
import dev.powderpass.pass.KioskManager;
import dev.powderpass.pass.PassService;
import org.bukkit.Keyed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/** Cross-cutting events: profiles, lifts, kiosks, world lifecycle, stray entity cleanup and crafting guards. */
public final class GeneralListener implements Listener {

    private final PowderPassPlugin plugin;

    public GeneralListener(PowderPassPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------------------------------------ profiles

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            plugin.profiles().preload(event.getUniqueId(), event.getName());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.profiles().get(p);
        // A vehicle of ours saved with the player (should never happen) is discarded.
        Entity vehicle = p.getVehicle();
        if (vehicle != null && Managed.is(vehicle)) {
            p.leaveVehicle();
            vehicle.remove();
        }
        for (org.bukkit.NamespacedKey key : plugin.gear().recipeKeys()) {
            p.discoverRecipe(key);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuitEarly(PlayerQuitEvent event) {
        // Before the server serialises the player (and their vehicle).
        plugin.lifts().rescue(event.getPlayer());
        plugin.lifts().forgetSurvey(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.profiles().unload(event.getPlayer());
    }

    // ------------------------------------------------------------------------------------------------ lifts

    @EventHandler(priority = EventPriority.HIGH)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Entity vehicle = event.getDismounted();
        if (!Managed.is(vehicle) || plugin.skis().byAnchor(vehicle) != null) {
            return;
        }
        if (plugin.lifts().shouldBlockDismount(player, vehicle) && event.isCancellable()) {
            event.setCancelled(true);
            plugin.messages().actionBar(player, "lift.hold-on");
            return;
        }
        plugin.lifts().onDismounted(player, vehicle);
    }

    // ------------------------------------------------------------------------------------------------ kiosks

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction) || !KioskManager.isKiosk(interaction)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = event.getPlayer();
        if (!p.hasPermission("powderpass.pass.buy")) {
            plugin.messages().send(p, "no-permission");
            return;
        }
        new TicketMenu(plugin, p).open(p);
    }

    // ------------------------------------------------------------------------------------------------ world lifecycle

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        // Our entities are never saved; anything marked that comes back from disk is a leftover.
        for (Entity e : event.getEntities()) {
            if (Managed.is(e)) {
                e.remove();
            }
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        plugin.onWorldUnload(event.getWorld());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.onWorldLoad(event.getWorld());
    }

    // ------------------------------------------------------------------------------------------------ crafting

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        boolean ours = recipe instanceof Keyed keyed && plugin.gear().isOurRecipe(keyed.getKey());
        if (ours) {
            dev.powderpass.gear.Gear required = plugin.gear().requiredInput(((Keyed) recipe).getKey());
            if (required != null) {
                boolean found = false;
                for (ItemStack item : event.getInventory().getMatrix()) {
                    if (item != null && item.getType() == org.bukkit.Material.CARROT_ON_A_STICK) {
                        found = required.equals(GearItems.identify(item));
                    }
                }
                if (!found) {
                    event.getInventory().setResult(null);
                }
            }
            return;
        }
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item == null) {
                continue;
            }
            boolean special = GearItems.identify(item) != null || PassService.isPass(item);
            if (special && !ours) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack base = event.getInventory().getInputEquipment();
        Recipe recipe = event.getInventory().getRecipe();
        boolean ours = recipe instanceof Keyed keyed && plugin.gear().isOurRecipe(keyed.getKey());
        if (ours) {
            dev.powderpass.gear.Gear required = plugin.gear().requiredInput(((Keyed) recipe).getKey());
            if (required != null && !required.equals(GearItems.identify(base))) {
                event.setResult(null);
            }
        } else if (GearItems.identify(base) != null || PassService.isPass(base)) {
            event.setResult(null);
        }
    }
}
