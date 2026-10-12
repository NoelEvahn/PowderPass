package dev.powderpass.ski;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Replaces the real (sitting) player model with the animated skier for everyone else: the rider gets a short,
 * self-refreshing invisibility effect (so a crash or restart never leaves them invisible for long) and other
 * players are sent empty equipment for them. Nothing in the rider's inventory is ever touched.
 */
public final class SkierVisibility {

    private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final int EFFECT_TICKS = 60;
    private static final double VIEW_RANGE_SQ = 160 * 160;

    private final Player rider;
    private PotionEffect previous;
    private long previousSavedAt;
    private boolean active;

    public SkierVisibility(Player rider) {
        this.rider = rider;
    }

    public void apply(long tick) {
        PotionEffect existing = rider.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (existing != null && (existing.getDuration() > EFFECT_TICKS || existing.isInfinite())) {
            previous = existing;
            previousSavedAt = tick;
        }
        active = true;
        refresh(tick);
        broadcastFake();
    }

    public void refresh(long tick) {
        if (!active) {
            return;
        }
        rider.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, EFFECT_TICKS, 0, true, false, false));
    }

    /** Sends empty equipment for the rider to every nearby player. */
    public void broadcastFake() {
        if (!active) {
            return;
        }
        Location here = rider.getLocation();
        for (Player viewer : rider.getWorld().getPlayers()) {
            if (viewer != rider && viewer.getLocation().distanceSquared(here) < VIEW_RANGE_SQ) {
                sendFake(viewer);
            }
        }
    }

    public void sendFake(Player viewer) {
        if (!active || viewer == rider || !viewer.isOnline()) {
            return;
        }
        Map<EquipmentSlot, ItemStack> empty = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : SLOTS) {
            empty.put(slot, null);
        }
        viewer.sendEquipmentChange(rider, empty);
    }

    public void restore(long tick) {
        if (!active) {
            return;
        }
        active = false;
        rider.removePotionEffect(PotionEffectType.INVISIBILITY);
        if (previous != null) {
            if (previous.isInfinite()) {
                rider.addPotionEffect(previous);
            } else {
                int remaining = previous.getDuration() - (int) Math.max(0, tick - previousSavedAt);
                if (remaining > 0) {
                    rider.addPotionEffect(previous.withDuration(remaining));
                }
            }
        }
        if (!rider.isOnline()) {
            return;
        }
        EntityEquipment eq = rider.getEquipment();
        Map<EquipmentSlot, ItemStack> real = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : SLOTS) {
            real.put(slot, eq.getItem(slot));
        }
        Location here = rider.getLocation();
        for (Player viewer : rider.getWorld().getPlayers()) {
            if (viewer != rider && viewer.getLocation().distanceSquared(here) < VIEW_RANGE_SQ) {
                viewer.sendEquipmentChange(rider, real);
            }
        }
    }

    public boolean active() {
        return active;
    }
}
