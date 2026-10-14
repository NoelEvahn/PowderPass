package com.powderpass.backrooms.command;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One word of {@code /backrooms}: either a group (has children, e.g. {@code audio}) or a leaf that runs (e.g.
 * {@code audio play}). Help text and tab completion are generated from the tree, so a command only has to be
 * declared once. Old command names are kept as hidden {@link #link links} to their new nodes.
 */
final class Node {
    interface Handler {
        void run(Ctx c);
    }

    /** Suggestions for the argument at {@code index} (0 = first argument after this node's path). */
    interface Completer {
        List<String> complete(Ctx c, int index);
    }

    final String name;
    final Node parent;
    String perm, usage = "", desc = "";
    boolean hidden;
    Handler handler;
    Completer completer;
    final Map<String, Node> children = new LinkedHashMap<>();
    private final Map<String, Node> links = new LinkedHashMap<>();

    Node(String name, Node parent) {
        this.name = name;
        this.parent = parent;
    }

    Node sub(String name) {
        Node n = new Node(name, this);
        children.put(name, n);
        return n;
    }

    Node perm(String p) {
        perm = p;
        return this;
    }

    Node usage(String u) {
        usage = u;
        return this;
    }

    Node desc(String d) {
        desc = d;
        return this;
    }

    Node hidden() {
        hidden = true;
        return this;
    }

    Node run(Handler h) {
        handler = h;
        return this;
    }

    Node complete(Completer c) {
        completer = c;
        return this;
    }

    /** An old or alternative word that leads to {@code target}; never shown in help or completion. */
    Node link(String word, Node target) {
        links.put(word.toLowerCase(Locale.ROOT), target);
        return this;
    }

    Node child(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        Node n = children.get(w);
        return n != null ? n : links.get(w);
    }

    boolean group() {
        return !children.isEmpty();
    }

    /** Words from the root, e.g. "audio play". */
    String path() {
        if (parent == null) return "";
        String p = parent.path();
        return p.isEmpty() ? name : p + " " + name;
    }

    /** This node and every parent grant the sender access. */
    boolean allowed(CommandSender s) {
        for (Node n = this; n != null; n = n.parent) if (n.perm != null && !s.hasPermission(n.perm)) return false;
        return true;
    }

    List<Node> visibleChildren(CommandSender s) {
        List<Node> out = new ArrayList<>();
        for (Node n : children.values()) if (!n.hidden && n.allowed(s)) out.add(n);
        return out;
    }
}
