package com.powderpass.backrooms.command;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import com.powderpass.backrooms.config.BackroomsConfig;
import com.powderpass.backrooms.creature.CreatureType;
import com.powderpass.backrooms.event.EventType;
import com.powderpass.backrooms.gen.LevelGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** /backrooms — player and admin commands (console supported wherever a target player is given). */
public final class BackroomsCommand implements TabExecutor {
    private final BackroomsPlugin plugin;
    private final Map<String, Pending> pending = new HashMap<>();

    private record Pending(Runnable action, long expires) {
    }

    public BackroomsCommand(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    private BackroomsConfig cfg() {
        return plugin.cfg();
    }

    private void audio(CommandSender s, String[] a) {
        String sub = a.length < 2 ? "status" : a[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.pack().start();
                plugin.audio().start();
                send(s, "<green>Audio and resource pack reloaded. <gray>" + plugin.pack().describe());
            }
            case "test" -> {
                if (a.length < 3) {
                    send(s, "<red>Usage: /backrooms audio test <sound> [player]");
                    return;
                }
                Player target = a.length > 3 ? Bukkit.getPlayerExact(a[3]) : s instanceof Player p ? p : null;
                if (target == null) {
                    send(s, "<red>Name a player to play the sound to.");
                    return;
                }
                send(s, "<gray>Sent " + plugin.audio().test(target, a[2]) + " to " + target.getName());
            }
            case "debug" -> {
                if (!(s instanceof Player p)) {
                    s.sendMessage(cfg().msg("player-only"));
                    return;
                }
                send(s, "<gray>Audio debug overlay " + (plugin.audio().toggleDebug(p) ? "<green>on" : "<red>off"));
            }
            default -> {
                send(s, "<gold>Backrooms audio");
                send(s, "<gray>Engine: <white>" + plugin.audio().describe());
                send(s, "<gray>Pack: <white>" + plugin.pack().describe());
                int loaded = 0, total = 0;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    total++;
                    if (plugin.pack().loaded(p)) loaded++;
                }
                send(s, "<gray>Players with the pack loaded: <white>" + loaded + "/" + total);
                if (s instanceof Player p) {
                    var st = plugin.pack().status(p);
                    send(s, "<gray>Your pack status: <white>" + (st == null ? "not offered" : st.name()));
                }
            }
        }
    }

    private boolean need(CommandSender s, String perm) {
        if (s.hasPermission(perm)) return true;
        s.sendMessage(cfg().msg("no-permission"));
        return false;
    }

    private Player target(CommandSender s, String[] a, int idx, String otherPerm) {
        if (a.length > idx) {
            if (!need(s, otherPerm)) return null;
            Player p = Bukkit.getPlayerExact(a[idx]);
            if (p == null) s.sendMessage(cfg().msg("player-not-found", "name", a[idx]));
            return p;
        }
        if (s instanceof Player p) return p;
        s.sendMessage(cfg().msg("player-only"));
        return null;
    }

    private static void send(CommandSender s, String mini) {
        s.sendMessage(BackroomsConfig.mm(mini));
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> help(s);
            case "enter" -> enter(s, a);
            case "leave" -> leave(s, a);
            case "level" -> level(s, a);
            case "levels" -> levels(s);
            case "setspawn" -> setspawn(s, a);
            case "status" -> {
                if (need(s, "backrooms.admin.debug")) status(s);
            }
            case "entity" -> {
                if (need(s, "backrooms.admin.entities")) entity(s, a);
            }
            case "event" -> {
                if (need(s, "backrooms.admin.events")) event(s, a);
            }
            case "skinwalker" -> {
                if (need(s, "backrooms.admin.entities")) skinwalker(s, a);
            }
            case "reload" -> {
                if (need(s, "backrooms.admin.reload")) {
                    plugin.reload();
                    s.sendMessage(cfg().msg("reloaded"));
                }
            }
            case "debug" -> {
                if (need(s, "backrooms.admin.debug")) debug(s, a);
            }
            case "give" -> {
                if (need(s, "backrooms.admin")) give(s, a);
            }
            case "audio" -> {
                if (need(s, "backrooms.admin.audio")) audio(s, a);
            }
            case "confirm" -> confirm(s);
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        send(s, "<#d8c46a><bold>Backrooms</bold> <gray>— five places, no map.");
        send(s, "<white>/backrooms levels</white> <gray>list the levels");
        if (cfg().allowSelfEnter || s.hasPermission("backrooms.admin.teleport")) send(s, "<white>/backrooms enter</white> <gray>step out of reality into Level 0");
        if (cfg().allowSelfLeave || s.hasPermission("backrooms.admin.teleport")) send(s, "<white>/backrooms leave</white> <gray>return to where you came from");
        send(s, "<dark_gray>Inside: follow the hum of transitions; read the damp notes; drink almond water; keep the lights on.");
        if (s.hasPermission("backrooms.admin")) {
            send(s, "<gold>Admin:</gold> <white>enter|leave [player]</white>, <white>level <0|poolrooms|1|4|plains> [player]</white>, "
                    + "<white>setspawn <level></white>, <white>status</white>, <white>debug <on|off></white>, <white>reload</white>");
            send(s, "<gold>Admin:</gold> <white>entity list|spawn <type> [amount]|clear [radius|level]</white>, "
                    + "<white>event list|trigger <event> [level]</white>, <white>give <item> [player]</white>");
            send(s, "<gold>Admin:</gold> <white>audio status|reload|test <sound> [player]|debug</white>, "
                    + "<white>skinwalker <player></white> <gray>a skinwalker takes that player");
        }
    }

    private void enter(CommandSender s, String[] a) {
        if (!need(s, "backrooms.use")) return;
        Player p = target(s, a, 1, "backrooms.admin.teleport");
        if (p == null) return;
        boolean admin = s.hasPermission("backrooms.admin.teleport");
        if (!admin) {
            if (!cfg().allowSelfEnter) {
                s.sendMessage(cfg().msg("no-permission"));
                return;
            }
            if (!cfg().allowedWorlds.isEmpty() && !cfg().allowedWorlds.contains(p.getWorld().getName()) && !plugin.worlds().isBackrooms(p.getWorld())) {
                s.sendMessage(cfg().msg("enter-world-denied"));
                return;
            }
            if (plugin.worlds().isBackrooms(p.getWorld())) {
                s.sendMessage(cfg().msg("already-inside", "name", p.getName()));
                return;
            }
        }
        plugin.sessions().enter(p, s);
    }

    private void leave(CommandSender s, String[] a) {
        if (!need(s, "backrooms.use")) return;
        Player p = target(s, a, 1, "backrooms.admin.teleport");
        if (p == null) return;
        if (!s.hasPermission("backrooms.admin.teleport") && !cfg().allowSelfLeave) {
            s.sendMessage(cfg().msg("self-leave-denied"));
            return;
        }
        if (!plugin.worlds().isBackrooms(p.getWorld())) {
            s.sendMessage(cfg().msg("not-inside", "name", p.getName()));
            return;
        }
        plugin.sessions().leave(p, s);
    }

    private void level(CommandSender s, String[] a) {
        if (!need(s, "backrooms.admin.teleport")) return;
        if (a.length < 2) {
            send(s, "<red>Usage: /backrooms level <0|poolrooms|1|2|3|4|5> [player]");
            return;
        }
        Level l = Level.parse(a[1]);
        if (l == null) {
            s.sendMessage(cfg().msg("unknown-level", "level", a[1]));
            return;
        }
        if (!plugin.worlds().available(l)) {
            s.sendMessage(cfg().msg("level-disabled", "level", l.displayName()));
            return;
        }
        Player p = target(s, a, 2, "backrooms.admin.teleport");
        if (p == null) return;
        plugin.sessions().toLevel(p, l);
    }

    private void levels(CommandSender s) {
        if (!need(s, "backrooms.use")) return;
        send(s, "<#d8c46a><bold>The levels</bold>");
        for (Level l : Level.values()) {
            World w = plugin.worlds().world(l);
            String state = w == null ? "<red>disabled" : "<green>open</green> <gray>(" + w.getPlayers().size() + " inside)";
            send(s, l.color() + "■ <white>" + l.displayName() + "</white> <gray>— " + l.subtitle() + " " + state
                    + (s.hasPermission("backrooms.admin") ? " <dark_gray>[" + l.arg() + "]" : ""));
        }
    }

    private void setspawn(CommandSender s, String[] a) {
        if (!need(s, "backrooms.admin.teleport")) return;
        if (!(s instanceof Player p)) {
            s.sendMessage(cfg().msg("player-only"));
            return;
        }
        Level l = a.length > 1 ? Level.parse(a[1]) : plugin.worlds().levelOf(p.getWorld());
        if (l == null) {
            s.sendMessage(cfg().msg("unknown-level", "level", a.length > 1 ? a[1] : "?"));
            return;
        }
        if (plugin.worlds().levelOf(p.getWorld()) != l) {
            send(s, "<red>Stand inside " + l.displayName() + " to set its spawn.");
            return;
        }
        if (!plugin.teleports().isSafe(p.getLocation().getBlock())) {
            send(s, "<red>This spot is not safe (needs two free blocks over a solid, harmless floor).");
            return;
        }
        Location loc = p.getLocation();
        plugin.data().setSpawn(l, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        plugin.data().saveAsync(plugin);
        s.sendMessage(cfg().msg("spawn-set", "level", l.displayName(), "x", String.valueOf(loc.getBlockX()),
                "y", String.valueOf(loc.getBlockY()), "z", String.valueOf(loc.getBlockZ())));
    }

    private void status(CommandSender s) {
        send(s, "<#d8c46a><bold>Backrooms status</bold> <gray>seed " + plugin.data().baseSeed());
        for (Level l : Level.values()) {
            World w = plugin.worlds().world(l);
            if (w == null) {
                send(s, "<gray>" + l.displayName() + ": <red>not loaded");
                continue;
            }
            LevelGenerator g = plugin.worlds().generator(l);
            send(s, l.color() + l.displayName() + "</" + l.color().substring(1) + " <gray>world=<white>" + w.getName()
                    + "</white> players=<white>" + w.getPlayers().size() + "</white> chunks=<white>" + w.getLoadedChunks().length
                    + "</white> creatures=<white>" + plugin.creatures().countLevel(w) + "</white> displays=<white>" + plugin.decorations().entityCount(w)
                    + "</white> (" + plugin.decorations().activeChunks(w) + " chunks) <dark_gray>cell=" + g.S + " region=" + g.R + " floors=" + g.floorCount());
        }
        String types = plugin.creatures().counts().entrySet().stream().map(e -> e.getKey().id + "=" + e.getValue()).collect(Collectors.joining(", "));
        send(s, "<gray>Active threats: <white>" + (types.isEmpty() ? "none" : types));
        send(s, String.format(Locale.ROOT, "<gray>Tick cost (avg): decorations <white>%.2f ms</white>, creatures <white>%.2f ms</white>",
                plugin.decorations().costMillis(), plugin.creatures().costMillis()));
    }

    private void entity(CommandSender s, String[] a) {
        String op = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        switch (op) {
            case "list" -> {
                send(s, "<#d8c46a><bold>Creatures & anomalies");
                for (CreatureType t : CreatureType.values()) {
                    send(s, "<white>" + t.id + "</white> <dark_gray>(" + t.category + ")</dark_gray> <gray>" + t.description);
                }
            }
            case "spawn" -> {
                if (!(s instanceof Player p)) {
                    s.sendMessage(cfg().msg("player-only"));
                    return;
                }
                if (a.length < 3) {
                    send(s, "<red>Usage: /backrooms entity spawn <type> [amount]");
                    return;
                }
                CreatureType t = CreatureType.parse(a[2]);
                if (t == null) {
                    send(s, "<red>Unknown type. Try /backrooms entity list");
                    return;
                }
                if (!plugin.worlds().isBackrooms(p.getWorld())) {
                    send(s, "<red>Creatures only exist inside the Backrooms.");
                    return;
                }
                int amount = a.length > 3 ? parseInt(a[3], 1) : 1;
                amount = Math.max(1, Math.min(10, amount));
                int ok = 0;
                for (int i = 0; i < amount; i++) {
                    var c = plugin.creatures().spawnNatural(t, p);
                    if (c == null) {
                        Location front = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(4));
                        Location safe = plugin.teleports().findSafe(front, 4);
                        if (safe != null && t != CreatureType.DRIFTER) c = plugin.creatures().spawn(t, safe, null);
                    }
                    if (c != null) ok++;
                }
                send(s, "<green>Spawned " + ok + "/" + amount + " " + t.displayName + (ok < amount ? " <gray>(no valid spot for the rest)" : ""));
            }
            case "clear" -> {
                String arg = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "32";
                if (arg.equals("level") || Level.parse(arg) != null && !isNumber(arg)) {
                    Level l = arg.equals("level") ? (s instanceof Player p ? plugin.worlds().levelOf(p.getWorld()) : (a.length > 3 ? Level.parse(a[3]) : null))
                            : Level.parse(arg);
                    if (l == null || plugin.worlds().world(l) == null) {
                        send(s, "<red>Usage: /backrooms entity clear level [level]  (stand in the level or name it)");
                        return;
                    }
                    World w = plugin.worlds().world(l);
                    ask(s, "remove every managed creature in " + l.displayName(), () -> send(s, "<green>Removed " + plugin.creatures().clearWorld(w) + " creatures from " + l.displayName() + "."));
                } else {
                    if (!(s instanceof Player p)) {
                        send(s, "<red>From console use: /backrooms entity clear level <level>");
                        return;
                    }
                    int r = Math.max(1, Math.min(256, parseInt(arg, 32)));
                    send(s, "<green>Removed " + plugin.creatures().clearRadius(p.getLocation(), r) + " creatures within " + r + " blocks.");
                }
            }
            default -> send(s, "<red>Usage: /backrooms entity list|spawn <type> [amount]|clear [radius|level]");
        }
    }

    /** /backrooms skinwalker <player>: a skinwalker takes that player now (they spectate their own body, locked to it). */
    private void skinwalker(CommandSender s, String[] a) {
        if (a.length < 2) {
            send(s, "<red>Usage: /backrooms skinwalker <player>");
            return;
        }
        Player p = Bukkit.getPlayerExact(a[1]);
        if (p == null) {
            s.sendMessage(cfg().msg("player-not-found", "name", a[1]));
            return;
        }
        if (!plugin.worlds().isBackrooms(p.getWorld())) {
            send(s, "<red>" + p.getName() + " is not inside the Backrooms; skinwalkers only exist there.");
            return;
        }
        if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.isDead()) {
            send(s, "<red>" + p.getName() + " cannot be taken right now (spectator or dead).");
            return;
        }
        if (!plugin.creatures().skinwalkerTakes(p)) {
            send(s, "<red>" + p.getName() + " is already being worn, or creatures are disabled.");
            return;
        }
        send(s, "<green>A skinwalker has taken " + p.getName() + ". <gray>Kill it to free them (or /backrooms entity clear).");
    }

    private void event(CommandSender s, String[] a) {
        String op = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        if (op.equals("list")) {
            send(s, "<#d8c46a><bold>Environmental events");
            for (EventType t : EventType.values()) {
                send(s, "<white>" + t.id + "</white> <gray>" + t.description + " <dark_gray>"
                        + t.levels.stream().map(Level::arg).collect(Collectors.joining(",")));
            }
            return;
        }
        if (!op.equals("trigger") || a.length < 3) {
            send(s, "<red>Usage: /backrooms event list|trigger <event> [level]");
            return;
        }
        EventType t = EventType.parse(a[2]);
        if (t == null) {
            send(s, "<red>Unknown event. Try /backrooms event list");
            return;
        }
        List<Player> targets = new ArrayList<>();
        if (a.length > 3) {
            Level l = Level.parse(a[3]);
            if (l == null || plugin.worlds().world(l) == null) {
                s.sendMessage(cfg().msg("unknown-level", "level", a[3]));
                return;
            }
            targets.addAll(plugin.worlds().world(l).getPlayers());
        } else if (s instanceof Player p) {
            targets.add(p);
        } else {
            send(s, "<red>From console, name a level: /backrooms event trigger <event> <level>");
            return;
        }
        int n = 0;
        for (Player p : targets) if (plugin.environment().trigger(t, p)) n++;
        send(s, n > 0 ? "<green>Triggered " + t.id + " for " + n + " player(s)." : "<gray>No player inside a Backrooms level to trigger it for.");
        if (!t.levels.isEmpty() && targets.stream().anyMatch(p -> !t.levels.contains(plugin.worlds().levelOf(p.getWorld())))) {
            send(s, "<dark_gray>(Note: " + t.id + " does not normally occur in this level.)");
        }
    }

    private void debug(CommandSender s, String[] a) {
        boolean on = a.length < 2 ? !(s instanceof Player p && plugin.sessions().debug(p)) : a[1].equalsIgnoreCase("on");
        if (s instanceof Player p) {
            plugin.sessions().setDebug(p, on);
        }
        plugin.setConsoleDebug(on);
        send(s, "<gray>Backrooms debug " + (on ? "<green>on" : "<red>off") + (s instanceof Player ? " <dark_gray>(action bar + console log)" : " <dark_gray>(console log)"));
    }

    private void give(CommandSender s, String[] a) {
        if (a.length < 2) {
            send(s, "<red>Usage: /backrooms give <almond_water|flashlight|battery|ration|note> [player]");
            return;
        }
        Player p = target(s, a, 2, "backrooms.admin");
        if (p == null) return;
        ItemStack it = switch (a[1].toLowerCase(Locale.ROOT)) {
            case "almond_water" -> plugin.items().almondWater();
            case "flashlight" -> plugin.items().flashlight(cfg().flashlightSeconds);
            case "battery" -> plugin.items().battery();
            case "ration" -> plugin.items().ration();
            case "note" -> plugin.items().note(plugin.worlds().levelOf(p.getWorld()) == null ? Level.LEVEL_0 : plugin.worlds().levelOf(p.getWorld()));
            default -> null;
        };
        if (it == null) {
            send(s, "<red>Unknown item.");
            return;
        }
        p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        send(s, "<green>Gave " + a[1] + " to " + p.getName());
    }

    private void ask(CommandSender s, String what, Runnable action) {
        pending.put(s.getName(), new Pending(action, System.currentTimeMillis() + 15_000));
        s.sendMessage(cfg().msg("confirm", "action", what));
    }

    private void confirm(CommandSender s) {
        Pending p = pending.remove(s.getName());
        if (p == null || p.expires < System.currentTimeMillis()) {
            s.sendMessage(cfg().msg("nothing-to-confirm"));
            return;
        }
        p.action.run();
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean isNumber(String s) {
        return s.chars().allMatch(Character::isDigit) && !s.isEmpty();
    }

    // ------------------------------------------------------------------ tab completion

    private static final List<String> LEVEL_ARGS = Stream.of(Level.values()).map(Level::arg).toList();

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.add("help");
            out.add("levels");
            if (s.hasPermission("backrooms.use")) {
                if (cfg().allowSelfEnter || s.hasPermission("backrooms.admin.teleport")) out.add("enter");
                if (cfg().allowSelfLeave || s.hasPermission("backrooms.admin.teleport")) out.add("leave");
            }
            if (s.hasPermission("backrooms.admin.teleport")) out.addAll(List.of("level", "setspawn"));
            if (s.hasPermission("backrooms.admin.debug")) out.addAll(List.of("status", "debug"));
            if (s.hasPermission("backrooms.admin.entities")) out.addAll(List.of("entity", "skinwalker"));
            if (s.hasPermission("backrooms.admin.events")) out.add("event");
            if (s.hasPermission("backrooms.admin.reload")) out.add("reload");
            if (s.hasPermission("backrooms.admin")) out.add("give");
            if (s.hasPermission("backrooms.admin.audio")) out.add("audio");
            if (pending.containsKey(s.getName())) out.add("confirm");
        } else {
            String sub = a[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "enter", "leave" -> {
                    if (a.length == 2 && s.hasPermission("backrooms.admin.teleport")) out.addAll(players());
                }
                case "level" -> {
                    if (a.length == 2) out.addAll(LEVEL_ARGS);
                    else if (a.length == 3 && s.hasPermission("backrooms.admin.teleport")) out.addAll(players());
                }
                case "setspawn" -> {
                    if (a.length == 2) out.addAll(LEVEL_ARGS);
                }
                case "debug" -> {
                    if (a.length == 2) out.addAll(List.of("on", "off"));
                }
                case "audio" -> {
                    if (a.length == 2) out.addAll(List.of("status", "reload", "test", "debug"));
                    else if (a.length == 3 && a[1].equalsIgnoreCase("test")) out.addAll(new java.util.TreeSet<>(plugin.audio().index().ids()));
                    else if (a.length == 4 && a[1].equalsIgnoreCase("test")) out.addAll(players());
                }
                case "entity" -> {
                    if (a.length == 2) out.addAll(List.of("list", "spawn", "clear"));
                    else if (a.length == 3 && a[1].equalsIgnoreCase("spawn")) out.addAll(Stream.of(CreatureType.values()).map(t -> t.id).toList());
                    else if (a.length == 3 && a[1].equalsIgnoreCase("clear")) out.addAll(List.of("16", "32", "64", "level"));
                    else if (a.length == 4 && a[1].equalsIgnoreCase("spawn")) out.addAll(List.of("1", "2", "3"));
                    else if (a.length == 4 && a[1].equalsIgnoreCase("clear") && a[2].equalsIgnoreCase("level")) out.addAll(LEVEL_ARGS);
                }
                case "event" -> {
                    if (a.length == 2) out.addAll(List.of("list", "trigger"));
                    else if (a.length == 3 && a[1].equalsIgnoreCase("trigger")) out.addAll(Stream.of(EventType.values()).map(t -> t.id).toList());
                    else if (a.length == 4 && a[1].equalsIgnoreCase("trigger")) out.addAll(LEVEL_ARGS);
                }
                case "skinwalker" -> {
                    if (a.length == 2 && s.hasPermission("backrooms.admin.entities")) out.addAll(players());
                }
                case "give" -> {
                    if (a.length == 2) out.addAll(List.of("almond_water", "flashlight", "battery", "ration", "note"));
                    else if (a.length == 3) out.addAll(players());
                }
                default -> {
                }
            }
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }

    private static List<String> players() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }
}
