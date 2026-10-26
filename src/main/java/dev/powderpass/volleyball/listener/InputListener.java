package dev.powderpass.volleyball.listener;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.ball.Volleyball;
import dev.powderpass.volleyball.config.Settings;
import dev.powderpass.volleyball.game.Match;
import dev.powderpass.volleyball.items.CustomItems;
import dev.powderpass.volleyball.items.ItemType;
import dev.powderpass.volleyball.util.Msg;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maps raw Minecraft input to volleyball actions. Left clicks are taken from the arm swing packet, which the
 * client sends for clicks into the air, on blocks and on entities alike.
 */
public final class InputListener implements Listener {

    private final PowderPassPlugin plugin;
    private final Map<UUID, Long> whistleCooldown = new HashMap<>();

    public InputListener(PowderPassPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemType type = CustomItems.typeOf(hand);
        if (type == ItemType.VOLLEYBALL && !hasTossInAir(p)) {
            underhand(p);
            return;
        }
        if (type == ItemType.WHISTLE) {
            return;
        }
        plugin.hits().primary(p);
    }

    private boolean hasTossInAir(Player p) {
        for (Volleyball b : plugin.balls().near(p.getLocation(), 8)) {
            if (p.getUniqueId().equals(b.tossedBy())) {
                return true;
            }
        }
        return false;
    }

    private void underhand(Player p) {
        Match m = plugin.matches().byPlayer(p);
        if (m != null) {
            m.requestUnderhand(p);
            return;
        }
        if (!p.hasPermission("powderpass.practice")) {
            return;
        }
        Volleyball b = plugin.balls().spawnPractice(p, plugin.hits().underhandPosition(p));
        b.markToss(p.getUniqueId());
        plugin.hits().underhand(p, b);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            ItemType off = CustomItems.typeOf(e.getItem());
            if (off != null) {
                e.setCancelled(true);
            }
            return;
        }
        Action a = e.getAction();
        Player p = e.getPlayer();
        ItemType type = CustomItems.typeOf(e.getItem());
        boolean inMatch = plugin.matches().byPlayer(p) != null;
        if (type == null) {
            if (inMatch && (a == Action.RIGHT_CLICK_BLOCK || a == Action.PHYSICAL)) {
                e.setCancelled(true);
            }
            return;
        }
        if (a == Action.PHYSICAL) {
            return;
        }
        e.setCancelled(true);
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        switch (type) {
            case VOLLEYBALL -> toss(p);
            case HANDS -> plugin.hits().secondary(p);
            case WHISTLE -> whistle(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = e.getPlayer();
        ItemType type = CustomItems.typeOf(p.getInventory().getItemInMainHand());
        if (type == null) {
            return;
        }
        e.setCancelled(true);
        switch (type) {
            case VOLLEYBALL -> toss(p);
            case HANDS -> plugin.hits().secondary(p);
            case WHISTLE -> whistle(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        Player p = e.getPlayer();
        if (plugin.matches().byPlayer(p) != null || CustomItems.typeOf(p.getInventory().getItemInMainHand()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onJump(PlayerJumpEvent e) {
        plugin.hits().onJump(e.getPlayer());
    }

    private void toss(Player p) {
        Match m = plugin.matches().byPlayer(p);
        if (m != null) {
            m.requestToss(p);
            return;
        }
        if (!p.hasPermission("powderpass.practice")) {
            Msg.error(p, "You don't have permission to practice.");
            return;
        }
        if (hasTossInAir(p)) {
            return;
        }
        plugin.hits().toss(p, true);
    }

    private void whistle(Player p) {
        long now = System.currentTimeMillis();
        Long last = whistleCooldown.get(p.getUniqueId());
        if (last != null && now - last < 700) {
            return;
        }
        whistleCooldown.put(p.getUniqueId(), now);
        Match m = plugin.matches().officiatedBy(p);
        if (p.isSneaking()) {
            if (m != null && m.isOfficial(p)) {
                plugin.menus().openReferee(p, m);
            } else {
                p.sendActionBar(Component.text("You are not refereeing a match", NamedTextColor.GRAY));
            }
            return;
        }
        plugin.fx().sound(p.getLocation(), Settings.SoundId.WHISTLE);
        if (m != null && m.isOfficial(p)) {
            m.refereeWhistle(p);
        }
    }
}
