package dev.powderpass.volleyball.listener;

import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.court.Court;
import dev.powderpass.volleyball.court.CourtVisuals;
import dev.powderpass.volleyball.game.Match;
import dev.powderpass.volleyball.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

/** Right-click a bench or the referee chair to sit; sneak to get up. */
public final class SeatListener implements Listener {

    private final PowderPassPlugin plugin;

    public SeatListener(PowderPassPlugin plugin) {
        this.plugin = plugin;
    }

    private static boolean isSeat(Entity e) {
        if (!(e instanceof Interaction)) {
            return false;
        }
        String tag = e.getPersistentDataContainer().get(Keys.ENTITY, PersistentDataType.STRING);
        return tag != null && tag.startsWith("seat:");
    }

    private CourtVisuals.Seat find(Entity e) {
        String tag = e.getPersistentDataContainer().get(Keys.ENTITY, PersistentDataType.STRING);
        Court c = tag == null ? null : plugin.courts().get(tag.substring("seat:".length()));
        CourtVisuals v = c == null ? null : plugin.courts().visuals(c);
        if (v == null) {
            return null;
        }
        for (CourtVisuals.Seat s : v.seats()) {
            if (s.entity().equals(e)) {
                return s;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(PlayerInteractEntityEvent e) {
        if (!isSeat(e.getRightClicked())) {
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = e.getPlayer();
        Match m = plugin.matches().byPlayer(p);
        if (m != null && m.onCourt(p.getUniqueId())) {
            p.sendActionBar(Component.text("You're on court - no sitting during the match", NamedTextColor.GRAY));
            return;
        }
        Entity seat = e.getRightClicked();
        if (!seat.getPassengers().isEmpty()) {
            p.sendActionBar(Component.text("Seat taken", NamedTextColor.GRAY));
            return;
        }
        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }
        seat.addPassenger(p);
        p.sendActionBar(Component.text("Sneak to stand up", NamedTextColor.GRAY));
    }

    /** Stepping off a bench puts you in front of it instead of inside the planks. */
    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player p) || !isSeat(e.getDismounted())) {
            return;
        }
        CourtVisuals.Seat seat = find(e.getDismounted());
        if (seat == null) {
            return;
        }
        Location seatLoc = e.getDismounted().getLocation();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // a match teleport right after dismounting moves the player away: leave that alone
            if (p.isOnline() && !p.isInsideVehicle() && p.getWorld() == seatLoc.getWorld()
                    && p.getLocation().distanceSquared(seatLoc) < 2.5 * 2.5) {
                Location front = seat.front().clone();
                front.setPitch(p.getLocation().getPitch());
                p.teleport(front);
            }
        });
    }
}
