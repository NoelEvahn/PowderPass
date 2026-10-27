package dev.powderpass.volleyball.command;

import dev.powderpass.volleyball.PowderPassPlugin;
import dev.powderpass.volleyball.court.Court;
import dev.powderpass.volleyball.court.CourtPreset;
import dev.powderpass.volleyball.game.MatchManager;
import dev.powderpass.volleyball.game.MatchSettings;
import dev.powderpass.volleyball.game.Rule;
import dev.powderpass.volleyball.game.TeamId;
import dev.powderpass.volleyball.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** /vb - the essentials only. */
public final class VolleyballCommand implements TabExecutor {

    private final PowderPassPlugin plugin;

    public VolleyballCommand(PowderPassPlugin plugin) {
        this.plugin = plugin;
    }

    private static final class CommandError extends RuntimeException {
        CommandError(String msg) {
            super(msg);
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        String[] rest = args.length == 0 ? args : Arrays.copyOfRange(args, 1, args.length);
        try {
            switch (sub) {
                case "join" -> join(s, rest);
                case "leave" -> leave(s);
                case "ball" -> ball(s, rest);
                case "pepper" -> pepper(s, rest);
                case "start" -> start(s, rest);
                case "stop" -> stop(s, rest);
                case "rules" -> rules(s, rest);
                case "court" -> court(s, rest);
                case "scoreboard" -> scoreboard(s, rest);
                case "reload" -> {
                    perm(s, "powderpass.admin");
                    plugin.reloadPlugin();
                    Msg.send(s, "<green>Reloaded.");
                }
                default -> help(s);
            }
        } catch (CommandError e) {
            Msg.error(s, Msg.escape(e.getMessage()));
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(Msg.mm("<gold><b>How to play</b></gold> <gray>- just click when the ball is near you:"));
        s.sendMessage(Msg.mm("<gray> <white>Click</white> - the right touch is picked for you (pass, set, or spike when jumping)"));
        s.sendMessage(Msg.mm("<gray> <white>Sneak + click</white> - dive for low balls, tip when jumping"));
        s.sendMessage(Msg.mm("<gray> <white>Serve</white> - click to toss, click again to hit (jump for a jump serve), or sneak + click for underhand"));
        s.sendMessage(Msg.mm("<gray> Passes and sets aim themselves; look where you want spikes and serves to go."));
        line(s, "/vb join [court] [red|blue]", "join a court");
        line(s, "/vb leave", "leave the lobby or match");
        line(s, "/vb ball [clear]", "hold a practice ball");
        line(s, "/vb pepper [join <player>|leave]", "no-rules pepper drill");
        if (s.hasPermission("powderpass.host")) {
            line(s, "/vb start|stop [court]", "start or stop a match");
            line(s, "/vb rules [court]", "rules and format");
        }
        if (s.hasPermission("powderpass.admin")) {
            line(s, "/vb court create <name> [indoor|women|beach|mini|duel] [nofloor]", "build a court here");
            line(s, "/vb court remove <name> [keepfloor] | list", "manage courts");
            line(s, "/vb scoreboard create|remove <name> [court]", "live scoreboard");
            line(s, "/vb reload", "reload config.yml");
        }
    }

    private static void line(CommandSender s, String cmd, String desc) {
        s.sendMessage(Msg.mm(" <yellow>" + Msg.escape(cmd) + "</yellow> <dark_gray>-</dark_gray> <gray>" + Msg.escape(desc)));
    }

    private static Player player(CommandSender s) {
        if (s instanceof Player p) {
            return p;
        }
        throw new CommandError("Only players can use this.");
    }

    private static void perm(CommandSender s, String perm) {
        if (!s.hasPermission(perm)) {
            throw new CommandError("No permission.");
        }
    }

    private Court courtArg(CommandSender s, String[] args, int index) {
        if (args.length > index) {
            Court c = plugin.courts().get(args[index]);
            if (c == null) {
                throw new CommandError("No court named '" + args[index] + "'.");
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
        throw new CommandError("Name a court (none nearby).");
    }

    // ------------------------------------------------------------------------------------------- play

    private void join(CommandSender s, String[] args) {
        Player p = player(s);
        perm(s, "powderpass.play");
        TeamId pref = null;
        String[] a = args;
        if (a.length > 0) {
            String last = a[a.length - 1].toLowerCase(Locale.ROOT);
            if (last.equals(plugin.settings().teamA.name().toLowerCase(Locale.ROOT)) || last.equals("a")) {
                pref = TeamId.A;
            } else if (last.equals(plugin.settings().teamB.name().toLowerCase(Locale.ROOT)) || last.equals("b")) {
                pref = TeamId.B;
            }
            if (pref != null) {
                a = Arrays.copyOf(a, a.length - 1);
            }
        }
        Court c = courtArg(s, a, 0);
        if (plugin.matches().byCourt(c) != null) {
            throw new CommandError("A match is running on " + c.name() + ".");
        }
        if (plugin.matches().join(p, c, pref) == MatchManager.JoinResult.IN_MATCH) {
            throw new CommandError("You are already in a match.");
        }
        int waiting = plugin.matches().lobby(c).size();
        Msg.send(p, "<green>Joined " + Msg.escape(c.name()) + "</green> <gray>(" + waiting + " waiting)");
        if (waiting >= 2 && p.hasPermission("powderpass.host")) {
            p.sendMessage(Msg.mm("<yellow><click:run_command:'/vb start " + c.name() + "'>[Start match]</click></yellow>"));
        }
    }

    private void leave(CommandSender s) {
        Player p = player(s);
        boolean inMatch = plugin.matches().byPlayer(p) != null;
        String lobby = plugin.matches().leaveLobby(p);
        plugin.pepper().leave(p);
        if (inMatch) {
            plugin.matches().leave(p, false);
        }
        Msg.send(p, inMatch || lobby != null ? "<gray>You left." : "<gray>Nothing to leave.");
    }

    private void ball(CommandSender s, String[] args) {
        Player p = player(s);
        perm(s, "powderpass.practice");
        if (args.length > 0 && args[0].equalsIgnoreCase("clear")) {
            int n = plugin.balls().removeOwnedBy(p.getUniqueId());
            Msg.send(p, "<gray>Removed " + n + " ball(s).");
            return;
        }
        if (plugin.matches().byPlayer(p) != null) {
            throw new CommandError("You are in a match.");
        }
        plugin.balls().giveHeld(p);
        Msg.send(p, "<gray>Ball in hand: <white>left-click</white> toss · <white>sneak + left-click</white> underhand.");
    }

    private void pepper(CommandSender s, String[] args) {
        Player p = player(s);
        perm(s, "powderpass.practice");
        if (plugin.matches().byPlayer(p) != null) {
            throw new CommandError("You are in a match.");
        }
        String action = args.length == 0 ? "start" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "join" -> {
                Player host = args.length < 2 ? null : Bukkit.getPlayerExact(args[1]);
                if (host == null || !plugin.pepper().join(p, host)) {
                    throw new CommandError("That player is not peppering.");
                }
            }
            case "leave" -> {
                plugin.pepper().leave(p);
                Msg.send(p, "<gray>You left the pepper.");
            }
            default -> {
                plugin.pepper().start(p);
                Msg.send(p, "<gold>Pepper:</gold> <gray>keep the ball off the floor. Face a teammate and it goes to them; "
                        + "alone it comes back to you.");
            }
        }
    }

    // ------------------------------------------------------------------------------------------- host

    private void start(CommandSender s, String[] args) {
        perm(s, "powderpass.host");
        Court c = courtArg(s, args, 0);
        String err = plugin.matches().start(c, s instanceof Player p ? p : null);
        if (err != null) {
            throw new CommandError(err);
        }
    }

    private void stop(CommandSender s, String[] args) {
        perm(s, "powderpass.host");
        Court c = courtArg(s, args, 0);
        if (!plugin.matches().stop(c, "The match was stopped.")) {
            throw new CommandError("No match on " + c.name() + ".");
        }
    }

    private void rules(CommandSender s, String[] args) {
        perm(s, "powderpass.host");
        Court c = courtArg(s, args, 0);
        if (args.length < 3) {
            plugin.menus().openRules(player(s), c, c.settings(), c.name());
            return;
        }
        Rule r = Rule.byKey(args[1]);
        MatchSettings.Option o = MatchSettings.Option.byKey(args[1]);
        String v = args[2].toLowerCase(Locale.ROOT);
        boolean on = v.equals("on") || v.equals("true");
        if (r != null) {
            c.settings().setRule(r, on);
        } else if (o != null) {
            try {
                c.settings().set(o, o.isToggle() ? (on ? 1 : 0) : Integer.parseInt(v));
            } catch (NumberFormatException e) {
                throw new CommandError("Not a number: " + v);
            }
        } else {
            throw new CommandError("Unknown rule '" + args[1] + "'.");
        }
        plugin.courts().save();
        Msg.send(s, "<green>Updated.");
    }

    // ------------------------------------------------------------------------------------------- admin

    private void court(CommandSender s, String[] args) {
        perm(s, "powderpass.admin");
        String action = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> {
                Player p = player(s);
                if (args.length < 2 || !args[1].matches("[A-Za-z0-9_-]{1,32}")) {
                    throw new CommandError("Usage: /vb court create <name> [preset] [nofloor]");
                }
                if (plugin.courts().get(args[1]) != null) {
                    throw new CommandError("That court already exists.");
                }
                List<String> opts = new ArrayList<>(Arrays.asList(args).subList(2, args.length));
                boolean floor = !opts.removeIf(x -> x.equalsIgnoreCase("nofloor"));
                CourtPreset preset = opts.isEmpty() ? CourtPreset.INDOOR : CourtPreset.byName(opts.get(0));
                if (preset == null) {
                    throw new CommandError("Presets: indoor, women, beach, mini, duel.");
                }
                plugin.courts().create(args[1], p.getLocation(), preset.length, preset.width, preset.netHeight,
                        preset.attackLine, preset.beach, floor);
                Msg.send(s, "<green>Court " + Msg.escape(args[1]) + " created.");
            }
            case "remove" -> {
                Court c = courtArg(s, args, 1);
                plugin.matches().stop(c, "The court was removed.");
                boolean keep = args.length > 2 && args[2].equalsIgnoreCase("keepfloor");
                plugin.courts().remove(c.name(), !keep);
                Msg.send(s, "<gray>Court removed" + (keep ? "." : ", floor restored."));
            }
            default -> Msg.send(s, "<gray>Courts: <white>" + String.join(", ", plugin.courts().names()));
        }
    }

    private void scoreboard(CommandSender s, String[] args) {
        perm(s, "powderpass.admin");
        if (args.length < 2) {
            throw new CommandError("Usage: /vb scoreboard create|remove <name> [court]");
        }
        if (args[0].equalsIgnoreCase("remove")) {
            if (!plugin.boards().remove(args[1])) {
                throw new CommandError("No scoreboard named " + args[1] + ".");
            }
            Msg.send(s, "<gray>Scoreboard removed.");
            return;
        }
        Player p = player(s);
        Court c = courtArg(s, args, 2);
        var at = p.getEyeLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(2));
        at.setYaw(p.getLocation().getYaw());
        plugin.boards().create(args[1], c, at, 1.0f, Display.Billboard.FIXED);
        Msg.send(s, "<green>Scoreboard placed for " + Msg.escape(c.name()) + ".");
    }

    // ------------------------------------------------------------------------------------------- tab

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        List<String> courts = plugin.courts().names();
        if (args.length == 1) {
            out.addAll(List.of("join", "leave", "ball", "pepper"));
            if (s.hasPermission("powderpass.host")) {
                out.addAll(List.of("start", "stop", "rules"));
            }
            if (s.hasPermission("powderpass.admin")) {
                out.addAll(List.of("court", "scoreboard", "reload"));
            }
        } else {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "join" -> {
                    if (args.length == 2) {
                        out.addAll(courts);
                    }
                    out.add(plugin.settings().teamA.name().toLowerCase(Locale.ROOT));
                    out.add(plugin.settings().teamB.name().toLowerCase(Locale.ROOT));
                }
                case "start", "stop", "rules" -> {
                    if (args.length == 2) {
                        out.addAll(courts);
                    } else if (args.length == 3 && args[0].equalsIgnoreCase("rules")) {
                        Arrays.stream(Rule.values()).forEach(r -> out.add(r.key()));
                        Arrays.stream(MatchSettings.Option.values()).forEach(o -> out.add(o.key));
                    } else if (args.length == 4) {
                        out.addAll(List.of("on", "off"));
                    }
                }
                case "ball" -> out.add("clear");
                case "pepper" -> {
                    if (args.length == 2) {
                        out.addAll(List.of("join", "leave"));
                    } else {
                        Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                    }
                }
                case "court" -> {
                    if (args.length == 2) {
                        out.addAll(List.of("create", "remove", "list"));
                    } else if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
                        out.addAll(courts);
                    } else if (args.length == 4 && args[1].equalsIgnoreCase("create")) {
                        Arrays.stream(CourtPreset.values()).forEach(c -> out.add(c.name().toLowerCase(Locale.ROOT)));
                    }
                }
                case "scoreboard" -> {
                    if (args.length == 2) {
                        out.addAll(List.of("create", "remove"));
                    } else if (args.length == 4) {
                        out.addAll(courts);
                    }
                }
                default -> {
                }
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).distinct().collect(Collectors.toList());
    }
}
