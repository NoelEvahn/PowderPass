package com.powderpass.backrooms.command;

import com.powderpass.backrooms.Level;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

/** enter, leave, goto, levels: getting in and out, and moving between levels. */
final class PlayerCommands {
    private PlayerCommands() {
    }

    static void register(Node root) {
        root.sub("enter").perm("backrooms.use").usage("[targets]").desc("step out of reality into Level 0")
                .run(PlayerCommands::enter).complete(PlayerCommands::adminTargets);
        root.sub("leave").perm("backrooms.use").usage("[targets]").desc("return to where you came from")
                .run(PlayerCommands::leave).complete(PlayerCommands::adminTargets);
        Node go = root.sub("goto").perm("backrooms.admin.teleport").usage("<level> [targets]").desc("send to a level")
                .run(PlayerCommands::go)
                .complete((c, i) -> i == 0 ? Ctx.LEVEL_ARGS : i == 1 ? Ctx.targetSuggestions() : List.of());
        root.sub("levels").perm("backrooms.use").desc("the five levels and whether they are open").run(PlayerCommands::levels);
        root.link("level", go); // old name
    }

    private static List<String> adminTargets(Ctx c, int i) {
        return i == 0 && c.sender.hasPermission("backrooms.admin.teleport") ? Ctx.targetSuggestions() : List.of();
    }

    private static void enter(Ctx c) {
        List<Player> ts = c.targets(0, "backrooms.admin.teleport");
        if (ts == null) return;
        boolean admin = c.sender.hasPermission("backrooms.admin.teleport");
        for (Player p : ts) {
            if (!admin) {
                if (!c.cfg().allowSelfEnter) {
                    c.msg("no-permission");
                    return;
                }
                if (!c.cfg().allowedWorlds.isEmpty() && !c.cfg().allowedWorlds.contains(p.getWorld().getName()) && !c.plugin.worlds().isBackrooms(p.getWorld())) {
                    c.msg("enter-world-denied");
                    return;
                }
                if (c.plugin.worlds().isBackrooms(p.getWorld())) {
                    c.msg("already-inside", "name", p.getName());
                    return;
                }
            }
            c.plugin.sessions().enter(p, c.sender);
        }
    }

    private static void leave(Ctx c) {
        List<Player> ts = c.targets(0, "backrooms.admin.teleport");
        if (ts == null) return;
        if (!c.sender.hasPermission("backrooms.admin.teleport") && !c.cfg().allowSelfLeave) {
            c.msg("self-leave-denied");
            return;
        }
        for (Player p : ts) {
            if (!c.plugin.worlds().isBackrooms(p.getWorld())) {
                if (ts.size() == 1) c.msg("not-inside", "name", p.getName());
                continue;
            }
            c.plugin.sessions().leave(p, c.sender);
        }
    }

    private static void go(Ctx c) {
        if (!c.has(0)) {
            c.usage();
            return;
        }
        Level l = c.level(0);
        if (l == null) return;
        if (!c.plugin.worlds().available(l)) {
            c.msg("level-disabled", "level", l.displayName());
            return;
        }
        List<Player> ts = c.targets(1, null);
        if (ts == null) return;
        for (Player p : ts) c.plugin.sessions().toLevel(p, l);
        if (ts.size() > 1 || ts.get(0) != c.sender) c.send("<green>Sent " + ts.size() + " player(s) to " + l.displayName() + ".");
    }

    private static void levels(Ctx c) {
        c.send("<#d8c46a><bold>The levels</bold>");
        for (Level l : Level.values()) {
            World w = c.plugin.worlds().world(l);
            String state = w == null ? "<red>disabled" : "<green>open</green> <gray>(" + w.getPlayers().size() + " inside)";
            c.send(l.color() + "■ <white>" + l.displayName() + "</white> <gray>— " + l.subtitle() + " " + state
                    + (c.sender.hasPermission("backrooms.admin") ? " <dark_gray>[" + l.arg() + "]" : ""));
        }
    }
}
