package dev.powderpass.volleyball.game;

import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.court.Court;
import dev.powderpass.volleyball.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lobbies (players waiting on a court) and running matches. */
public final class MatchManager {

    private final PowderPassPlugin plugin;
    /** court name (lower case) -> player -> preferred team (null = any) */
    private final Map<String, LinkedHashMap<UUID, TeamId>> lobbies = new HashMap<>();
    private final Map<String, Match> matches = new HashMap<>();

    public MatchManager(PowderPassPlugin plugin) {
        this.plugin = plugin;
    }

    private static String key(Court c) {
        return c.name().toLowerCase();
    }

    // ------------------------------------------------------------------------------------------- lobby

    public enum JoinResult { JOINED, SWITCHED, IN_MATCH, FULL }

    public JoinResult join(Player p, Court court, TeamId preferred) {
        if (byPlayer(p) != null) {
            return JoinResult.IN_MATCH;
        }
        LinkedHashMap<UUID, TeamId> lobby = lobbies.computeIfAbsent(key(court), k -> new LinkedHashMap<>());
        boolean had = lobby.containsKey(p.getUniqueId());
        if (!had && lobby.size() >= 14) {
            return JoinResult.FULL;
        }
        leaveLobby(p);
        lobby.put(p.getUniqueId(), preferred);
        return had ? JoinResult.SWITCHED : JoinResult.JOINED;
    }

    /** Removes the player from any lobby. Returns the court name they left, or null. */
    public String leaveLobby(Player p) {
        for (Map.Entry<String, LinkedHashMap<UUID, TeamId>> e : lobbies.entrySet()) {
            if (e.getValue().containsKey(p.getUniqueId())) {
                e.getValue().remove(p.getUniqueId());
                return e.getKey();
            }
        }
        return null;
    }

    public Court lobbyOf(Player p) {
        for (Map.Entry<String, LinkedHashMap<UUID, TeamId>> e : lobbies.entrySet()) {
            if (e.getValue().containsKey(p.getUniqueId())) {
                return plugin.courts().get(e.getKey());
            }
        }
        return null;
    }

    public Map<UUID, TeamId> lobby(Court court) {
        LinkedHashMap<UUID, TeamId> l = lobbies.get(key(court));
        return l == null ? Collections.emptyMap() : Collections.unmodifiableMap(l);
    }

    // ------------------------------------------------------------------------------------------- matches

    /**
     * Starts a match with the lobby of {@code court}. Players with a team preference are honoured, the rest
     * balance the teams.
     *
     * @return an error message or null on success
     */
    public String start(Court court, Player host) {
        if (byCourt(court) != null) {
            return "A match is already running on " + court.name() + ".";
        }
        LinkedHashMap<UUID, TeamId> lobby = lobbies.getOrDefault(key(court), new LinkedHashMap<>());
        lobby.keySet().removeIf(id -> Bukkit.getPlayer(id) == null || byPlayer(id) != null);
        List<UUID> a = new ArrayList<>();
        List<UUID> b = new ArrayList<>();
        List<UUID> any = new ArrayList<>();
        for (Map.Entry<UUID, TeamId> e : lobby.entrySet()) {
            if (e.getValue() == TeamId.A && fits(a, e.getKey())) {
                a.add(e.getKey());
            } else if (e.getValue() == TeamId.B && fits(b, e.getKey())) {
                b.add(e.getKey());
            } else {
                any.add(e.getKey());
            }
        }
        Collections.shuffle(any);
        for (UUID id : any) {
            boolean preferA = a.size() <= b.size();
            if (preferA && fits(a, id)) {
                a.add(id);
            } else if (fits(b, id)) {
                b.add(id);
            } else if (fits(a, id)) {
                a.add(id);
            }
        }
        if (a.isEmpty() || b.isEmpty()) {
            return "Need at least one player on each team (" + lobby.size() + " in the lobby). Use /vb join "
                    + court.name() + ".";
        }
        lobbies.remove(key(court));
        // clear free-play balls from the court
        plugin.balls().removeIf(ball -> ball.context() == plugin.balls().practice() && court.inArea(ball.location(), 2));
        for (UUID id : a) {
            plugin.pepper().leave(Bukkit.getPlayer(id));
        }
        for (UUID id : b) {
            plugin.pepper().leave(Bukkit.getPlayer(id));
        }
        Match m = new Match(plugin, court, court.settings().copy(), a, b,
                host == null ? null : host.getUniqueId());
        matches.put(key(court), m);
        m.start();
        for (Player p : m.players()) {
            plugin.hud().attach(p, m);
        }
        if (a.size() != b.size()) {
            Msg.send(m.audience(), "<yellow>Uneven teams: " + a.size() + "v" + b.size() + ".");
        }
        return null;
    }
    /** Six players per team. */
    private static boolean fits(List<UUID> team, UUID candidate) {
        return team.size() < 6;
    }

    public boolean stop(Court court, String reason) {
        Match m = matches.remove(key(court));
        if (m == null) {
            return false;
        }
        m.end(reason);
        return true;
    }

    public Match byCourt(Court court) {
        return court == null ? null : matches.get(key(court));
    }

    public Match byPlayer(Player p) {
        return byPlayer(p.getUniqueId());
    }

    public Match byPlayer(UUID id) {
        for (Match m : matches.values()) {
            if (m.contains(id)) {
                return m;
            }
        }
        return null;
    }
    public Collection<Match> all() {
        return matches.values();
    }

    public List<Player> allPlayers() {
        List<Player> list = new ArrayList<>();
        for (Match m : matches.values()) {
            list.addAll(m.players());
        }
        return list;
    }

    public void tick() {
        if (matches.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Match> e : new ArrayList<>(matches.entrySet())) {
            Match m = e.getValue();
            try {
                m.tick();
            } catch (RuntimeException ex) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error in match on " + e.getKey() + ", ending it", ex);
                m.end("The match was stopped because of an internal error.");
            }
            if (m.ended()) {
                matches.remove(e.getKey());
            }
        }
    }

    public void leave(Player p, boolean quit) {
        leaveLobby(p);
        Match m = byPlayer(p);
        if (m != null) {
            m.removePlayer(p, quit);
        }
    }

    public void shutdown() {
        for (Match m : new ArrayList<>(matches.values())) {
            m.end("The server is reloading.");
        }
        matches.clear();
        lobbies.clear();
    }
}
