package dev.powderpass.volleyball.game;

import dev.powderpass.volleyball.config.Settings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A team in a running match. {@code order} is the line-up: index 0 starts in position 1 (server). */
public final class Team {

    private final TeamId id;
    private final Settings.TeamStyle style;
    private final List<UUID> order = new ArrayList<>();
    private int side;
    private int timeoutsUsed;

    public Team(TeamId id, Settings.TeamStyle style, int side) {
        this.id = id;
        this.style = style;
        this.side = side;
    }

    public TeamId id() {
        return id;
    }

    public String name() {
        return style.name();
    }

    public TextColor color() {
        return style.color();
    }

    public Component displayName() {
        return Component.text(style.name(), style.color(), TextDecoration.BOLD);
    }

    public List<UUID> order() {
        return order;
    }

    public int size() {
        return order.size();
    }

    public boolean contains(UUID id) {
        return order.contains(id);
    }

    /** -1 = court side with u &lt; 0, +1 = u &gt; 0. */
    public int side() {
        return side;
    }

    public void switchSide() {
        side = -side;
    }

    public int timeoutsUsed() {
        return timeoutsUsed;
    }

    public void useTimeout() {
        timeoutsUsed++;
    }

    public void resetTimeouts() {
        timeoutsUsed = 0;
    }

    /** Player standing in rotation position {@code position} (1-based) after {@code rotation} rotations. */
    public UUID atPosition(int position, int rotation) {
        if (order.isEmpty()) {
            return null;
        }
        return order.get(Math.floorMod(position - 1 + rotation, order.size()));
    }

    /** Rotation position (1-based) of a player. */
    public int positionOf(UUID player, int rotation) {
        int idx = order.indexOf(player);
        if (idx < 0) {
            return -1;
        }
        return Math.floorMod(idx - rotation, order.size()) + 1;
    }

    public List<Player> online() {
        List<Player> list = new ArrayList<>();
        for (UUID id : order) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }
}
