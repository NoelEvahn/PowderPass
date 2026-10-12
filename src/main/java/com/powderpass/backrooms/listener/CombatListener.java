package com.powderpass.backrooms.listener;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.creature.Creature;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Creatures are hit through their Interaction hitbox. Damage follows the attacker's attack-damage attribute
 * scaled by the attack cooldown, so weapons and timing matter as they would against a vanilla mob.
 */
public final class CombatListener implements Listener {
    private final BackroomsPlugin plugin;

    public CombatListener(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        if (!(e.getAttacked() instanceof Interaction hit)) return;
        Player p = e.getPlayer();
        Creature c = plugin.creatures().byHitbox(hit);
        if (c != null) {
            AttributeInstance a = p.getAttribute(Attribute.ATTACK_DAMAGE);
            double dmg = (a == null ? 1 : a.getValue()) * Math.max(0.2, p.getAttackCooldown());
            p.resetCooldown();
            c.hurt(p, dmg);
            plugin.creatures().noise(p.getLocation(), 12);
            return;
        }
        plugin.decorations().handleAttack(p, hit);
    }
}
