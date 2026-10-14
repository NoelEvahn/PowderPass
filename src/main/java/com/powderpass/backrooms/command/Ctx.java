package com.powderpass.backrooms.command;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.config.BackroomsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** One command invocation: who ran it, the node reached, the remaining arguments, and the shared helpers. */
final class Ctx {
    static final List<String> SELECTORS = List.of("@a", "@s", "@p", "@r");
    static final List<String> LEVEL_ARGS = Stream.of(Level.values()).map(Level::arg).toList();

    final BackroomsPlugin plugin;
    final CommandSender sender;
    final String label;
    final Node node;
    final String[] args;
    private final BackroomsCommand owner;

    Ctx(BackroomsCommand owner, BackroomsPlugin plugin, CommandSender sender, String label, Node node, String[] args) {
        this.owner = owner;
        this.plugin = plugin;
        this.sender = sender;
        this.label = label;
        this.node = node;
        this.args = args;
    }

    BackroomsConfig cfg() {
        return plugin.cfg();
    }

    boolean has(int i) {
        return i < args.length;
    }

    String arg(int i) {
        return i < args.length ? args[i] : null;
    }

    String lower(int i) {
        return i < args.length ? args[i].toLowerCase(Locale.ROOT) : null;
    }

    int intArg(int i, int def) {
        if (!has(i)) return def;
        try {
            return Integer.parseInt(args[i]);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    double doubleArg(int i, double def) {
        if (!has(i)) return def;
        try {
            return Double.parseDouble(args[i]);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static boolean isInt(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    void send(String mini) {
        sender.sendMessage(BackroomsConfig.mm(mini));
    }

    void msg(String key, String... kv) {
        sender.sendMessage(cfg().msg(key, kv));
    }

    void usage() {
        send("<red>Usage: /" + label + " " + node.path() + (node.usage.isEmpty() ? "" : " " + node.usage));
    }

    boolean need(String perm) {
        if (sender.hasPermission(perm)) return true;
        msg("no-permission");
        return false;
    }

    /** The sender as a player, or null after telling them this needs a player. */
    Player self() {
        if (sender instanceof Player p) return p;
        msg("player-only");
        return null;
    }

    /**
     * Players named by argument {@code i}: a name, or a vanilla selector ({@code @a @s @p @r}, with filters such as
     * {@code @a[distance=..20]}). Missing = the sender. Acting on anyone but yourself needs {@code otherPerm} (null =
     * no extra permission). Returns null after explaining the problem.
     */
    List<Player> targets(int i, String otherPerm) {
        List<Player> out;
        if (!has(i)) {
            Player p = self();
            if (p == null) return null;
            out = List.of(p);
        } else if (args[i].startsWith("@")) {
            try {
                out = new ArrayList<>();
                for (Entity e : Bukkit.selectEntities(sender, args[i])) if (e instanceof Player p) out.add(p);
            } catch (IllegalArgumentException ex) {
                send("<red>Bad selector " + args[i] + ": " + ex.getMessage());
                return null;
            }
            if (out.isEmpty()) {
                send("<red>No player matches " + args[i] + ".");
                return null;
            }
        } else {
            Player p = Bukkit.getPlayerExact(args[i]);
            if (p == null) {
                msg("player-not-found", "name", args[i]);
                return null;
            }
            out = List.of(p);
        }
        boolean onlySelf = out.size() == 1 && out.get(0) == sender;
        if (otherPerm != null && !onlySelf && !need(otherPerm)) return null;
        return out;
    }

    /** Does argument {@code i} look like a player target rather than something else (a level, a number)? */
    boolean looksLikeTarget(int i) {
        return has(i) && (args[i].startsWith("@") || Bukkit.getPlayerExact(args[i]) != null);
    }

    Level level(int i) {
        if (!has(i)) return null;
        Level l = Level.parse(args[i]);
        if (l == null) msg("unknown-level", "level", args[i]);
        return l;
    }

    /** A coordinate token: a number, {@code ~} or {@code ~offset} relative to {@code base}. */
    static Double coord(String s, double base) {
        try {
            if (s.startsWith("~")) return base + (s.length() == 1 ? 0 : Double.parseDouble(s.substring(1)));
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Where the sender stands, or null for the console. */
    Location here() {
        if (sender instanceof Entity e) return e.getLocation();
        if (sender instanceof org.bukkit.command.BlockCommandSender b) return b.getBlock().getLocation().add(0.5, 1, 0.5);
        return null;
    }

    void ask(String what, Runnable action) {
        owner.ask(sender, what, action);
    }

    void confirm() {
        owner.confirm(this);
    }

    boolean hasPending() {
        return owner.hasPending(sender);
    }

    // ------------------------------------------------------------------ completion helpers

    static List<String> players() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    static List<String> targetSuggestions() {
        List<String> out = new ArrayList<>(SELECTORS);
        out.addAll(players());
        return out;
    }
}
