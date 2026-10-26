package dev.powderpass.volleyball.ui;

import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.court.Court;
import dev.powderpass.volleyball.game.Match;
import dev.powderpass.volleyball.game.MatchState;
import dev.powderpass.volleyball.game.Team;
import dev.powderpass.volleyball.game.TeamId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** World scoreboards: persistence and live updates from matches. */
public final class ScoreboardManager {

    private final PowderPassPlugin plugin;
    private final File file;
    private final Map<String, WorldScoreboard> boards = new LinkedHashMap<>();
    private long tick;

    public ScoreboardManager(PowderPassPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "scoreboards.yml");
    }

    public void load() {
        boards.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("scoreboards");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) {
                continue;
            }
            Location loc = s.getLocation("location");
            if (loc == null || loc.getWorld() == null) {
                plugin.getLogger().warning("Scoreboard " + name + " has no valid location, skipping.");
                continue;
            }
            Display.Billboard bb;
            try {
                bb = Display.Billboard.valueOf(s.getString("billboard", "FIXED"));
            } catch (IllegalArgumentException e) {
                bb = Display.Billboard.FIXED;
            }
            boards.put(key(name), new WorldScoreboard(name, s.getString("court", ""), loc, (float) s.getDouble("scale", 1.0), bb));
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        ConfigurationSection root = y.createSection("scoreboards");
        for (WorldScoreboard b : boards.values()) {
            ConfigurationSection s = root.createSection(b.name());
            s.set("court", b.court());
            s.set("location", b.location());
            s.set("scale", b.scale());
            s.set("billboard", b.billboard().name());
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save scoreboards.yml", e);
        }
    }

    public void spawnAll() {
        for (WorldScoreboard b : boards.values()) {
            Location l = b.location();
            l.getWorld().addPluginChunkTicket(l.getBlockX() >> 4, l.getBlockZ() >> 4, plugin);
            b.spawn();
        }
        tick = 0;
        update(true);
    }

    public WorldScoreboard create(String name, Court court, Location at, float scale, Display.Billboard billboard) {
        Location loc = at.clone();
        loc.setPitch(0);
        // face the player who placed it: the text faces back toward the creator
        loc.setYaw(Math.round((at.getYaw() + 180) / 90f) * 90f);
        WorldScoreboard b = new WorldScoreboard(name, court.name(), loc, scale, billboard);
        WorldScoreboard old = boards.put(key(name), b);
        if (old != null) {
            old.remove();
        }
        loc.getWorld().addPluginChunkTicket(loc.getBlockX() >> 4, loc.getBlockZ() >> 4, plugin);
        b.spawn();
        save();
        update(true);
        return b;
    }

    public boolean remove(String name) {
        WorldScoreboard b = boards.remove(key(name));
        if (b == null) {
            return false;
        }
        b.remove();
        save();
        return true;
    }

    public WorldScoreboard get(String name) {
        return boards.get(key(name));
    }

    public Collection<WorldScoreboard> all() {
        return boards.values();
    }

    public List<String> names() {
        List<String> l = new ArrayList<>();
        for (WorldScoreboard b : boards.values()) {
            l.add(b.name());
        }
        return l;
    }

    public void respawn(WorldScoreboard b) {
        b.spawn();
        save();
        update(true);
    }

    public void tick() {
        tick++;
        if (tick % 5 == 0) {
            update(false);
        }
    }

    private void update(boolean force) {
        for (WorldScoreboard b : boards.values()) {
            Court c = plugin.courts().get(b.court());
            if (c == null) {
                b.show(Component.text("Scoreboard " + b.name(), NamedTextColor.GRAY),
                        Component.text("--", NamedTextColor.DARK_GRAY),
                        Component.text("Court '" + b.court() + "' not found · /vb scoreboard link", NamedTextColor.RED));
                continue;
            }
            Match m = plugin.matches().byCourt(c);
            if (m == null) {
                showIdle(b, c);
            } else {
                showMatch(b, m);
            }
        }
    }

    private void showIdle(WorldScoreboard b, Court c) {
        Map<UUID, TeamId> lobby = plugin.matches().lobby(c);
        Component header = Component.text("🏐 ", NamedTextColor.GOLD)
                .append(Component.text(c.name().toUpperCase(Locale.ROOT), NamedTextColor.YELLOW, TextDecoration.BOLD));
        Component score = Component.text("0 : 0", NamedTextColor.DARK_GRAY, TextDecoration.BOLD);
        List<String> names = new ArrayList<>();
        for (UUID id : lobby.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                names.add(p.getName());
            }
        }
        Component footer = Component.text("Waiting for players · ", NamedTextColor.GRAY)
                .append(Component.text("/vb join " + c.name(), NamedTextColor.WHITE));
        if (!names.isEmpty()) {
            footer = footer.appendNewline().append(Component.text(names.size() + " in lobby: "
                    + String.join(", ", names.subList(0, Math.min(6, names.size()))), NamedTextColor.GRAY));
        }
        b.show(header, score, footer);
    }

    private void showMatch(WorldScoreboard b, Match m) {
        Team a = m.team(TeamId.A);
        Team bb = m.team(TeamId.B);
        TeamId serving = m.score().serving();
        Component header = Component.text(serving == TeamId.A ? "● " : "  ", NamedTextColor.GOLD)
                .append(Component.text(a.name().toUpperCase(Locale.ROOT), a.color(), TextDecoration.BOLD))
                .append(Component.text("  " + m.score().sets(TeamId.A) + "  ", NamedTextColor.WHITE))
                .append(Component.text("SET " + m.score().setNumber(), NamedTextColor.GRAY))
                .append(Component.text("  " + m.score().sets(TeamId.B) + "  ", NamedTextColor.WHITE))
                .append(Component.text(bb.name().toUpperCase(Locale.ROOT), bb.color(), TextDecoration.BOLD))
                .append(Component.text(serving == TeamId.B ? " ●" : "  ", NamedTextColor.GOLD));
        Component score = Component.text(m.score().points(TeamId.A), a.color(), TextDecoration.BOLD)
                .append(Component.text(" : ", NamedTextColor.WHITE))
                .append(Component.text(m.score().points(TeamId.B), bb.color(), TextDecoration.BOLD));
        Component status;
        if (m.state() == MatchState.FINISHED && m.score().winner() != null) {
            Team w = m.team(m.score().winner());
            status = w.displayName().append(Component.text(" WINS", NamedTextColor.GOLD, TextDecoration.BOLD));
        } else if (m.state() == MatchState.POINT_END) {
            status = Component.text(m.lastPointText(), NamedTextColor.GRAY);
        } else if (m.score().isMatchPoint(serving) || m.score().isMatchPoint(serving.other())) {
            status = Component.text("MATCH POINT", NamedTextColor.GOLD, TextDecoration.BOLD);
        } else if (m.score().isSetPoint(serving) || m.score().isSetPoint(serving.other())) {
            status = Component.text("SET POINT", NamedTextColor.YELLOW, TextDecoration.BOLD);
        } else {
            status = Component.text(m.state().label() + " · " + m.settings().describe(), NamedTextColor.GRAY);
        }
        if (!m.score().history().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int[] h : m.score().history()) {
                sb.append(h[0]).append('-').append(h[1]).append("   ");
            }
            status = status.appendNewline().append(Component.text(sb.toString().trim(), NamedTextColor.DARK_GRAY));
        }
        b.show(header, score, status);
    }

    public void shutdown() {
        for (WorldScoreboard b : boards.values()) {
            b.remove();
        }
    }

    private static String key(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
