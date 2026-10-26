package dev.powderpass.volleyball.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Chat formatting helpers. */
public final class Msg {

    public static final MiniMessage MM = MiniMessage.miniMessage();
    private static Component prefix = MM.deserialize("<gradient:#ffd34d:#4da6ff><b>Volleyball</b></gradient> <dark_gray>»</dark_gray> ");

    private Msg() {
    }

    public static void setPrefix(String mini) {
        prefix = MM.deserialize(mini);
    }

    public static Component mm(String mini, TagResolver... resolvers) {
        return MM.deserialize(mini, resolvers);
    }

    public static void send(Audience to, String mini, TagResolver... resolvers) {
        to.sendMessage(prefix.append(MM.deserialize(mini, resolvers)));
    }

    public static void send(Audience to, Component c) {
        to.sendMessage(prefix.append(c));
    }

    public static void error(Audience to, String mini, TagResolver... resolvers) {
        to.sendMessage(prefix.append(MM.deserialize("<red>" + mini, resolvers)));
    }

    public static String escape(String s) {
        return MM.escapeTags(s);
    }
}
