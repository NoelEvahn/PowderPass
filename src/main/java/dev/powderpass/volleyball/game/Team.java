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
    private final java.util.Map<UUID, Role> roles = new java.util.HashMap<>();
    private UUID libero;
    /** The back-row player the libero replaces in the current rally (null = libero not on court). */
    private UUID benched;
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
        return order.contains(id) || id.equals(libero);
    }

    public void applyLineup(Lineup lineup) {
        order.clear();
        order.addAll(lineup.order());
        roles.clear();
        roles.putAll(lineup.roles());
        libero = lineup.libero();
        benched = null;
    }

    public Role role(UUID id) {
        return roles.getOrDefault(id, Role.ANY);
    }

    public UUID libero() {
        return libero;
    }

    public void clearLibero() {
        libero = null;
        benched = null;
    }

    public UUID benched() {
        return benched;
    }

    public void setBenched(UUID benched) {
        this.benched = benched;
    }

    public boolean isLibero(UUID id) {
        return id.equals(libero);
    }

    /** On the court this rally: not swapped out for the libero, and the libero only while replacing someone. */
    public boolean onCourt(UUID id) {
        if (id.equals(libero)) {
            return benched != null;
        }
        return order.contains(id) && !id.equals(benched);
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

    /** Rotation position (1-based) of a player. The libero takes the position of the player they replace. */
    public int positionOf(UUID player, int rotation) {
        if (player.equals(libero)) {
            return benched == null ? -1 : positionOf(benched, rotation);
        }
        int idx = order.indexOf(player);
        if (idx < 0) {
            return -1;
        }
        return Math.floorMod(idx - rotation, order.size()) + 1;
    }

    /** Every online member, the libero included. */
    public List<Player> online() {
        List<Player> list = new ArrayList<>();
        for (UUID id : order) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                list.add(p);
            }
        }
        if (libero != null) {
            Player p = Bukkit.getPlayer(libero);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    /** Players currently on the court. */
    public List<Player> onCourt() {
        List<Player> list = new ArrayList<>();
        for (Player p : online()) {
            if (onCourt(p.getUniqueId())) {
                list.add(p);
            }
        }
        return list;
    }
}
