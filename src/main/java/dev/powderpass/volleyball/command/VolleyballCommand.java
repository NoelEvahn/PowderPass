package dev.powderpass.volleyball.command;

import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.ball.Volleyball;
import dev.powderpass.volleyball.court.Court;
import dev.powderpass.volleyball.court.CourtPreset;
import dev.powderpass.volleyball.game.Match;
import dev.powderpass.volleyball.game.MatchManager;
import dev.powderpass.volleyball.game.MatchSettings;
import dev.powderpass.volleyball.game.Rule;
import dev.powderpass.volleyball.game.TeamId;
import dev.powderpass.volleyball.items.ItemType;
import dev.powderpass.volleyball.math.Vec3;
import dev.powderpass.volleyball.physics.TrajectorySolver;
import dev.powderpass.volleyball.ui.GuideBook;
import dev.powderpass.volleyball.ui.WorldScoreboard;
import dev.powderpass.volleyball.util.Keys;
import dev.powderpass.volleyball.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** /vb - every player and admin feature in one command tree. */
public final class VolleyballCommand implements TabExecutor {

    private final PowderPassPlugin plugin;

    public VolleyballCommand(PowderPassPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        try {
            switch (sub) {
                case "guide" -> guide(sender);
                case "join" -> join(sender, rest);
                case "leave" -> leave(sender);
                case "start" -> start(sender, rest);
                case "stop" -> stop(sender, rest);
                case "rules" -> rules(sender, rest);
                case "court" -> court(sender, rest);
                case "scoreboard", "board" -> scoreboard(sender, rest);
                case "ball" -> ball(sender, rest);
                case "give" -> give(sender, rest);
                case "practice" -> practice(sender);
                case "aim" -> aim(sender);
                case "referee", "ref" -> referee(sender, rest);
                case "timeout" -> timeout(sender);
                case "status" -> status(sender, rest);
                case "reload" -> reload(sender);
                case "debug" -> debug(sender, rest);
                default -> Msg.error(sender, "Unknown subcommand. Try /" + label + " help");
            }
        } catch (CommandError e) {
            Msg.error(sender, Msg.escape(e.getMessage()));
        }
        return true;
    }

    private static final class CommandError extends RuntimeException {
        CommandError(String msg) {
            super(msg);
        }
    }

    private static Player player(CommandSender s) {
        if (s instanceof Player p) {
            return p;
        }
        throw new CommandError("Only players can use this.");
    }

    private static void perm(CommandSender s, String perm) {
        if (!s.hasPermission(perm)) {
            throw new CommandError("You don't have permission (" + perm + ").");
        }
    }

    private Court courtArg(CommandSender s, String[] args, int index) {
        if (args.length > index) {
            Court c = plugin.courts().get(args[index]);
            if (c == null) {
                throw new CommandError("No court named '" + args[index] + "'. See /vb court list");
            }
            return c;
        }
        if (s instanceof Player p) {
            Court c = plugin.courts().at(p.getLocation(), 6);
            if (c == null) {
                c = plugin.courts().nearest(p.getLocation(), 64);
            }
            if (c != null) {
                return c;
            }
        }
        throw new CommandError("Specify a court name (no court nearby).");
    }

    private static double num(String s, String what) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new CommandError("'" + s + "' is not a valid " + what + ".");
        }
    }

    // ------------------------------------------------------------------------------------------- help

    private void help(CommandSender s, String label) {
        String c = "/" + label;
        s.sendMessage(Msg.mm("<gradient:#ffd34d:#4da6ff><b>━━━━━━ Volleyball ━━━━━━</b></gradient>"));
        line(s, c + " guide", "Open the illustrated guide (controls, rules)");
        line(s, c + " join [court] [red|blue]", "Join a court's lobby");
        line(s, c + " leave", "Leave the lobby or match");
        line(s, c + " practice", "Get a ball + hands to practice anywhere");
        line(s, c + " aim", "Toggle the aim target marker");
        line(s, c + " timeout", "Call a timeout for your team");
        if (s.hasPermission("powderpass.host")) {
            s.sendMessage(Msg.mm("<gray>Hosting:"));
            line(s, c + " start [court]", "Start a match with the lobby");
            line(s, c + " stop [court]", "Stop a match");
            line(s, c + " rules [court]", "Rules & format menu (or: rules <court> <rule> <on|off>)");
            line(s, c + " referee [court] [player]", "Become referee, get the whistle");
            line(s, c + " status [court]", "Show lobby and match info");
        }
        if (s.hasPermission("powderpass.admin")) {
            s.sendMessage(Msg.mm("<gray>Building:"));
            line(s, c + " court create <name> [preset] [nofloor]", "Build a court here (presets: indoor, women, beach, mini, duel)");
            line(s, c + " court create <name> custom <len> <width> <net>", "Custom size court");
            line(s, c + " court remove <name> [keepfloor]", "Remove a court (restores the floor)");
            line(s, c + " court list|info|tp|net|respawn", "Manage courts");
            line(s, c + " scoreboard create <name> <court>", "Place a live scoreboard here");
            line(s, c + " scoreboard remove|link|move|scale|billboard|list", "Manage scoreboards");
            line(s, c + " ball spawn|clear", "Spawn or clear free-play balls");
            line(s, c + " give <player> <volleyball|whistle|hands>", "Give custom items");
            line(s, c + " reload", "Reload config.yml");
        }
    }

    private static void line(CommandSender s, String cmd, String desc) {
        s.sendMessage(Msg.mm("<yellow><click:suggest_command:'" + cmd.replaceAll("[\\[<].*$", "").trim() + " '>"
                + Msg.escape(cmd) + "</click></yellow> <dark_gray>-</dark_gray> <gray>" + Msg.escape(desc)));
    }

    private void guide(CommandSender s) {
        GuideBook.open(player(s));
    }

    // ------------------------------------------------------------------------------------------- playing

    private void join(CommandSender s, String[] args) {
        Player p = player(s);
        perm(s, "powderpass.play");
        TeamId pref = null;
        String[] a = args;
        if (a.length > 0) {
            TeamId t = parseTeam(a[a.length - 1]);
            if (t != null) {
                pref = t;
                a = Arrays.copyOf(a, a.length - 1);
            }
        }
        Court c = courtArg(s, a, 0);
        Match running = plugin.matches().byCourt(c);
        if (running != null) {
            throw new CommandError("A match is running on " + c.name() + ". Wait for it to finish.");
        }
        MatchManager.JoinResult r = plugin.matches().join(p, c, pref);
        switch (r) {
            case IN_MATCH -> throw new CommandError("You are already in a match. /vb leave first.");
            case FULL -> throw new CommandError("That lobby is full (12 players).");
            default -> {
                Map<UUID, TeamId> lobby = plugin.matches().lobby(c);
                String team = pref == null ? "any team" : teamName(pref);
                Msg.send(p, "<green>Joined <white>" + Msg.escape(c.name()) + "</white> lobby (" + team + "). "
                        + "<gray>" + lobby.size() + " waiting.");
                for (UUID id : lobby.keySet()) {
                    Player o = Bukkit.getPlayer(id);
                    if (o != null && o != p) {
                        Msg.send(o, "<gray>" + Msg.escape(p.getName()) + " joined the lobby (" + lobby.size() + ").");
                    }
                }
                if (lobby.size() >= 2 && p.hasPermission("powderpass.host")) {
                    p.sendMessage(Msg.mm("<gray>Ready? <yellow><click:run_command:'/vb start " + c.name()
                            + "'>[Click to start]</click></yellow> <dark_gray>or</dark_gray> <aqua><click:run_command:'/vb rules "
                            + c.name() + "'>[Rules]</click></aqua>"));
                }
            }
        }
    }

    private String teamName(TeamId id) {
        return id == TeamId.A ? plugin.settings().teamA.name() : plugin.settings().teamB.name();
    }

    private TeamId parseTeam(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        if (l.equals("a") || l.equalsIgnoreCase(plugin.settings().teamA.name())) {
            return TeamId.A;
        }
        if (l.equals("b") || l.equalsIgnoreCase(plugin.settings().teamB.name())) {
            return TeamId.B;
        }
        return null;
    }

    private void leave(CommandSender s) {
        Player p = player(s);
        boolean inMatch = plugin.matches().byPlayer(p) != null;
        String lobby = plugin.matches().leaveLobby(p);
        if (inMatch) {
            plugin.matches().leave(p, false);
            Msg.send(p, "<gray>You left the match.");
        } else if (lobby != null) {
            Msg.send(p, "<gray>You left the lobby.");
        } else {
            throw new CommandError("You are not in a lobby or match.");
        }
    }

    private void start(CommandSender s, String[] args) {
        perm(s, "powderpass.host");
        Court c = courtArg(s, args, 0);
        String err = plugin.matches().start(c, s instanceof Player p ? p : null);
        if (err != null) {
            throw new CommandError(err);
        }
        Msg.send(s, "<green>Match started on " + Msg.escape(c.name()) + ".");
    }

    private void stop(CommandSender s, String[] args) {
        perm(s, "powderpass.host");
        Court c = courtArg(s, args, 0);
        if (!plugin.matches().stop(c, "The match was stopped by " + s.getName() + ".")) {
            throw new CommandError("No match is running on " + c.name() + ".");
        }
        Msg.send(s, "<gray>Match stopped.");
    }

    private void rules(CommandSender s, String[] args) {
        perm(s, "powderpass.host");
        Court c = courtArg(s, args, 0);
        if (args.length <= 1) {
            plugin.menus().openRules(player(s), c, c.settings(), c.name());
            return;
        }
        String key = args[1];
        Rule r = Rule.byKey(key);
        MatchSettings.Option o = MatchSettings.Option.byKey(key);
        if (args.length < 3) {
            if (r != null) {
                Msg.send(s, "<white>" + r.displayName() + ": " + (c.settings().rule(r) ? "<green>on" : "<red>off"));
                return;
            }
            if (o != null) {
                Msg.send(s, "<white>" + o.label + ": <yellow>" + c.settings().get(o));
                return;
            }
            throw new CommandError("Unknown rule '" + key + "'.");
        }
        String value = args[2].toLowerCase(Locale.ROOT);
        if (r != null) {
            boolean on = value.equals("on") || value.equals("true") || value.equals("yes");
            c.settings().setRule(r, on);
            Msg.send(s, "<white>" + r.displayName() + (on ? " <green>enabled" : " <red>disabled") + "<white> for " + Msg.escape(c.name()));
        } else if (o != null) {
            int v = o.isToggle() ? (value.equals("on") || value.equals("true") ? 1 : value.equals("off") || value.equals("false") ? 0 : (int) num(value, "number"))
                    : (int) num(value, "number");
            c.settings().set(o, v);
            Msg.send(s, "<white>" + o.label + " = <yellow>" + c.settings().get(o) + "<white> for " + Msg.escape(c.name()));
        } else {
            throw new CommandError("Unknown rule '" + key + "'.");
        }
        plugin.courts().save();
    }

    private void practice(CommandSender s) {
        Player p = player(s);
        perm(s, "powderpass.practice");
        if (plugin.matches().byPlayer(p) != null) {
            throw new CommandError("You are in a match.");
        }
        giveIfMissing(p, ItemType.HANDS);
        giveIfMissing(p, ItemType.VOLLEYBALL);
        Msg.send(p, "<green>Practice kit ready.</green> <gray>Hold the ball: right-click to toss, left-click to serve. "
                + "Use the Hands to bump/set/spike loose balls. <white>/vb guide</white> for all controls.");
    }

    private void giveIfMissing(Player p, ItemType type) {
        for (var it : p.getInventory().getContents()) {
            if (dev.powderpass.volleyball.items.CustomItems.is(it, type)) {
                return;
            }
        }
        p.getInventory().addItem(plugin.items().create(type));
    }

    private void aim(CommandSender s) {
        Player p = player(s);
        boolean on = plugin.hits().toggleReticle(p);
        Msg.send(p, "<gray>Aim marker " + (on ? "<green>on" : "<red>off"));
    }

    private void referee(CommandSender s, String[] args) {
        perm(s, "powderpass.referee");
        Court c = courtArg(s, args, 0);
        Match m = plugin.matches().byCourt(c);
        if (m == null) {
            throw new CommandError("No match running on " + c.name() + ".");
        }
        Player ref = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : player(s);
        if (ref == null) {
            throw new CommandError("Player not found.");
        }
        m.setReferee(ref.getUniqueId());
        if (!m.contains(ref.getUniqueId())) {
            ref.getInventory().addItem(plugin.items().create(ItemType.WHISTLE));
        }
        Msg.send(ref, "<green>You are the referee of " + Msg.escape(c.name()) + ".</green> <gray>Whistle: right-click to blow "
                + "(allows the serve when 'referee-whistle' is on), sneak + right-click for the referee menu.");
    }

    private void timeout(CommandSender s) {
        Player p = player(s);
        Match m = plugin.matches().byPlayer(p);
        if (m == null) {
            throw new CommandError("You are not in a match.");
        }
        if (!m.callTimeout(p)) {
            throw new CommandError("You can't call a timeout now (only between rallies, limited per set).");
        }
    }

    private void status(CommandSender s, String[] args) {
        Court c = courtArg(s, args, 0);
        Match m = plugin.matches().byCourt(c);
        Msg.send(s, "<white><b>" + Msg.escape(c.name()) + "</b> <gray>" + c.length() + "x" + c.width() + " m, net "
                + c.netHeight() + " m · " + c.settings().describe());
        if (m == null) {
            Map<UUID, TeamId> lobby = plugin.matches().lobby(c);
            String names = lobby.entrySet().stream().map(e -> {
                Player p = Bukkit.getPlayer(e.getKey());
                return (p == null ? "?" : p.getName()) + (e.getValue() == null ? "" : " (" + teamName(e.getValue()) + ")");
            }).collect(Collectors.joining(", "));
            Msg.send(s, "<gray>Lobby (" + lobby.size() + "): <white>" + Msg.escape(names.isEmpty() ? "empty" : names));
        } else {
            Msg.send(s, "<gray>Match: <white>" + m.team(TeamId.A).name() + " " + m.score().points(TeamId.A) + " - "
                    + m.score().points(TeamId.B) + " " + m.team(TeamId.B).name() + "<gray> · set " + m.score().setNumber()
                    + " · sets " + m.score().sets(TeamId.A) + "-" + m.score().sets(TeamId.B) + " · " + m.state().label());
        }
    }

    // ------------------------------------------------------------------------------------------- courts

    private void court(CommandSender s, String[] args) {
        perm(s, "powderpass.admin");
        if (args.length == 0) {
            throw new CommandError("Usage: /vb court <create|remove|list|info|tp|net|respawn>");
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> courtCreate(s, rest);
            case "remove", "delete" -> {
                if (rest.length < 1) {
                    throw new CommandError("Usage: /vb court remove <name> [keepfloor]");
                }
                Court c = courtArg(s, rest, 0);
                plugin.matches().stop(c, "The court was removed.");
                boolean keep = rest.length > 1 && rest[1].equalsIgnoreCase("keepfloor");
                plugin.courts().remove(c.name(), !keep);
                Msg.send(s, "<gray>Court <white>" + Msg.escape(c.name()) + "</white> removed" + (keep ? "." : ", floor restored."));
            }
            case "list" -> {
                List<String> names = plugin.courts().names();
                if (names.isEmpty()) {
                    Msg.send(s, "<gray>No courts yet. Stand where the centre should be and run /vb court create <name>");
                    return;
                }
                Msg.send(s, "<white>Courts:");
                for (String n : names) {
                    Court c = plugin.courts().get(n);
                    Match m = plugin.matches().byCourt(c);
                    s.sendMessage(Msg.mm(" <yellow><click:run_command:'/vb court tp " + n + "'>" + Msg.escape(n)
                            + "</click></yellow> <gray>" + c.worldName() + " " + (int) c.centerX() + "," + (int) c.centerY()
                            + "," + (int) c.centerZ() + " · " + c.length() + "x" + c.width() + " · net " + c.netHeight()
                            + (m != null ? " · <green>match running" : "")));
                }
            }
            case "info" -> status(s, rest);
            case "tp" -> {
                Court c = courtArg(s, rest, 0);
                player(s).teleport(c.facingNet(-(c.halfLength() + 1.5), c.halfWidth() + 1.5));
            }
            case "net", "netheight" -> {
                if (rest.length < 2) {
                    throw new CommandError("Usage: /vb court net <court> <height in metres>");
                }
                Court c = courtArg(s, rest, 0);
                double h = num(rest[1], "height");
                if (h < 1.0 || h > 4.0) {
                    throw new CommandError("Net height must be between 1.0 and 4.0 m.");
                }
                if (plugin.matches().byCourt(c) != null) {
                    throw new CommandError("Stop the match first.");
                }
                c.setNetHeight(h);
                plugin.courts().spawnVisuals(c);
                plugin.courts().save();
                Msg.send(s, "<green>Net height of " + Msg.escape(c.name()) + " set to " + h + " m.");
            }
            case "respawn" -> {
                Court c = courtArg(s, rest, 0);
                plugin.courts().spawnVisuals(c);
                Msg.send(s, "<green>Court decorations respawned.");
            }
            case "floor" -> {
                Court c = courtArg(s, rest, 0);
                int n = plugin.courts().builder().placeFloor(c, plugin.settings());
                plugin.courts().save();
                Msg.send(s, "<green>Floor placed (" + n + " blocks changed).");
            }
            default -> throw new CommandError("Usage: /vb court <create|remove|list|info|tp|net|respawn|floor>");
        }
    }

    private void courtCreate(CommandSender s, String[] args) {
        Player p = player(s);
        if (args.length < 1) {
            throw new CommandError("Usage: /vb court create <name> [indoor|women|beach|mini|duel|custom <len> <width> <net>] [nofloor]");
        }
        String name = args[0];
        if (!name.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new CommandError("Court names may only use letters, digits, - and _.");
        }
        if (plugin.courts().get(name) != null) {
            throw new CommandError("A court named " + name + " already exists.");
        }
        List<String> rest = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
        boolean floor = !rest.removeIf(x -> x.equalsIgnoreCase("nofloor"));
        CourtPreset preset = CourtPreset.INDOOR;
        double length;
        double width;
        double net;
        double attack;
        boolean beach;
        if (!rest.isEmpty() && rest.get(0).equalsIgnoreCase("custom")) {
            if (rest.size() < 4) {
                throw new CommandError("Usage: /vb court create <name> custom <length> <width> <netHeight>");
            }
            length = num(rest.get(1), "length");
            width = num(rest.get(2), "width");
            net = num(rest.get(3), "net height");
            if (length < 4 || length > 40 || width < 2 || width > 20 || net < 1 || net > 4) {
                throw new CommandError("Size limits: length 4-40, width 2-20, net 1-4 m.");
            }
            attack = length >= 12 ? Math.min(3, length / 6) : 0;
            beach = false;
        } else {
            if (!rest.isEmpty()) {
                preset = CourtPreset.byName(rest.get(0));
                if (preset == null) {
                    throw new CommandError("Unknown preset. Use indoor, women, beach, mini, duel or custom.");
                }
            }
            length = preset.length;
            width = preset.width;
            net = preset.netHeight;
            attack = preset.attackLine;
            beach = preset.beach;
        }
        Location at = p.getLocation();
        Court c = plugin.courts().create(name, at, length, width, net, attack, beach, floor);
        Msg.send(s, "<green>Court <white>" + Msg.escape(c.name()) + "</white> created</green> <gray>(" + length + "x" + width
                + " m, net " + net + " m" + (floor ? ", floor placed" : "") + "). Next: <yellow><click:suggest_command:'/vb scoreboard create "
                + c.name() + "_board " + c.name() + "'>/vb scoreboard create</click></yellow> and <yellow>/vb join " + c.name() + "</yellow>");
    }

    // ------------------------------------------------------------------------------------------- scoreboards

    private void scoreboard(CommandSender s, String[] args) {
        perm(s, "powderpass.admin");
        if (args.length == 0) {
            throw new CommandError("Usage: /vb scoreboard <create|remove|link|move|scale|billboard|list>");
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                Player p = player(s);
                if (rest.length < 1) {
                    throw new CommandError("Usage: /vb scoreboard create <name> [court]");
                }
                Court c = courtArg(s, rest, 1);
                Location at = p.getEyeLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(2));
                at.setYaw(p.getLocation().getYaw());
                plugin.boards().create(rest[0], c, at, 1.0f, Display.Billboard.FIXED);
                Msg.send(s, "<green>Scoreboard <white>" + Msg.escape(rest[0]) + "</white> placed, linked to " + Msg.escape(c.name()) + ".");
            }
            case "remove", "delete" -> {
                if (rest.length < 1 || !plugin.boards().remove(rest[0])) {
                    throw new CommandError("No scoreboard with that name.");
                }
                Msg.send(s, "<gray>Scoreboard removed.");
            }
            case "link" -> {
                if (rest.length < 2) {
                    throw new CommandError("Usage: /vb scoreboard link <name> <court>");
                }
                WorldScoreboard b = board(rest[0]);
                Court c = courtArg(s, rest, 1);
                b.setCourt(c.name());
                plugin.boards().respawn(b);
                Msg.send(s, "<green>Linked to " + Msg.escape(c.name()) + ".");
            }
            case "move" -> {
                if (rest.length < 1) {
                    throw new CommandError("Usage: /vb scoreboard move <name>");
                }
                Player p = player(s);
                WorldScoreboard b = board(rest[0]);
                Location at = p.getEyeLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(2));
                at.setPitch(0);
                at.setYaw(Math.round((p.getLocation().getYaw() + 180) / 90f) * 90f);
                b.setLocation(at);
                plugin.boards().respawn(b);
                Msg.send(s, "<green>Moved.");
            }
            case "scale" -> {
                if (rest.length < 2) {
                    throw new CommandError("Usage: /vb scoreboard scale <name> <0.3-5>");
                }
                WorldScoreboard b = board(rest[0]);
                b.setScale((float) Math.max(0.3, Math.min(5, num(rest[1], "scale"))));
                plugin.boards().respawn(b);
                Msg.send(s, "<green>Scale set.");
            }
            case "billboard" -> {
                if (rest.length < 2) {
                    throw new CommandError("Usage: /vb scoreboard billboard <name> <fixed|vertical|center>");
                }
                WorldScoreboard b = board(rest[0]);
                try {
                    b.setBillboard(Display.Billboard.valueOf(rest[1].toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    throw new CommandError("Use fixed, vertical, horizontal or center.");
                }
                plugin.boards().respawn(b);
                Msg.send(s, "<green>Billboard mode set.");
            }
            case "list" -> {
                if (plugin.boards().all().isEmpty()) {
                    Msg.send(s, "<gray>No scoreboards.");
                }
                for (WorldScoreboard b : plugin.boards().all()) {
                    Location l = b.location();
                    s.sendMessage(Msg.mm(" <yellow>" + Msg.escape(b.name()) + "</yellow> <gray>-> " + Msg.escape(b.court())
                            + " · " + l.getWorld().getName() + " " + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ()));
                }
            }
            default -> throw new CommandError("Usage: /vb scoreboard <create|remove|link|move|scale|billboard|list>");
        }
    }

    private WorldScoreboard board(String name) {
        WorldScoreboard b = plugin.boards().get(name);
        if (b == null) {
            throw new CommandError("No scoreboard named '" + name + "'.");
        }
        return b;
    }

    // ------------------------------------------------------------------------------------------- items & balls

    private void ball(CommandSender s, String[] args) {
        String action = args.length == 0 ? "spawn" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "spawn" -> {
                perm(s, "powderpass.practice");
                Player p = player(s);
                Location eye = p.getEyeLocation();
                Vec3 pos = new Vec3(eye.getX(), eye.getY(), eye.getZ()).add(
                        eye.getDirection().getX() * 1.2, 0.3, eye.getDirection().getZ() * 1.2);
                Volleyball b = plugin.balls().spawnPractice(p, pos);
                b.launch(new Vec3(0, 3, 0), new Vec3(), false, null, null);
                Msg.send(p, "<gray>Ball spawned. Hit it with the Hands item (/vb practice).");
            }
            case "clear" -> {
                if (s.hasPermission("powderpass.admin")) {
                    int n = plugin.balls().all().size();
                    plugin.balls().removeIf(b -> b.context() == plugin.balls().practice());
                    Msg.send(s, "<gray>Removed " + (n - plugin.balls().all().size()) + " free-play ball(s).");
                } else {
                    int n = plugin.balls().removeOwnedBy(player(s).getUniqueId());
                    Msg.send(s, "<gray>Removed " + n + " ball(s).");
                }
            }
            case "give" -> give(s, new String[]{s.getName(), "volleyball"});
            default -> throw new CommandError("Usage: /vb ball <spawn|clear|give>");
        }
    }

    private void give(CommandSender s, String[] args) {
        perm(s, "powderpass.admin");
        if (args.length < 2) {
            throw new CommandError("Usage: /vb give <player> <volleyball|whistle|hands>");
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            throw new CommandError("Player not found.");
        }
        ItemType type = ItemType.byName(args[1]);
        if (type == null) {
            throw new CommandError("Unknown item. Use volleyball, whistle or hands.");
        }
        target.getInventory().addItem(plugin.items().create(type));
        Msg.send(s, "<gray>Gave " + type.name().toLowerCase(Locale.ROOT) + " to " + Msg.escape(target.getName()) + ".");
    }

    private void reload(CommandSender s) {
        perm(s, "powderpass.admin");
        plugin.reloadPlugin();
        Msg.send(s, "<green>Configuration reloaded.");
    }

    // ------------------------------------------------------------------------------------------- debug

    private void debug(CommandSender s, String[] args) {
        perm(s, "powderpass.admin");
        String action = args.length == 0 ? "stats" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "stats" -> {
                Msg.send(s, "<white>Balls: " + plugin.balls().all().size() + " · matches: " + plugin.matches().all().size()
                        + " · courts: " + plugin.courts().all().size() + " · scoreboards: " + plugin.boards().all().size());
                Msg.send(s, String.format(Locale.ROOT, "<white>Tick cost: avg %.3f ms, max %.3f ms (last 10 s)",
                        plugin.avgTickMs(), plugin.maxTickMs()));
                Msg.send(s, "<gray>ms/tick since last check: " + plugin.tickBreakdown());
                for (Court c : plugin.courts().all()) {
                    var v = plugin.courts().visuals(c);
                    Msg.send(s, "<gray>" + Msg.escape(c.name()) + ": " + (v == null ? 0 : v.entityCount()) + " display entities");
                }
                for (Volleyball b : plugin.balls().all()) {
                    Msg.send(s, "<gray>ball " + b.position() + " v=" + String.format(Locale.ROOT, "%.2f", b.body().speed())
                            + " resting=" + b.body().resting + " court=" + (b.court() == null ? "-" : b.court().name()));
                }
            }
            case "launch" -> {
                // /vb debug launch <court> <u> <y> <w> <vu> <vy> <vw> [topspin]
                if (args.length < 8) {
                    throw new CommandError("Usage: /vb debug launch <court> <u> <y> <w> <vu> <vy> <vw> [topspin]");
                }
                Court c = courtArg(s, args, 1);
                World world = c.world();
                Vec3 pos = c.frame().toWorld(num(args[2], "u"), num(args[3], "y"), num(args[4], "w"), new Vec3());
                Vec3 vel = c.frame().dirToWorld(num(args[5], "vu"), num(args[6], "vy"), num(args[7], "vw"), new Vec3());
                double spin = args.length > 8 ? num(args[8], "topspin") : 0;
                Volleyball b = plugin.balls().spawn(world, pos, plugin.balls().practice());
                b.launch(vel, TrajectorySolver.SpinFactory.topspin(spin).spinFor(vel), false, null, null);
                Vec3 landing = plugin.hits().solver().predictLanding(pos, vel, TrajectorySolver.SpinFactory.topspin(spin).spinFor(vel), c.centerY());
                Msg.send(s, "<gray>Launched ball from " + pos + " predicted landing (no net) u="
                        + String.format(Locale.ROOT, "%.2f w=%.2f", c.frame().toU(landing.x, landing.z), c.frame().toW(landing.x, landing.z)));
            }
            case "cleanup" -> {
                int n = 0;
                for (World w : Bukkit.getWorlds()) {
                    for (Entity e : w.getEntities()) {
                        if (e.getPersistentDataContainer().has(Keys.ENTITY, PersistentDataType.STRING)) {
                            e.remove();
                            n++;
                        }
                    }
                }
                plugin.balls().shutdown();
                plugin.courts().spawnAll();
                plugin.boards().spawnAll();
                Msg.send(s, "<gray>Removed " + n + " plugin entities and respawned courts and scoreboards.");
            }
            default -> throw new CommandError("Usage: /vb debug <stats|launch|cleanup>");
        }
    }

    // ------------------------------------------------------------------------------------------- tab completion

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("help", "guide", "join", "leave", "practice", "aim", "timeout", "status"));
            if (s.hasPermission("powderpass.host")) {
                out.addAll(List.of("start", "stop", "rules", "referee"));
            }
            if (s.hasPermission("powderpass.admin")) {
                out.addAll(List.of("court", "scoreboard", "ball", "give", "reload", "debug"));
            } else {
                out.add("ball");
            }
            return filter(out, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> courts = plugin.courts().names();
        switch (sub) {
            case "join" -> {
                if (args.length == 2) {
                    out.addAll(courts);
                    out.add(plugin.settings().teamA.name().toLowerCase(Locale.ROOT));
                    out.add(plugin.settings().teamB.name().toLowerCase(Locale.ROOT));
                } else if (args.length == 3) {
                    out.add(plugin.settings().teamA.name().toLowerCase(Locale.ROOT));
                    out.add(plugin.settings().teamB.name().toLowerCase(Locale.ROOT));
                }
            }
            case "start", "stop", "status" -> {
                if (args.length == 2) {
                    out.addAll(courts);
                }
            }
            case "referee", "ref" -> {
                if (args.length == 2) {
                    out.addAll(courts);
                } else if (args.length == 3) {
                    Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                }
            }
            case "rules" -> {
                if (args.length == 2) {
                    out.addAll(courts);
                } else if (args.length == 3) {
                    for (Rule r : Rule.values()) {
                        out.add(r.key());
                    }
                    for (MatchSettings.Option o : MatchSettings.Option.values()) {
                        out.add(o.key);
                    }
                } else if (args.length == 4) {
                    MatchSettings.Option o = MatchSettings.Option.byKey(args[2]);
                    if (Rule.byKey(args[2]) != null || (o != null && o.isToggle())) {
                        out.addAll(List.of("on", "off"));
                    } else if (o != null) {
                        out.add(String.valueOf(o.min));
                        out.add(String.valueOf(o.max));
                    }
                }
            }
            case "court" -> {
                if (args.length == 2) {
                    out.addAll(List.of("create", "remove", "list", "info", "tp", "net", "respawn", "floor"));
                } else if (args.length == 3 && !args[1].equalsIgnoreCase("create") && !args[1].equalsIgnoreCase("list")) {
                    out.addAll(courts);
                } else if (args.length == 3) {
                    out.add("<name>");
                } else if (args.length == 4 && args[1].equalsIgnoreCase("create")) {
                    for (CourtPreset p : CourtPreset.values()) {
                        out.add(p.name().toLowerCase(Locale.ROOT));
                    }
                    out.add("custom");
                    out.add("nofloor");
                } else if (args.length == 4 && args[1].equalsIgnoreCase("remove")) {
                    out.add("keepfloor");
                } else if (args.length >= 5 && args[1].equalsIgnoreCase("create")) {
                    out.add("nofloor");
                }
            }
            case "scoreboard", "board" -> {
                if (args.length == 2) {
                    out.addAll(List.of("create", "remove", "link", "move", "scale", "billboard", "list"));
                } else if (args.length == 3 && !args[1].equalsIgnoreCase("create")) {
                    out.addAll(plugin.boards().names());
                } else if (args.length == 4 && (args[1].equalsIgnoreCase("create") || args[1].equalsIgnoreCase("link"))) {
                    out.addAll(courts);
                } else if (args.length == 4 && args[1].equalsIgnoreCase("billboard")) {
                    out.addAll(List.of("fixed", "vertical", "center"));
                }
            }
            case "ball" -> {
                if (args.length == 2) {
                    out.addAll(List.of("spawn", "clear", "give"));
                }
            }
            case "give" -> {
                if (args.length == 2) {
                    Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                } else if (args.length == 3) {
                    out.addAll(List.of("volleyball", "whistle", "hands"));
                }
            }
            case "debug" -> {
                if (args.length == 2) {
                    out.addAll(List.of("stats", "launch", "cleanup"));
                } else if (args.length == 3 && args[1].equalsIgnoreCase("launch")) {
                    out.addAll(courts);
                }
            }
            default -> {
            }
        }
        return filter(out, args[args.length - 1]);
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).distinct().collect(Collectors.toList());
    }
}
