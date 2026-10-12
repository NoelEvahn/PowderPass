package dev.powderpass.command;

import dev.powderpass.PowderPassPlugin;
import dev.powderpass.config.Messages;
import dev.powderpass.course.Course;
import dev.powderpass.gear.Gear;
import dev.powderpass.gear.GearTier;
import dev.powderpass.gear.GearType;
import dev.powderpass.gui.MainMenu;
import dev.powderpass.lift.Lift;
import dev.powderpass.lift.LiftDefinition;
import dev.powderpass.lift.LiftStyle;
import dev.powderpass.pass.PassType;
import dev.powderpass.profile.PlayerData;
import dev.powderpass.ski.SkiSession;
import dev.powderpass.util.Text;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** /powderpass (alias /pp): everything from opening the menu to building lifts. */
public final class PowderPassCommand implements BasicCommand {

    private final PowderPassPlugin plugin;

    public PowderPassCommand(PowderPassPlugin plugin) {
        this.plugin = plugin;
    }

    private Messages m() {
        return plugin.messages();
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> menu(sender);
            case "help" -> help(sender);
            case "stats" -> stats(sender, args);
            case "unclip" -> unclip(sender);
            case "give" -> give(sender, args);
            case "lift" -> lift(sender, args);
            case "course" -> course(sender, args);
            case "kiosk" -> kiosk(sender, args);
            case "reload" -> reload(sender);
            default -> help(sender);
        }
    }

    // ------------------------------------------------------------------------------------------------ players

    private Player player(CommandSender sender) {
        if (sender instanceof Player p) {
            return p;
        }
        m().send(sender, "player-only");
        return null;
    }

    private boolean perm(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        m().send(sender, "no-permission");
        return false;
    }

    private void menu(CommandSender sender) {
        Player p = player(sender);
        if (p != null && perm(p, "powderpass.menu")) {
            new MainMenu(plugin, p).open(p);
        }
    }

    private void help(CommandSender sender) {
        for (String line : m().raw("help").split("\n")) {
            sender.sendMessage(Text.mm(line));
        }
        if (sender.hasPermission("powderpass.admin")) {
            for (String line : m().raw("help-admin").split("\n")) {
                sender.sendMessage(Text.mm(line));
            }
        }
    }

    private void stats(CommandSender sender, String[] args) {
        PlayerData data;
        if (args.length >= 2) {
            if (!perm(sender, "powderpass.stats.others")) {
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (target == null) {
                m().send(sender, "unknown-player", Messages.p("player", args[1]));
                return;
            }
            Player online = target.getPlayer();
            data = online != null ? plugin.profiles().get(online) : plugin.profiles().readOffline(target.getUniqueId(), args[1]);
        } else {
            Player p = player(sender);
            if (p == null) {
                return;
            }
            data = plugin.profiles().get(p);
        }
        sender.sendMessage(Text.mm("<gradient:#38bdf8:#e0f2fe><bold>⛷ " + Text.escape(data.name()) + "</bold></gradient> <dark_gray>— <gray>"
                + data.badges.size() + " badges"));
        for (String line : MainMenuLines.of(data)) {
            sender.sendMessage(Text.mm("  " + line));
        }
    }

    private void unclip(CommandSender sender) {
        Player p = player(sender);
        if (p == null) {
            return;
        }
        SkiSession s = plugin.skis().session(p);
        if (s == null) {
            m().send(p, "ski.not-skiing");
            return;
        }
        plugin.skis().end(s, SkiSession.EndReason.UNCLIP, true);
    }

    private void give(CommandSender sender, String[] args) {
        if (!perm(sender, "powderpass.admin.give")) {
            return;
        }
        if (args.length < 3) {
            m().send(sender, "usage", Messages.p("usage", "/pp give <player> <skis|snowboard|pass> [tier|day|season]"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            m().send(sender, "unknown-player", Messages.p("player", args[1]));
            return;
        }
        String what = args[2].toLowerCase(Locale.ROOT);
        ItemStack item;
        String name;
        if (what.equals("pass")) {
            PassType type = args.length >= 4 ? PassType.byId(args[3]) : PassType.DAY;
            if (type == null) {
                m().send(sender, "usage", Messages.p("usage", "/pp give <player> pass <day|season>"));
                return;
            }
            item = plugin.passes().create(target, type);
            name = type.display();
        } else {
            GearType type = GearType.byId(what);
            GearTier tier = args.length >= 4 ? GearTier.byId(args[3]) : GearTier.ALPINE;
            if (type == null || tier == null) {
                m().send(sender, "usage", Messages.p("usage", "/pp give <player> <skis|snowboard> <timber|alpine|glacier|avalanche>"));
                return;
            }
            Gear gear = new Gear(type, tier);
            item = plugin.gear().create(gear);
            name = gear.displayName();
        }
        for (ItemStack overflow : target.getInventory().addItem(item).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), overflow);
        }
        m().send(sender, "gave", Messages.p("item", name), Messages.p("player", target.getName()));
    }

    private void reload(CommandSender sender) {
        if (perm(sender, "powderpass.admin.reload")) {
            plugin.reloadEverything();
            m().send(sender, "reloaded");
        }
    }

    // ------------------------------------------------------------------------------------------------ lifts

    private void lift(CommandSender sender, String[] args) {
        if (!perm(sender, "powderpass.admin.lift")) {
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "create" -> {
                Player p = player(sender);
                if (p == null) {
                    return;
                }
                if (args.length < 3) {
                    m().send(p, "usage", Messages.p("usage", "/pp lift create <name>"));
                } else if (plugin.lifts().beginSurvey(p, args[2])) {
                    m().send(p, "lift.survey-started", Messages.p("lift", args[2]));
                } else {
                    m().send(p, "invalid-name");
                }
            }
            case "top" -> {
                Player p = player(sender);
                if (p == null) {
                    return;
                }
                String error = plugin.lifts().finishSurvey(p);
                if (error == null) {
                    m().send(p, "lift.built");
                } else {
                    p.sendMessage(Text.mm(m().raw("prefix") + "<red>" + Text.escape(error)));
                }
            }
            case "cancel" -> {
                Player p = player(sender);
                if (p != null) {
                    plugin.lifts().forgetSurvey(p.getUniqueId());
                    m().send(p, "lift.survey-cancelled");
                }
            }
            case "list" -> {
                Collection<LiftDefinition> defs = plugin.lifts().definitions();
                m().send(sender, "lift.list-header", Messages.p("count", defs.size()));
                for (LiftDefinition d : defs) {
                    Lift lift = plugin.lifts().lift(d.name);
                    sender.sendMessage(Text.mm(" <dark_gray>• <white>" + Text.escape(d.name) + " <gray>" + d.world + " "
                            + Math.round(d.horizontalLength()) + "m " + (lift == null ? "<red>offline" : lift.active() ? "<green>running" : "<yellow>idle")));
                }
            }
            case "info" -> {
                LiftDefinition d = args.length >= 3 ? plugin.lifts().definition(args[2]) : null;
                if (d == null) {
                    m().send(sender, "lift.unknown");
                    return;
                }
                Lift lift = plugin.lifts().lift(d.name);
                sender.sendMessage(Text.mm("<#4ade80><bold>🚡 " + Text.escape(d.name)));
                sender.sendMessage(Text.mm("<gray>Style <white>" + d.style + " <gray>speed <white>" + d.speed + " <gray>spacing <white>"
                        + d.spacing + " <gray>seats <white>" + d.seats + " <gray>pass <white>" + (d.requirePass == null ? "default" : d.requirePass)));
                if (lift != null) {
                    sender.sendMessage(Text.mm("<gray>Towers <white>" + lift.layout().towers().size() + " <gray>chairs <white>"
                            + lift.chairCount() + " <gray>rope <white>" + Math.round(lift.layout().path().length()) + "m"));
                }
            }
            case "remove" -> {
                LiftDefinition d = args.length >= 3 ? plugin.lifts().definition(args[2]) : null;
                if (d == null) {
                    m().send(sender, "lift.unknown");
                    return;
                }
                plugin.lifts().remove(d);
                m().send(sender, "lift.removed", Messages.p("lift", d.name));
            }
            case "rebuild" -> {
                LiftDefinition d = args.length >= 3 ? plugin.lifts().definition(args[2]) : null;
                if (d == null) {
                    m().send(sender, "lift.unknown");
                    return;
                }
                m().send(sender, plugin.lifts().resample(d) ? "lift.rebuilt" : "lift.unloaded-short");
            }
            case "set" -> liftSet(sender, args);
            default -> m().send(sender, "usage", Messages.p("usage", "/pp lift <create|top|cancel|list|info|set|rebuild|remove>"));
        }
    }

    private void liftSet(CommandSender sender, String[] args) {
        if (args.length < 5) {
            m().send(sender, "usage", Messages.p("usage", "/pp lift set <name> <speed|spacing|seats|style|pass> <value>"));
            return;
        }
        LiftDefinition d = plugin.lifts().definition(args[2]);
        if (d == null) {
            m().send(sender, "lift.unknown");
            return;
        }
        String key = args[3].toLowerCase(Locale.ROOT);
        String value = args[4];
        try {
            switch (key) {
                case "speed" -> d.speed = clamp(Double.parseDouble(value), 0.03, 0.8);
                case "spacing" -> d.spacing = clamp(Double.parseDouble(value), 6, 80);
                case "seats" -> d.seats = (int) clamp(Integer.parseInt(value), 1, 4);
                case "style" -> d.style = LiftStyle.valueOf(value.toUpperCase(Locale.ROOT));
                case "pass" -> d.requirePass = value.equalsIgnoreCase("default") ? null : Boolean.parseBoolean(value);
                default -> {
                    m().send(sender, "usage", Messages.p("usage", "/pp lift set <name> <speed|spacing|seats|style|pass> <value>"));
                    return;
                }
            }
        } catch (IllegalArgumentException e) {
            m().send(sender, "invalid-value", Messages.p("value", value));
            return;
        }
        plugin.lifts().changed(d);
        m().send(sender, "lift.updated", Messages.p("lift", d.name), Messages.p("key", key), Messages.p("value", value));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // ------------------------------------------------------------------------------------------------ courses

    private void course(CommandSender sender, String[] args) {
        if (!perm(sender, "powderpass.admin.course")) {
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if (action.equals("list")) {
            Collection<Course> courses = plugin.courses().courses();
            m().send(sender, "course.list-header", Messages.p("count", courses.size()));
            for (Course c : courses) {
                Course.Entry rec = c.record();
                sender.sendMessage(Text.mm(" <dark_gray>• <white>" + Text.escape(c.name()) + " <gray>" + c.slalomGates().size() + " gates "
                        + (c.complete() ? "" : "<red>(no finish) ") + (rec == null ? "" : "<#facc15>★ " + Text.raceTime(rec.ticks()))));
            }
            return;
        }
        if (args.length < 3) {
            m().send(sender, "usage", Messages.p("usage", "/pp course <create|gate|finish|undo|reset|remove|list> <name>"));
            return;
        }
        String name = args[2];
        if (action.equals("create")) {
            Player p = player(sender);
            if (p == null) {
                return;
            }
            if (plugin.courses().create(p, name)) {
                m().send(p, "course.created", Messages.p("course", name));
            } else {
                m().send(p, "invalid-name");
            }
            return;
        }
        Course c = plugin.courses().get(name);
        if (c == null) {
            m().send(sender, "course.unknown");
            return;
        }
        switch (action) {
            case "gate" -> {
                Player p = player(sender);
                if (p != null && sameWorld(p, c)) {
                    int n = plugin.courses().addGate(p, c);
                    m().send(p, "course.gate-added", Messages.p("number", n), Messages.p("course", c.name()));
                }
            }
            case "finish" -> {
                Player p = player(sender);
                if (p != null && sameWorld(p, c)) {
                    plugin.courses().setFinish(p, c);
                    m().send(p, "course.finish-set", Messages.p("course", c.name()));
                }
            }
            case "undo" -> m().send(sender, plugin.courses().undo(c) ? "course.undone" : "course.nothing-to-undo");
            case "reset" -> {
                plugin.courses().reset(c);
                m().send(sender, "course.reset", Messages.p("course", c.name()));
            }
            case "remove" -> {
                plugin.courses().remove(c);
                m().send(sender, "course.removed", Messages.p("course", c.name()));
            }
            default -> m().send(sender, "usage", Messages.p("usage", "/pp course <create|gate|finish|undo|reset|remove|list> <name>"));
        }
    }

    private boolean sameWorld(Player p, Course c) {
        if (p.getWorld().getName().equals(c.world())) {
            return true;
        }
        m().send(p, "course.other-world");
        return false;
    }

    // ------------------------------------------------------------------------------------------------ kiosks

    private void kiosk(CommandSender sender, String[] args) {
        if (!perm(sender, "powderpass.admin.kiosk")) {
            return;
        }
        Player p = player(sender);
        if (p == null) {
            return;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "create" -> {
                plugin.kiosks().create(p);
                m().send(p, "kiosk.created");
            }
            case "remove" -> m().send(p, plugin.kiosks().removeNearest(p) ? "kiosk.removed" : "kiosk.none-nearby");
            default -> m().send(p, "usage", Messages.p("usage", "/pp kiosk <create|remove>"));
        }
    }

    // ------------------------------------------------------------------------------------------------ completion

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender s = source.getSender();
        List<String> out = new ArrayList<>();
        if (args.length <= 1) {
            out.addAll(List.of("menu", "help", "stats", "unclip"));
            if (s.hasPermission("powderpass.admin.give")) {
                out.add("give");
            }
            if (s.hasPermission("powderpass.admin.lift")) {
                out.add("lift");
            }
            if (s.hasPermission("powderpass.admin.course")) {
                out.add("course");
            }
            if (s.hasPermission("powderpass.admin.kiosk")) {
                out.add("kiosk");
            }
            if (s.hasPermission("powderpass.admin.reload")) {
                out.add("reload");
            }
            return filter(out, args.length == 0 ? "" : args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String last = args[args.length - 1];
        switch (sub) {
            case "stats" -> {
                if (args.length == 2 && s.hasPermission("powderpass.stats.others")) {
                    return filter(onlineNames(), last);
                }
            }
            case "give" -> {
                if (!s.hasPermission("powderpass.admin.give")) {
                    return List.of();
                }
                if (args.length == 2) {
                    return filter(onlineNames(), last);
                }
                if (args.length == 3) {
                    return filter(List.of("skis", "snowboard", "pass"), last);
                }
                if (args.length == 4) {
                    return filter(args[2].equalsIgnoreCase("pass") ? List.of("day", "season")
                            : Arrays.stream(GearTier.values()).map(GearTier::id).toList(), last);
                }
            }
            case "lift" -> {
                if (!s.hasPermission("powderpass.admin.lift")) {
                    return List.of();
                }
                if (args.length == 2) {
                    return filter(List.of("create", "top", "cancel", "list", "info", "set", "rebuild", "remove"), last);
                }
                String action = args[1].toLowerCase(Locale.ROOT);
                if (args.length == 3 && Stream.of("info", "set", "rebuild", "remove").anyMatch(action::equals)) {
                    return filter(plugin.lifts().names(), last);
                }
                if (action.equals("set") && args.length == 4) {
                    return filter(List.of("speed", "spacing", "seats", "style", "pass"), last);
                }
                if (action.equals("set") && args.length == 5) {
                    return switch (args[3].toLowerCase(Locale.ROOT)) {
                        case "style" -> filter(Arrays.stream(LiftStyle.values()).map(Enum::name).toList(), last);
                        case "pass" -> filter(List.of("true", "false", "default"), last);
                        case "seats" -> filter(List.of("1", "2", "3", "4"), last);
                        case "speed" -> filter(List.of("0.15", "0.2", "0.3"), last);
                        case "spacing" -> filter(List.of("10", "14", "20"), last);
                        default -> List.of();
                    };
                }
            }
            case "course" -> {
                if (!s.hasPermission("powderpass.admin.course")) {
                    return List.of();
                }
                if (args.length == 2) {
                    return filter(List.of("create", "gate", "finish", "undo", "reset", "remove", "list"), last);
                }
                if (args.length == 3 && !args[1].equalsIgnoreCase("create")) {
                    return filter(plugin.courses().names(), last);
                }
            }
            case "kiosk" -> {
                if (args.length == 2 && s.hasPermission("powderpass.admin.kiosk")) {
                    return filter(List.of("create", "remove"), last);
                }
            }
            default -> {
            }
        }
        return List.of();
    }

    private static List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }

    /** Shared stat lines (also used by the menu). */
    private static final class MainMenuLines {
        static String[] of(PlayerData d) {
            return dev.powderpass.gui.MainMenu.statLines(d, d.mph);
        }
    }
}
