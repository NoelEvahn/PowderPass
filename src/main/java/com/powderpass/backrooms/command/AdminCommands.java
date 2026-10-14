package com.powderpass.backrooms.command;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.event.EventType;
import com.powderpass.backrooms.gen.LevelGenerator;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** event, spawnpoint, give, status, debug, reload, confirm. */
final class AdminCommands {
    private static final List<String> ITEMS = List.of("almond_water", "flashlight", "battery", "ration", "note");

    private AdminCommands() {
    }

    static void register(Node root) {
        Node ev = root.sub("event").perm("backrooms.admin.events").desc("environmental events");
        ev.sub("list").desc("every event and where it happens").run(AdminCommands::eventList);
        ev.sub("trigger").usage("<event> [targets | level]").desc("for players, or for everyone in a level")
                .run(AdminCommands::eventTrigger)
                .complete((c, i) -> {
                    if (i == 0) return Stream.of(EventType.values()).map(t -> t.id).toList();
                    if (i != 1) return List.of();
                    List<String> out = new ArrayList<>(Ctx.targetSuggestions());
                    out.addAll(Ctx.LEVEL_ARGS);
                    return out;
                });

        Node sp = root.sub("spawnpoint").perm("backrooms.admin.teleport").desc("where goto lands in each level");
        Node set = sp.sub("set").usage("[level]").desc("here (must be a safe spot inside that level)").run(AdminCommands::spawnSet)
                .complete((c, i) -> i == 0 ? Ctx.LEVEL_ARGS : List.of());
        sp.sub("clear").usage("<level>").desc("back to the safe cell nearest the origin").run(AdminCommands::spawnClear)
                .complete((c, i) -> i == 0 ? Ctx.LEVEL_ARGS : List.of());
        sp.sub("show").usage("[level]").desc("the saved spawn points").run(AdminCommands::spawnShow)
                .complete((c, i) -> i == 0 ? Ctx.LEVEL_ARGS : List.of());
        root.link("setspawn", set); // old name

        root.sub("give").perm("backrooms.admin").usage("<item> [targets] [amount]").desc("Backrooms items")
                .run(AdminCommands::give)
                .complete((c, i) -> i == 0 ? ITEMS : i == 1 ? Ctx.targetSuggestions() : i == 2 ? List.of("1", "4", "16") : List.of());
        root.sub("status").perm("backrooms.admin.debug").desc("worlds, players, creatures, displays, tick cost").run(AdminCommands::status);
        root.sub("debug").perm("backrooms.admin.debug").usage("[on|off]").desc("action-bar overlay and console log")
                .run(AdminCommands::debug).complete((c, i) -> i == 0 ? List.of("on", "off") : List.of());
        root.sub("reload").perm("backrooms.admin.reload").desc("reload config.yml (locked generation values stay)")
                .run(c -> {
                    c.plugin.reload();
                    c.msg("reloaded");
                });
        root.sub("confirm").desc("confirm the last destructive command (15 s)").run(Ctx::confirm);
    }

    // ------------------------------------------------------------------ events

    private static void eventList(Ctx c) {
        c.send("<#d8c46a><bold>Environmental events");
        for (EventType t : EventType.values()) {
            c.send("<white>" + t.id + "</white> <gray>" + t.description + " <dark_gray>" + t.levels.stream().map(Level::arg).collect(Collectors.joining(",")));
        }
    }

    private static void eventTrigger(Ctx c) {
        if (!c.has(0)) {
            c.usage();
            return;
        }
        EventType t = EventType.parse(c.arg(0));
        if (t == null) {
            c.send("<red>Unknown event. Try /" + c.label + " event list");
            return;
        }
        List<Player> targets;
        if (c.has(1) && !c.looksLikeTarget(1)) {
            Level l = c.level(1);
            if (l == null) return;
            if (c.plugin.worlds().world(l) == null) {
                c.msg("level-disabled", "level", l.displayName());
                return;
            }
            targets = c.plugin.worlds().world(l).getPlayers();
        } else {
            targets = c.targets(1, null);
            if (targets == null) return;
        }
        int n = 0;
        for (Player p : targets) if (c.plugin.environment().trigger(t, p)) n++;
        c.send(n > 0 ? "<green>Triggered " + t.id + " for " + n + " player(s)." : "<gray>No player inside a Backrooms level to trigger it for.");
        if (!t.levels.isEmpty() && targets.stream().anyMatch(p -> !t.levels.contains(c.plugin.worlds().levelOf(p.getWorld())))) {
            c.send("<dark_gray>(Note: " + t.id + " does not normally occur in this level.)");
        }
    }

    // ------------------------------------------------------------------ spawn points

    private static void spawnSet(Ctx c) {
        Player p = c.self();
        if (p == null) return;
        Level l = c.has(0) ? c.level(0) : c.plugin.worlds().levelOf(p.getWorld());
        if (l == null) {
            if (!c.has(0)) c.send("<red>Stand inside a level, or name it.");
            return;
        }
        if (c.plugin.worlds().levelOf(p.getWorld()) != l) {
            c.send("<red>Stand inside " + l.displayName() + " to set its spawn.");
            return;
        }
        if (!c.plugin.teleports().isSafe(p.getLocation().getBlock())) {
            c.send("<red>This spot is not safe (needs two free blocks over a solid, harmless floor).");
            return;
        }
        Location loc = p.getLocation();
        c.plugin.data().setSpawn(l, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        c.plugin.data().saveAsync(c.plugin);
        c.msg("spawn-set", "level", l.displayName(), "x", String.valueOf(loc.getBlockX()), "y", String.valueOf(loc.getBlockY()),
                "z", String.valueOf(loc.getBlockZ()));
    }

    private static void spawnClear(Ctx c) {
        if (!c.has(0)) {
            c.usage();
            return;
        }
        Level l = c.level(0);
        if (l == null) return;
        c.plugin.data().clearSpawn(l);
        c.plugin.data().saveAsync(c.plugin);
        c.send("<green>Spawn point of " + l.displayName() + " cleared.");
    }

    private static void spawnShow(Ctx c) {
        List<Level> levels = c.has(0) ? (c.level(0) == null ? List.of() : List.of(Level.parse(c.arg(0)))) : List.of(Level.values());
        for (Level l : levels) {
            double[] s = c.plugin.data().spawn(l);
            c.send("<white>" + l.displayName() + "</white> <gray>" + (s == null ? "not set (safe cell nearest the origin)"
                    : String.format(Locale.ROOT, "%.1f, %.1f, %.1f", s[0], s[1], s[2])));
        }
    }

    // ------------------------------------------------------------------ items, status, debug

    private static void give(Ctx c) {
        if (!c.has(0)) {
            c.usage();
            return;
        }
        String id = c.lower(0);
        if (!ITEMS.contains(id)) {
            c.send("<red>Unknown item. Items: " + String.join(", ", ITEMS));
            return;
        }
        List<Player> ts = c.targets(1, null);
        if (ts == null) return;
        int amount = Math.max(1, Math.min(64, c.intArg(2, 1)));
        for (Player p : ts) {
            for (int k = 0; k < amount; k++) {
                Level lvl = c.plugin.worlds().levelOf(p.getWorld());
                ItemStack it = switch (id) {
                    case "almond_water" -> c.plugin.items().almondWater();
                    case "flashlight" -> c.plugin.items().flashlight(c.cfg().flashlightSeconds);
                    case "battery" -> c.plugin.items().battery();
                    case "ration" -> c.plugin.items().ration();
                    default -> c.plugin.items().note(lvl == null ? Level.LEVEL_0 : lvl);
                };
                p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
            }
        }
        c.send("<green>Gave " + amount + " " + id + " to " + (ts.size() == 1 ? ts.get(0).getName() : ts.size() + " players") + ".");
    }

    private static void status(Ctx c) {
        c.send("<#d8c46a><bold>Backrooms status</bold> <gray>seed " + c.plugin.data().baseSeed());
        for (Level l : Level.values()) {
            World w = c.plugin.worlds().world(l);
            if (w == null) {
                c.send("<gray>" + l.displayName() + ": <red>not loaded");
                continue;
            }
            LevelGenerator g = c.plugin.worlds().generator(l);
            c.send(l.color() + l.displayName() + "</" + l.color().substring(1) + " <gray>world=<white>" + w.getName()
                    + "</white> players=<white>" + w.getPlayers().size() + "</white> chunks=<white>" + w.getLoadedChunks().length
                    + "</white> creatures=<white>" + c.plugin.creatures().countLevel(w) + "</white> displays=<white>" + c.plugin.decorations().entityCount(w)
                    + "</white> (" + c.plugin.decorations().activeChunks(w) + " chunks) <dark_gray>cell=" + g.S + " region=" + g.R + " floors=" + g.floorCount());
        }
        String types = c.plugin.creatures().counts().entrySet().stream().map(e -> e.getKey().id + "=" + e.getValue()).collect(Collectors.joining(", "));
        c.send("<gray>Active threats: <white>" + (types.isEmpty() ? "none" : types));
        c.send("<gray>Placed assets: <white>" + c.plugin.assets().count() + "</white>, riders on slides: <white>" + c.plugin.slides().riders());
        c.send(String.format(Locale.ROOT, "<gray>Tick cost (avg): decorations <white>%.2f ms</white>, creatures <white>%.2f ms</white>",
                c.plugin.decorations().costMillis(), c.plugin.creatures().costMillis()));
    }

    private static void debug(Ctx c) {
        boolean on = c.has(0) ? c.lower(0).equals("on") : !(c.sender instanceof Player p && c.plugin.sessions().debug(p));
        if (c.sender instanceof Player p) c.plugin.sessions().setDebug(p, on);
        c.plugin.setConsoleDebug(on);
        c.send("<gray>Backrooms debug " + (on ? "<green>on" : "<red>off") + (c.sender instanceof Player ? " <dark_gray>(action bar + console log)" : " <dark_gray>(console log)"));
    }
}
