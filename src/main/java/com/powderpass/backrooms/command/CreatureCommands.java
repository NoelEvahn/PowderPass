package com.powderpass.backrooms.command;

import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.creature.CreatureType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Stream;

/** creature list | spawn | clear | skinwalker. */
final class CreatureCommands {
    private CreatureCommands() {
    }

    static void register(Node root) {
        Node g = root.sub("creature").perm("backrooms.admin.entities").desc("the things that live here");
        g.sub("list").desc("every creature and anomaly").run(CreatureCommands::list);
        g.sub("spawn").usage("<type> [amount] [targets]").desc("spawn near players, by the natural placement rules")
                .run(CreatureCommands::spawn)
                .complete((c, i) -> switch (i) {
                    case 0 -> Stream.of(CreatureType.values()).map(t -> t.id).toList();
                    case 1 -> List.of("1", "2", "3");
                    case 2 -> Ctx.targetSuggestions();
                    default -> List.of();
                });
        g.sub("clear").usage("[radius | level [level]]").desc("remove creatures around you, or in a whole level")
                .run(CreatureCommands::clear)
                .complete((c, i) -> i == 0 ? List.of("16", "32", "64", "level") : i == 1 && "level".equalsIgnoreCase(c.arg(0)) ? Ctx.LEVEL_ARGS : List.of());
        Node sw = g.sub("skinwalker").usage("<targets>").desc("a skinwalker takes these players now").run(CreatureCommands::skinwalker)
                .complete((c, i) -> i == 0 ? Ctx.targetSuggestions() : List.of());
        sw.sub("remove").usage("<targets>").desc("free these players from a skinwalker (it lets go and is gone)")
                .run(CreatureCommands::skinwalkerRemove)
                .complete((c, i) -> i == 0 ? Ctx.targetSuggestions() : List.of());
        root.link("entity", g); // old name
        root.link("skinwalker", sw); // old name
    }

    private static void list(Ctx c) {
        c.send("<#d8c46a><bold>Creatures & anomalies");
        for (CreatureType t : CreatureType.values()) {
            c.send("<white>" + t.id + "</white> <dark_gray>(" + t.category + ")</dark_gray> <gray>" + t.description);
        }
    }

    private static void spawn(Ctx c) {
        if (!c.has(0)) {
            c.usage();
            return;
        }
        CreatureType t = CreatureType.parse(c.arg(0));
        if (t == null) {
            c.send("<red>Unknown type. Try /" + c.label + " creature list");
            return;
        }
        int amount = Math.max(1, Math.min(10, c.intArg(1, 1)));
        List<Player> ts = c.targets(Ctx.isInt(c.arg(1)) ? 2 : 1, null);
        if (ts == null) return;
        int ok = 0, asked = 0;
        for (Player p : ts) {
            if (!c.plugin.worlds().isBackrooms(p.getWorld())) {
                c.send("<red>" + p.getName() + " is not inside the Backrooms; creatures only exist there.");
                continue;
            }
            for (int i = 0; i < amount; i++) {
                asked++;
                var cr = c.plugin.creatures().spawnNatural(t, p);
                if (cr == null) {
                    Location front = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(4));
                    Location safe = c.plugin.teleports().findSafe(front, 4);
                    if (safe != null && t != CreatureType.DRIFTER) cr = c.plugin.creatures().spawn(t, safe, null);
                }
                if (cr != null) ok++;
            }
        }
        if (asked > 0) c.send("<green>Spawned " + ok + "/" + asked + " " + t.displayName + (ok < asked ? " <gray>(no valid spot for the rest)" : ""));
    }

    private static void clear(Ctx c) {
        String a0 = c.has(0) ? c.lower(0) : "32";
        if (a0.equals("level") || Level.parse(a0) != null && !Ctx.isInt(a0)) {
            Level l = a0.equals("level")
                    ? (c.has(1) ? Level.parse(c.arg(1)) : c.sender instanceof Player p ? c.plugin.worlds().levelOf(p.getWorld()) : null)
                    : Level.parse(a0);
            if (l == null || c.plugin.worlds().world(l) == null) {
                c.send("<red>Usage: /" + c.label + " creature clear level [level]  (stand in the level or name it)");
                return;
            }
            World w = c.plugin.worlds().world(l);
            c.ask("remove every managed creature in " + l.displayName(),
                    () -> c.send("<green>Removed " + c.plugin.creatures().clearWorld(w) + " creatures from " + l.displayName() + "."));
            return;
        }
        Location at = c.here();
        if (at == null) {
            c.send("<red>From the console use: /" + c.label + " creature clear level <level>");
            return;
        }
        int r = Math.max(1, Math.min(256, c.intArg(0, 32)));
        c.send("<green>Removed " + c.plugin.creatures().clearRadius(at, r) + " creatures within " + r + " blocks.");
    }

    private static void skinwalker(Ctx c) {
        if (!c.has(0)) {
            c.usage();
            return;
        }
        List<Player> ts = c.targets(0, null);
        if (ts == null) return;
        for (Player p : ts) {
            if (!c.plugin.worlds().isBackrooms(p.getWorld())) {
                c.send("<red>" + p.getName() + " is not inside the Backrooms; skinwalkers only exist there.");
            } else if (p.getGameMode() == GameMode.SPECTATOR || p.isDead()) {
                c.send("<red>" + p.getName() + " cannot be taken right now (spectator or dead).");
            } else if (!c.plugin.creatures().skinwalkerTakes(p)) {
                c.send("<red>" + p.getName() + " is already being worn, or creatures are disabled.");
            } else {
                c.send("<green>A skinwalker has taken " + p.getName() + ". <gray>Kill it to free them (or /" + c.label + " creature clear).");
            }
        }
    }

    private static void skinwalkerRemove(Ctx c) {
        if (!c.has(0)) {
            c.usage();
            return;
        }
        List<Player> ts = c.targets(0, null);
        if (ts == null) return;
        for (Player p : ts) {
            if (!c.plugin.possessions().isVictim(p)) {
                c.send("<gray>" + p.getName() + " is not being worn by a skinwalker.");
                continue;
            }
            c.plugin.possessions().forget(p);
            c.send("<green>Freed " + p.getName() + " from the skinwalker.");
        }
    }
}
