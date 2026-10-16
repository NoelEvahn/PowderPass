package com.powderpass.backrooms.command;

import com.powderpass.backrooms.BackroomsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /backrooms: a tree of command groups ({@link Node}). Every group follows the same shape (a {@code list}, then
 * actions), every command that acts on players takes a name or a selector ({@code @a @s @p @r}), and help and tab
 * completion come from the tree. Old names (level, setspawn, entity, audio test, skinwalker) still work as hidden
 * links for one release.
 */
public final class BackroomsCommand implements TabExecutor {
    private final BackroomsPlugin plugin;
    private final Node root = new Node("backrooms", null);
    private final Map<String, Pending> pending = new HashMap<>();

    private record Pending(Runnable action, long expires) {
    }

    public BackroomsCommand(BackroomsPlugin plugin) {
        this.plugin = plugin;
        root.sub("help").usage("[group]").desc("this list, or every command of one group").run(this::help)
                .complete((c, i) -> i == 0 ? root.visibleChildren(c.sender).stream().filter(Node::group).map(n -> n.name).toList() : List.of());
        PlayerCommands.register(root);
        AudioCommands.register(root);
        AssetCommands.register(root);
        SlideCommands.register(root);
        CreatureCommands.register(root);
        AdminCommands.register(root);
    }

    // ------------------------------------------------------------------ dispatch

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        Node node = root;
        int i = 0;
        while (i < a.length) {
            Node next = node.child(a[i]);
            if (next == null) break;
            node = next;
            i++;
        }
        if (!node.allowed(s)) {
            s.sendMessage(plugin.cfg().msg("no-permission"));
            return true;
        }
        Ctx c = new Ctx(this, plugin, s, label, node, Arrays.copyOfRange(a, i, a.length));
        if (node == root || node.handler == null) {
            if (i < a.length) c.send("<red>Unknown command: <white>" + a[i] + "</white>.");
            if (node == root) rootHelp(c);
            else groupHelp(c, node);
            return true;
        }
        try {
            node.handler.run(c);
        } catch (RuntimeException ex) {
            c.send("<red>That failed: " + ex.getMessage());
            plugin.getLogger().warning("/" + label + " " + String.join(" ", a) + " failed: " + ex);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        Node node = root;
        int i = 0;
        while (i < a.length - 1) {
            Node next = node.child(a[i]);
            if (next == null) break;
            node = next;
            i++;
        }
        if (!node.allowed(s)) return List.of();
        List<String> out = new ArrayList<>();
        int index = a.length - 1 - i;
        if (index == 0 && node.group()) {
            for (Node n : node.visibleChildren(s)) out.add(n.name);
        } else if (node.completer != null) {
            out.addAll(node.completer.complete(new Ctx(this, plugin, s, alias, node, Arrays.copyOfRange(a, i, a.length)), index));
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().distinct().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }

    // ------------------------------------------------------------------ help

    private void help(Ctx c) {
        if (c.has(0)) {
            Node g = root.child(c.arg(0));
            if (g != null && g.group() && g.allowed(c.sender)) {
                groupHelp(c, g);
                return;
            }
        }
        rootHelp(c);
    }

    private void rootHelp(Ctx c) {
        c.send("<#d8c46a><bold>Backrooms</bold> <gray>— five places, no map. <dark_gray>Targets: a name or @a @s @p @r.");
        for (Node n : root.visibleChildren(c.sender)) {
            if (n.name.equals("help")) continue;
            if (n.group()) {
                String subs = String.join("|", n.visibleChildren(c.sender).stream().map(x -> x.name).toList());
                c.send("<white>/" + c.label + " " + n.name + " <" + subs + "></white> <gray>" + n.desc);
            } else {
                c.send(line(c, n));
            }
        }
        c.send("<dark_gray>/" + c.label + " help <group> for the details of a group.");
    }

    private void groupHelp(Ctx c, Node g) {
        c.send("<#d8c46a><bold>/" + c.label + " " + g.path() + "</bold> <gray>" + g.desc);
        for (Node n : g.visibleChildren(c.sender)) c.send(line(c, n));
    }

    private static String line(Ctx c, Node n) {
        return "<white>/" + c.label + " " + n.path() + (n.usage.isEmpty() ? "" : " " + n.usage) + "</white> <gray>" + n.desc;
    }

    // ------------------------------------------------------------------ confirmations

    void ask(CommandSender s, String what, Runnable action) {
        pending.put(s.getName(), new Pending(action, System.currentTimeMillis() + 15_000));
        s.sendMessage(plugin.cfg().msg("confirm", "action", what));
    }

    void confirm(Ctx c) {
        Pending p = pending.remove(c.sender.getName());
        if (p == null || p.expires < System.currentTimeMillis()) {
            c.msg("nothing-to-confirm");
            return;
        }
        p.action.run();
    }

    boolean hasPending(CommandSender s) {
        return pending.containsKey(s.getName());
    }

    Node root() {
        return root;
    }
}
