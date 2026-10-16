package com.powderpass.backrooms.command;

import com.powderpass.backrooms.world.SlideRides;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** slide list | info | ride. */
final class SlideCommands {
    private SlideCommands() {
    }

    static void register(Node root) {
        Node g = root.sub("slide").perm("backrooms.admin.slides").desc("water slides near you");
        g.sub("list").usage("[radius]").desc("slides starting near you (default 64 blocks)").run(SlideCommands::list)
                .complete((c, i) -> i == 0 ? List.of("32", "64", "128") : List.of());
        g.sub("info").desc("the nearest slide").run(SlideCommands::info);
        g.sub("ride").usage("[targets] [number]").desc("put players on the nearest slide (or number n from the list)")
                .run(SlideCommands::ride).complete((c, i) -> i == 0 ? Ctx.targetSuggestions() : i == 1 ? List.of("1", "2", "3") : List.of());
    }

    private static void list(Ctx c) {
        Location here = c.here();
        if (here == null) {
            c.msg("player-only");
            return;
        }
        double r = Math.max(8, Math.min(256, c.doubleArg(0, 64)));
        List<SlideRides.Track> ts = c.plugin.slides().near(here, r);
        if (ts.isEmpty()) {
            c.send("<gray>No slide starts within " + (int) r + " blocks.");
            return;
        }
        c.send("<gold>" + ts.size() + " slide(s) within " + (int) r + " blocks");
        for (int i = 0; i < ts.size(); i++) c.send("<white>" + (i + 1) + ".</white> " + describe(ts.get(i), here));
    }

    private static void info(Ctx c) {
        Location here = c.here();
        if (here == null) {
            c.msg("player-only");
            return;
        }
        List<SlideRides.Track> ts = c.plugin.slides().near(here, 64);
        c.send(ts.isEmpty() ? "<gray>No slide starts within 64 blocks." : "<gold>Nearest:</gold> " + describe(ts.get(0), here));
    }

    private static String describe(SlideRides.Track t, Location from) {
        double[][] p = t.pts();
        double len = 0;
        for (int i = 1; i < p.length; i++) len += Math.sqrt(Math.pow(p[i][0] - p[i - 1][0], 2) + Math.pow(p[i][1] - p[i - 1][1], 2) + Math.pow(p[i][2] - p[i - 1][2], 2));
        double d = Math.sqrt(Math.pow(p[0][0] - from.getX(), 2) + Math.pow(p[0][1] - from.getY(), 2) + Math.pow(p[0][2] - from.getZ(), 2));
        return String.format(Locale.ROOT, "<white>%s</white> <gray>start %.0f, %.0f, %.0f (%.0f m away), %.0f blocks long, drops %.0f%s",
                t.name(), p[0][0], p[0][1], p[0][2], d, len, p[0][1] - p[p.length - 1][1], t.wet() ? ", wet" : ", dry");
    }

    private static void ride(Ctx c) {
        boolean firstIsNumber = Ctx.isInt(c.arg(0));
        List<Player> ts = c.targets(firstIsNumber ? c.args.length : 0, null); // an index past the end = the sender
        if (ts == null) return;
        int n = Math.max(1, c.intArg(firstIsNumber ? 0 : 1, 1));
        int ok = 0;
        for (Player p : ts) {
            List<SlideRides.Track> near = c.plugin.slides().near(p.getLocation(), 96);
            if (near.size() < n) {
                c.send("<red>No slide #" + n + " near " + p.getName() + ".");
                continue;
            }
            if (c.plugin.slides().begin(p, near.get(n - 1))) ok++;
        }
        if (ok > 0) c.send("<green>" + ok + " player(s) on their way down.");
    }
}
