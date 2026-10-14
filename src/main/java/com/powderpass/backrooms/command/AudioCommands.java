package com.powderpass.backrooms.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** audio status | reload | list | play | stop | debug. */
final class AudioCommands {
    private AudioCommands() {
    }

    static void register(Node root) {
        Node g = root.sub("audio").perm("backrooms.admin.audio").desc("the soundscape and the resource pack");
        g.sub("status").desc("engine, pack and who has it loaded").run(AudioCommands::status);
        g.sub("reload").desc("re-check the pack URL and restart the engine").run(AudioCommands::reload);
        g.sub("list").usage("[filter]").desc("every sound id").run(AudioCommands::list);
        Node play = g.sub("play").usage("<sound> [targets] [volume] [pitch]").desc("play a sound to players")
                .run(AudioCommands::play)
                .complete((c, i) -> switch (i) {
                    case 0 -> new ArrayList<>(new TreeSet<>(c.plugin.audio().index().ids()));
                    case 1 -> Ctx.targetSuggestions();
                    case 2 -> List.of("0.5", "1", "2");
                    case 3 -> List.of("0.8", "1", "1.2");
                    default -> List.of();
                });
        g.sub("stop").usage("[targets]").desc("stop what the plugin is playing to players").run(AudioCommands::stop)
                .complete((c, i) -> i == 0 ? Ctx.targetSuggestions() : List.of());
        g.sub("debug").desc("action-bar overlay of your soundscape").run(AudioCommands::debug);
        g.link("test", play); // old name: audio test <sound> [player]
    }

    private static void status(Ctx c) {
        c.send("<gold>Backrooms audio");
        c.send("<gray>Engine: <white>" + c.plugin.audio().describe());
        c.send("<gray>Pack: <white>" + c.plugin.pack().describe());
        int loaded = 0, total = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            total++;
            if (c.plugin.pack().loaded(p)) loaded++;
        }
        c.send("<gray>Players with the pack loaded: <white>" + loaded + "/" + total);
        if (c.sender instanceof Player p) {
            var st = c.plugin.pack().status(p);
            c.send("<gray>Your pack status: <white>" + (st == null ? "not offered" : st.name()));
        }
    }

    private static void reload(Ctx c) {
        c.plugin.pack().start();
        c.plugin.audio().start();
        c.send("<green>Audio and resource pack reloaded. <gray>" + c.plugin.pack().describe());
    }

    private static void list(Ctx c) {
        String f = c.has(0) ? c.lower(0) : "";
        List<String> ids = new TreeSet<>(c.plugin.audio().index().ids()).stream().filter(id -> id.toLowerCase(Locale.ROOT).contains(f)).toList();
        c.send("<gold>" + ids.size() + " sound ids" + (f.isEmpty() ? "" : " matching '" + f + "'"));
        for (int i = 0; i < ids.size(); i += 6) c.send("<gray>" + String.join("  ", ids.subList(i, Math.min(ids.size(), i + 6))));
    }

    private static void play(Ctx c) {
        if (!c.has(0)) {
            c.usage();
            return;
        }
        String id = c.arg(0);
        List<Player> ts = c.targets(1, null);
        if (ts == null) return;
        float volume = (float) Math.max(0.01, Math.min(4, c.doubleArg(2, 1)));
        float pitch = (float) Math.max(0.5, Math.min(2, c.doubleArg(3, 1)));
        String result = null;
        for (Player p : ts) result = c.plugin.audio().play(p, id, volume, pitch);
        if (result != null && result.startsWith("unknown")) c.send("<red>" + result + ". Try /" + c.label + " audio list");
        else c.send("<gray>Played <white>" + result + "</white> to " + (ts.size() == 1 ? ts.get(0).getName() : ts.size() + " players") + ".");
    }

    private static void stop(Ctx c) {
        List<Player> ts = c.targets(0, null);
        if (ts == null) return;
        for (Player p : ts) c.plugin.audio().stop(p);
        c.send("<gray>Stopped sounds for " + ts.size() + " player(s).");
    }

    private static void debug(Ctx c) {
        Player p = c.self();
        if (p == null) return;
        c.send("<gray>Audio debug overlay " + (c.plugin.audio().toggleDebug(p) ? "<green>on" : "<red>off"));
    }
}
