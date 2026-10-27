package dev.powderpass.volleyball.listener;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.ball.Volleyball;
import dev.powderpass.volleyball.game.Match;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Everything is played with empty hands and left clicks: the arm swing packet arrives for clicks into the air,
 * on blocks and on entities alike. Sneaking changes the technique.
 */
public final class InputListener implements Listener {

    private final PowderPassPlugin plugin;

    public InputListener(PowderPassPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player p = e.getPlayer();
        Match m = plugin.matches().byPlayer(p);
        if (m != null && m.holdsServeBall(p)) {
            if (p.isSneaking()) {
                m.requestUnderhand(p);
            } else {
                m.requestToss(p);
            }
            return;
        }
        Volleyball held = plugin.balls().holding(p);
        if (held != null) {
            plugin.balls().serveHeld(p, held);
            return;
        }
        plugin.hits().primary(p);
    }

    /** Match players can't use blocks or items on the court. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if ((a == Action.RIGHT_CLICK_BLOCK || a == Action.PHYSICAL) && plugin.matches().byPlayer(e.getPlayer()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (plugin.matches().byPlayer(e.getPlayer()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        Player p = e.getPlayer();
        if (plugin.matches().byPlayer(p) != null || !plugin.balls().near(p.getLocation(), 6).isEmpty()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onJump(PlayerJumpEvent e) {
        plugin.hits().onJump(e.getPlayer());
    }
}
