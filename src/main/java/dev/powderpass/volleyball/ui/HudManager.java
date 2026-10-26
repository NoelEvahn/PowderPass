package dev.powderpass.volleyball.ui;

import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.game.Match;
import dev.powderpass.volleyball.game.MatchState;
import dev.powderpass.volleyball.game.Positions;
import dev.powderpass.volleyball.game.Team;
import dev.powderpass.volleyball.game.TeamId;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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

/** Per-player sidebar and per-match boss bar. */
public final class HudManager {

    private static final int LINES = 12;

    private final PowderPassPlugin plugin;
    private final Map<UUID, Attached> attached = new HashMap<>();
    private final Map<Match, BossBar> bars = new HashMap<>();
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
        if (plugin.settings().hud.bossbar()) {
            p.showBossBar(bar(m));
        }
        update(p, attached.get(p.getUniqueId()));
    }

    public void detach(Player p) {
        Attached a = attached.remove(p.getUniqueId());
        if (a == null) {
            return;
        }
        BossBar bar = bars.get(a.match);
        if (bar != null) {
            p.hideBossBar(bar);
        }
        if (a.board != null && p.getScoreboard() == a.board) {
            p.setScoreboard(a.previous != null ? a.previous : Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private static Component title() {
        return Component.text("🏐 ", NamedTextColor.GOLD)
                .append(Component.text("VOLLEYBALL", NamedTextColor.YELLOW, TextDecoration.BOLD));
    }

    private BossBar bar(Match m) {
        return bars.computeIfAbsent(m, k -> BossBar.bossBar(Component.empty(), 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS));
    }

    public void tick() {
        tick++;
        // bars every tick for a smooth serve clock
        bars.entrySet().removeIf(e -> {
            Match m = e.getKey();
            if (m.ended()) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.hideBossBar(e.getValue());
                }
                return true;
            }
            updateBar(m, e.getValue());
            return false;
        });
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

    private void updateBar(Match m, BossBar bar) {
        MatchState st = m.state();
        Team serving = m.team(m.score().serving());
        Component name;
        float progress = 1f;
        BossBar.Color color = barColor(serving.color());
        switch (st) {
            case SERVE -> {
                Player sp = m.server() == null ? null : Bukkit.getPlayer(m.server());
                name = Component.text("Serve ", NamedTextColor.WHITE, TextDecoration.BOLD)
                        .append(Component.text(sp == null ? "" : sp.getName(), serving.color()))
                        .append(Component.text(String.format("  %.1fs", m.secondsLeft()), NamedTextColor.GRAY));
                progress = (float) m.clockProgress();
            }
            case RALLY -> {
                TeamId t = m.lastTouchTeam();
                Component touches = t == null ? Component.empty()
                        : Component.text("  " + dots(m.teamContacts()), m.team(t).color());
                name = Component.text("Rally", NamedTextColor.WHITE, TextDecoration.BOLD)
                        .append(Component.text("  touches " + m.rallyTouches(), NamedTextColor.GRAY)).append(touches);
                color = t == null ? color : barColor(m.team(t).color());
            }
            case TIMEOUT -> {
                name = Component.text(String.format("Timeout  %.0fs", m.secondsLeft()), NamedTextColor.AQUA, TextDecoration.BOLD);
                progress = (float) m.clockProgress();
            }
            case COUNTDOWN -> name = Component.text(String.format("Starting in %.0f", Math.ceil(m.secondsLeft())),
                    NamedTextColor.YELLOW, TextDecoration.BOLD);
            case POINT_END -> name = Component.text(m.lastPointText(), NamedTextColor.GRAY);
            case PAUSED -> name = Component.text("Paused by the referee", NamedTextColor.YELLOW, TextDecoration.BOLD);
            case FINISHED -> name = Component.text("Match over", NamedTextColor.GOLD, TextDecoration.BOLD);
            default -> name = Component.text("Get ready · ", NamedTextColor.WHITE)
                    .append(Component.text(serving.name() + " serves", serving.color()));
        }
        bar.name(name);
        bar.progress(Math.max(0f, Math.min(1f, progress)));
        bar.color(color);
    }

    private static String dots(int contacts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 3; i++) {
            sb.append(i <= contacts ? '●' : '○');
        }
        return sb.toString();
    }

    private static BossBar.Color barColor(TextColor c) {
        int r = c.red();
        int g = c.green();
        int b = c.blue();
        if (r > 200 && g < 120 && b < 120) {
            return BossBar.Color.RED;
        }
        if (b > 180 && r < 150) {
            return BossBar.Color.BLUE;
        }
        if (g > 180 && r < 150 && b < 150) {
            return BossBar.Color.GREEN;
        }
        if (r > 200 && g > 180 && b < 120) {
            return BossBar.Color.YELLOW;
        }
        if (r > 150 && b > 150 && g < 120) {
            return BossBar.Color.PURPLE;
        }
        if (r > 200 && g > 100 && b > 150) {
            return BossBar.Color.PINK;
        }
        return BossBar.Color.WHITE;
    }

    private void update(Player p, Attached a) {
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
        for (BossBar bar : bars.values()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
        }
        bars.clear();
    }
}
