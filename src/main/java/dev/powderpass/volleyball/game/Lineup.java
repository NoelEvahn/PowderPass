package dev.powderpass.volleyball.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a team's players and their preferred roles into a rotation order.
 * <p>
 * Six players use the standard 5-1 system: S, OH, MB, OPP, OH, MB from position 1 to 6, which keeps the setter
 * opposite the opposite hitter and pairs of hitters and middles opposite each other. Smaller teams use a
 * shortened template. A libero (only with four or more players) stays out of the rotation.
 */
public final class Lineup {

    private final List<UUID> order;
    private final UUID libero;
    private final Map<UUID, Role> roles;

    private Lineup(List<UUID> order, UUID libero, Map<UUID, Role> roles) {
        this.order = order;
        this.libero = libero;
        this.roles = roles;
    }

    public List<UUID> order() {
        return order;
    }

    public UUID libero() {
        return libero;
    }

    /** Role each player ended up with (players without a preference get the slot they filled). */
    public Map<UUID, Role> roles() {
        return roles;
    }

    static Role[] template(int n) {
        return switch (n) {
            case 6 -> new Role[]{Role.SETTER, Role.OUTSIDE, Role.MIDDLE, Role.OPPOSITE, Role.OUTSIDE, Role.MIDDLE};
            case 5 -> new Role[]{Role.SETTER, Role.OUTSIDE, Role.MIDDLE, Role.OPPOSITE, Role.OUTSIDE};
            case 4 -> new Role[]{Role.SETTER, Role.OUTSIDE, Role.MIDDLE, Role.OUTSIDE};
            case 3 -> new Role[]{Role.SETTER, Role.OUTSIDE, Role.MIDDLE};
            case 2 -> new Role[]{Role.SETTER, Role.OUTSIDE};
            default -> {
                Role[] r = new Role[Math.max(0, n)];
                Arrays.fill(r, Role.ANY);
                yield r;
            }
        };
    }

    /**
     * @param players  in join order
     * @param wanted   preferred roles (missing = any)
     */
    public static Lineup build(List<UUID> players, Map<UUID, Role> wanted) {
        UUID libero = null;
        List<UUID> field = new ArrayList<>();
        for (UUID id : players) {
            if (libero == null && wanted.get(id) == Role.LIBERO && players.size() >= 4) {
                libero = id;
            } else {
                field.add(id);
            }
        }
        Role[] slots = template(field.size());
        UUID[] placed = new UUID[slots.length];
        List<UUID> rest = new ArrayList<>();
        // exact role matches first, in join order
        for (UUID id : field) {
            Role r = wanted.getOrDefault(id, Role.ANY);
            int slot = -1;
            for (int i = 0; i < slots.length; i++) {
                if (placed[i] == null && slots[i] == r && r != Role.ANY) {
                    slot = i;
                    break;
                }
            }
            if (slot >= 0) {
                placed[slot] = id;
            } else {
                rest.add(id);
            }
        }
        // everybody else fills the remaining slots
        for (int i = 0; i < placed.length && !rest.isEmpty(); i++) {
            if (placed[i] == null) {
                placed[i] = rest.remove(0);
            }
        }
        List<UUID> order = new ArrayList<>(Arrays.asList(placed));
        Map<UUID, Role> roles = new LinkedHashMap<>();
        for (int i = 0; i < placed.length; i++) {
            Role want = wanted.getOrDefault(placed[i], Role.ANY);
            // a player keeps their own pick unless it was impossible (e.g. two setters), then takes the slot
            roles.put(placed[i], want == slots[i] ? want : slots[i]);
        }
        if (libero != null) {
            roles.put(libero, Role.LIBERO);
        }
        return new Lineup(order, libero, roles);
    }
}
