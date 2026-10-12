package dev.powderpass.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** MiniMessage and number formatting helpers. */
public final class Text {

    public static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component mm(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(miniMessage, resolvers);
    }

    /** Parses MiniMessage for item names and lore: no default italics. */
    public static Component item(String miniMessage, TagResolver... resolvers) {
        return MM.deserialize(miniMessage, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> lore(String... lines) {
        List<Component> out = new ArrayList<>(lines.length);
        for (String line : lines) {
            out.add(item(line));
        }
        return out;
    }

    public static String escape(String raw) {
        return MM.escapeTags(raw);
    }

    /** Formats a race time given in ticks as m:ss.cc */
    public static String raceTime(double ticks) {
        long centis = Math.round(ticks * 5.0);
        long minutes = centis / 6000;
        long seconds = (centis / 100) % 60;
        long cs = centis % 100;
        return String.format(Locale.ROOT, "%d:%02d.%02d", minutes, seconds, cs);
    }

    /** Formats a signed time delta in ticks as +1.23 / -0.45 */
    public static String delta(double ticks) {
        double seconds = ticks / 20.0;
        return String.format(Locale.ROOT, "%s%.2f", seconds >= 0 ? "+" : "-", Math.abs(seconds));
    }

    public static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    public static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    public static String distance(double blocks) {
        if (blocks >= 1000) {
            return String.format(Locale.ROOT, "%.2f km", blocks / 1000.0);
        }
        return Math.round(blocks) + " m";
    }

    public static String speed(double blocksPerTick, boolean mph) {
        double kmh = MathUtil.toKmh(blocksPerTick);
        return mph ? Math.round(kmh * 0.621371) + " mph" : Math.round(kmh) + " km/h";
    }

    public static String duration(long millis) {
        long minutes = Math.max(0, millis) / 60000;
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return hours + "h " + (minutes % 60) + "m";
        }
        return (hours / 24) + "d " + (hours % 24) + "h";
    }
}
