package dev.powderpass.volleyball.ui;

import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.game.Match;
import dev.powderpass.volleyball.game.MatchState;
import dev.powderpass.volleyball.game.Positions;
import dev.powderpass.volleyball.game.Team;
import dev.powderpass.volleyball.game.TeamId;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-player sidebar and name tags. */
public final class HudManager {

    private static final int LINES = 13;

    private final PowderPassPlugin plugin;
    private final Map<UUID, Attached> attached = new HashMap<>();
    private long tick;

    private static final class Attached {
        final Match match;
        final Scoreboard board;
        final Objective objective;
        final Scoreboard previous;
        final Component[] shown = new Component[LINES];

        Attached(Match match, Scoreboard board, Objective objective, Scoreboard previous) {
            this.match = match;
            this.board = board;
            this.objective = objective;
            this.previous = previous;
        }
    }

    public HudManager(PowderPassPlugin plugin) {
        this.plugin = plugin;
    }

    public void attach(Player p, Match m) {
        detach(p);
        Scoreboard previous = p.getScoreboard();
        Scoreboard board = null;
        Objective obj = null;
        if (plugin.settings().hud.sidebar()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            obj = board.registerNewObjective("powderpass", Criteria.DUMMY, title());
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(NumberFormat.blank());
            p.setScoreboard(board);
        }
        attached.put(p.getUniqueId(), new Attached(m, board, obj, previous));
        update(p, attached.get(p.getUniqueId()));
    }

    public void detach(Player p) {
        Attached a = attached.remove(p.getUniqueId());
        if (a == null) {
            return;
        }
        if (a.board != null && p.getScoreboard() == a.board) {
            p.setScoreboard(a.previous != null ? a.previous : Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private static Component title() {
        return Component.text("🏐 ", NamedTextColor.GOLD)
                .append(Component.text("VOLLEYBALL", NamedTextColor.YELLOW, TextDecoration.BOLD));
    }

    public void tick() {
        tick++;
        if (tick % plugin.settings().hud.sidebarUpdateTicks() != 0) {
            return;
        }
        for (Map.Entry<UUID, Attached> e : new ArrayList<>(attached.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            Attached a = e.getValue();
            if (p == null || a.match.ended() || !a.match.contains(e.getKey())) {
                if (p != null) {
                    detach(p);
                } else {
                    attached.remove(e.getKey());
                }
                continue;
            }
            update(p, a);
        }
    }

    private static String dots(int contacts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 3; i++) {
            sb.append(i <= contacts ? '●' : '○');
        }
        return sb.toString();
    }

    /**
     * Name tags: every match player gets a scoreboard team on each player's board, whose prefix shows their
     * rotation position and role in the team colour. Collisions are off so players don't push each other.
     */
    private void updateTags(Attached a) {
        if (a.board == null) {
            return;
        }
        Match m = a.match;
        for (Team t : m.teams()) {
            NamedTextColor nc = NamedTextColor.nearestTo(t.color());
            for (Player q : t.online()) {
                String name = "vb" + q.getUniqueId().toString().substring(0, 12);
                org.bukkit.scoreboard.Team st = a.board.getTeam(name);
                if (st == null) {
                    st = a.board.registerNewTeam(name);
                    st.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
                    st.setOption(org.bukkit.scoreboard.Team.Option.NAME_TAG_VISIBILITY, org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);
                    st.color(nc);
                    st.addEntry(q.getName());
                }
                int pos = m.positionOf(q.getUniqueId());
                String label = pos > 0 ? "P" + pos : "";
                Component prefix = Component.text(label + " ", t.color(), TextDecoration.BOLD);
                if (!prefix.equals(st.prefix())) {
                    st.prefix(prefix);
                }
            }
        }
    }

    private void update(Player p, Attached a) {
        updateTags(a);
        if (a.objective == null) {
            return;
        }
        Match m = a.match;
        List<Component> lines = new ArrayList<>();
        int set = m.score().setNumber();
        lines.add(Component.text(m.court().name() + " · Set " + set + " (to " + m.score().targetPoints() + ")", NamedTextColor.GRAY));
        lines.add(Component.empty());
        for (TeamId id : TeamId.values()) {
            Team t = m.team(id);
            boolean serving = m.score().serving() == id;
            lines.add(Component.text(serving ? "● " : "  ", NamedTextColor.GOLD)
                    .append(Component.text(pad(t.name(), 8), t.color(), TextDecoration.BOLD))
                    .append(Component.text(" " + m.score().sets(id) + " ", NamedTextColor.GRAY))
                    .append(Component.text("│ ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(m.score().points(id), NamedTextColor.WHITE, TextDecoration.BOLD)));
        }
        lines.add(Component.text(" "));
        Team mine = m.teamOf(p.getUniqueId());
        if (mine != null) {
            int pos = m.positionOf(p.getUniqueId());
            lines.add(Component.text("You: ", NamedTextColor.GRAY)
                    .append(Component.text(Positions.label(mine.size(), pos), mine.color())));
        }
        Player server = m.server() == null ? null : Bukkit.getPlayer(m.server());
        if (server != null) {
            lines.add(Component.text("Server: ", NamedTextColor.GRAY).append(Component.text(server.getName(), NamedTextColor.WHITE)));
        }
        if (m.state() == MatchState.RALLY && m.lastTouchTeam() != null) {
            lines.add(Component.text("Touches: ", NamedTextColor.GRAY)
                    .append(Component.text(dots(m.teamContacts()), m.team(m.lastTouchTeam()).color())));
        } else {
            lines.add(Component.text("State: ", NamedTextColor.GRAY).append(Component.text(m.state().label(), NamedTextColor.WHITE)));
        }
        if (!m.score().history().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int[] h : m.score().history()) {
                sb.append(h[0]).append('-').append(h[1]).append("  ");
            }
            lines.add(Component.text("Sets: " + sb.toString().trim(), NamedTextColor.DARK_GRAY));
        }
        lines.add(Component.text("  "));
        lines.add(Component.text(m.settings().describe(), NamedTextColor.DARK_GRAY));
        for (int i = 0; i < LINES; i++) {
            String entry = "l" + i;
            if (i < lines.size()) {
                Component line = lines.get(i);
                if (line.equals(a.shown[i])) {
                    continue;
                }
                var score = a.objective.getScore(entry);
                score.setScore(LINES - i);
                score.customName(line);
                a.shown[i] = line;
            } else if (a.shown[i] != null) {
                a.board.resetScores(entry);
                a.shown[i] = null;
            }
        }
    }

    private static String pad(String s, int n) {
        if (s.length() >= n) {
            return s.substring(0, n);
        }
        return s + " ".repeat(n - s.length());
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(attached.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                detach(p);
            }
        }
        attached.clear();
    }
}
