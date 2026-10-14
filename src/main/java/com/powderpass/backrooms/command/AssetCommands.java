package com.powderpass.backrooms.command;

import com.powderpass.backrooms.display.AssetCatalog;
import com.powderpass.backrooms.display.AssetStore;
import com.powderpass.backrooms.display.DecorationManager;
import com.powderpass.backrooms.display.ModelLibrary;
import com.powderpass.backrooms.gen.DecorSpec;
import com.powderpass.backrooms.gen.SlidePath;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** assets list | spawn | remove | info: every display model of the plugin, placeable anywhere and saved. */
final class AssetCommands {
    private AssetCommands() {
    }

    static void register(Node root) {
        Node g = root.sub("assets").perm("backrooms.admin.assets").desc("place any prop, creature model or slide");
        g.sub("list").usage("[category | filter]").desc("the catalogue").run(AssetCommands::list)
                .complete((c, i) -> i == 0 ? new ArrayList<>(AssetCatalog.categories().keySet()) : List.of());
        g.sub("spawn").usage("<asset> [x y z] [yaw] [seed]").desc("where you look (or at x y z, ~ allowed); saved")
                .run(AssetCommands::spawn)
                .complete((c, i) -> switch (i) {
                    case 0 -> AssetCatalog.all();
                    case 1, 2, 3 -> List.of("~");
                    case 4 -> List.of("0", "90", "180", "270");
                    default -> List.of();
                });
        g.sub("remove").usage("[radius | all]").desc("placed assets near you (default 3 blocks), or all in this world")
                .run(AssetCommands::remove).complete((c, i) -> i == 0 ? List.of("3", "8", "all") : List.of());
        g.sub("info").desc("what is near you, placed or generated").run(AssetCommands::info);
    }

    private static void list(Ctx c) {
        Map<String, List<String>> cats = AssetCatalog.categories();
        if (!c.has(0)) {
            c.send("<#d8c46a><bold>Assets</bold> <gray>" + AssetCatalog.all().size() + " in " + cats.size() + " categories. <dark_gray>/" + c.label + " assets list <category>");
            for (Map.Entry<String, List<String>> e : cats.entrySet()) {
                c.send("<white>" + e.getKey() + "</white> <gray>(" + e.getValue().size() + ") <dark_gray>" + String.join(", ", e.getValue().subList(0, Math.min(5, e.getValue().size()))) + "…");
            }
            c.send("<dark_gray>Most take a :suffix too, e.g. panel_light:flicker, floatie_ring:2, slide:enclosed:dry:12.");
            return;
        }
        String f = c.lower(0);
        List<String> ids = cats.containsKey(f) ? cats.get(f) : AssetCatalog.all().stream().filter(id -> id.contains(f)).toList();
        c.send("<gold>" + ids.size() + " asset(s)" + (cats.containsKey(f) ? " in " + f : " matching '" + f + "'"));
        for (int i = 0; i < ids.size(); i += 5) c.send("<gray>" + String.join("  ", ids.subList(i, Math.min(ids.size(), i + 5))));
    }

    private static void spawn(Ctx c) {
        if (!c.has(0)) {
            c.usage();
            return;
        }
        String id = c.lower(0);
        Location here = c.here();
        int next = 1;
        Location at;
        boolean exact = c.has(3) && Ctx.coord(c.arg(1), 0) != null && Ctx.coord(c.arg(2), 0) != null && Ctx.coord(c.arg(3), 0) != null;
        if (exact) {
            if (here == null && (c.arg(1).startsWith("~") || c.arg(2).startsWith("~") || c.arg(3).startsWith("~"))) {
                c.send("<red>~ needs a position: use absolute coordinates from the console.");
                return;
            }
            World w = here != null ? here.getWorld() : c.plugin.getServer().getWorlds().get(0);
            double bx = here == null ? 0 : here.getX(), by = here == null ? 0 : here.getY(), bz = here == null ? 0 : here.getZ();
            at = new Location(w, Ctx.coord(c.arg(1), bx), Ctx.coord(c.arg(2), by), Ctx.coord(c.arg(3), bz));
            next = 4;
        } else {
            if (here == null) {
                c.send("<red>From the console give coordinates: /" + c.label + " assets spawn <asset> <x> <y> <z>");
                return;
            }
            at = groundInFront(c, here);
        }
        float yaw;
        if (c.has(next) && Ctx.coord(c.arg(next), 0) != null) {
            yaw = (float) c.doubleArg(next, 0);
            next++;
        } else {
            // face whoever placed it, snapped to 45 degrees
            yaw = here == null ? 0 : (float) (Math.round((here.getYaw() + 180) / 45.0) * 45 % 360);
        }
        long seed = c.has(next) ? parseSeed(c.arg(next)) : ThreadLocalRandom.current().nextLong();
        try {
            if (ModelLibrary.build(id, seed) == null) {
                c.send("<red>Unknown asset <white>" + id + "</white>. Try /" + c.label + " assets list");
                return;
            }
        } catch (RuntimeException ex) {
            c.send("<red>" + id + " could not be built: " + ex.getMessage());
            return;
        }
        SlidePath slide = SlidePath.fromId(id, seed);
        if (slide != null) at.add(0, slide.drop + 0.15, 0); // the slide's end rests on the ground, its top up in the air
        AssetStore.Placed p = c.plugin.assets().add(at, id, yaw, seed, c.sender.getName());
        c.plugin.decorations().placed(at.getWorld(), p.spec());
        c.send(String.format(Locale.ROOT, "<green>Placed <white>%s</white> at %.1f, %.1f, %.1f <gray>(yaw %.0f, seed %d)%s",
                id, at.getX(), at.getY(), at.getZ(), yaw, seed, slide != null ? ". Click its ladder to ride." : "."));
    }

    /** On the block the sender looks at (within 8), else at their feet; centred on the block. */
    private static Location groundInFront(Ctx c, Location here) {
        if (c.sender instanceof Player p) {
            Block b = p.getTargetBlockExact(8);
            if (b != null) return b.getLocation().add(0.5, 1, 0.5);
        }
        return new Location(here.getWorld(), here.getBlockX() + 0.5, here.getBlockY(), here.getBlockZ() + 0.5);
    }

    private static long parseSeed(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return s.hashCode();
        }
    }

    private static void remove(Ctx c) {
        Location here = c.here();
        if (here == null) {
            c.send("<red>Stand near the assets to remove them.");
            return;
        }
        if ("all".equals(c.lower(0))) {
            World w = here.getWorld();
            List<AssetStore.Placed> all = c.plugin.assets().all(w);
            c.ask("remove all " + all.size() + " placed assets in " + w.getName(), () -> {
                for (AssetStore.Placed p : all) c.plugin.decorations().unplaced(w, p.spec());
                c.plugin.assets().remove(all);
                c.send("<green>Removed " + all.size() + " placed assets.");
            });
            return;
        }
        double r = Math.max(0.5, Math.min(64, c.doubleArg(0, 3)));
        List<AssetStore.Placed> near = c.plugin.assets().near(here, r);
        if (near.isEmpty()) {
            c.send("<gray>No placed asset within " + r + " blocks. (Generated props are part of the level and cannot be removed.)");
            return;
        }
        for (AssetStore.Placed p : near) c.plugin.decorations().unplaced(here.getWorld(), p.spec());
        c.plugin.assets().remove(near);
        c.send("<green>Removed " + near.size() + " asset(s): <gray>" + String.join(", ", near.stream().map(AssetStore.Placed::model).distinct().toList()));
    }

    private static void info(Ctx c) {
        Location here = c.here();
        if (here == null) {
            c.msg("player-only");
            return;
        }
        List<AssetStore.Placed> near = c.plugin.assets().near(here, 6);
        if (!near.isEmpty()) {
            AssetStore.Placed p = near.get(0);
            c.send(String.format(Locale.ROOT, "<gold>Placed</gold> <white>%s</white> <gray>at %.1f, %.1f, %.1f, yaw %.0f, seed %d, by %s",
                    p.model(), p.x(), p.y(), p.z(), p.yaw(), p.seed(), p.by()));
        }
        DecorationManager.Instance inst = c.plugin.decorations().nearest(here, 4);
        if (inst != null) {
            DecorSpec s = inst.spec();
            c.send(String.format(Locale.ROOT, "<gold>Nearest prop</gold> <white>%s</white> <gray>seed %d — spawn a copy with /%s assets spawn %s ~ ~ ~ %.0f %d",
                    s.model, s.seed, c.label, s.model, s.yaw, s.seed));
        }
        if (near.isEmpty() && inst == null) c.send("<gray>Nothing placed or generated within a few blocks.");
    }
}
