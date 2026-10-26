package dev.powderpass.volleyball.ui;

import dev.powderpass.volleyball.util.Msg;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/** In-game handbook opened with /vb guide. */
public final class GuideBook {

    private GuideBook() {
    }

    public static void open(Player p) {
        List<Component> pages = List.of(
                page("<b><gold>Volleyball</gold></b>\n\n"
                        + "<black>Quick start:\n\n"
                        + "<dark_aqua>1.</dark_aqua> <black>/vb join <court>\n"
                        + "<dark_aqua>2.</dark_aqua> <black>Host runs /vb start <court>\n"
                        + "<dark_aqua>3.</dark_aqua> <black>Hold the <gold>Hands</gold> item and play!\n\n"
                        + "<dark_gray>Practice anywhere with /vb practice"),
                page("<b>Controls</b>\n\n"
                        + "<dark_green>Left-click</dark_green>\n<black> ground: bump\n air: <red>spike</red>\n\n"
                        + "<dark_green>Right-click</dark_green>\n<black> ground: set\n air at net: <blue>block</blue>\n\n"
                        + "<dark_green>Sneak +</dark_green>\n<black> left: dive / tip\n right: quick set"),
                page("<b>Timing</b>\n\n<black>Click as the ball reaches your hands:\n\n"
                        + "<dark_gray>bump</dark_gray> waist, in front\n<dark_gray>set</dark_gray> above your head\n"
                        + "<dark_gray>spike</dark_gray> top of your jump\n\n"
                        + "<black>Better timing = more power and accuracy. The action bar grades each touch: "
                        + "<gold>PERFECT</gold> <dark_green>GOOD</dark_green> <gold>OK</gold> <red>POOR</red>."),
                page("<b>Aiming</b>\n\n<black>Face where you want the ball to go.\n\n"
                        + "Look <dark_aqua>down</dark_aqua> = shorter\nLook <dark_aqua>ahead</dark_aqua> = longer\n"
                        + "Look <dark_aqua>up at the ball</dark_aqua> = standard distance\n\n"
                        + "<black>The coloured ring on the floor shows your target. Toggle: /vb aim"),
                page("<b>Serving</b>\n\n<black>Hold the ball and wait for the whistle.\n\n"
                        + "<dark_green>Right-click</dark_green> toss\n<dark_green>Sprint + right-click</dark_green> jump toss\n"
                        + "<dark_green>Left-click the ball</dark_green> hit it\n\n"
                        + "<black>Standing = float (wobbles!)\nIn the air = jump topspin\nSneak = swap spin type\n"
                        + "Left-click without toss = underhand"),
                page("<b>Spiking</b>\n\n<black>Sprint at the net and jump for an approach jump (extra height). "
                        + "Hit at the peak. Look down steeply for a sharp angle, ahead for deep shots.\n\n"
                        + "<b>Blocking</b>\n<black>Jump at the net and right-click as the attacker hits."),
                page("<b>Rules</b>\n\n<black>Rally point scoring, sets to 25 (win by 2), deciding set to 15. "
                        + "Max 3 touches, no double touches, no net touches, stay behind the end line when serving. "
                        + "Hosts change rules with /vb rules <court>."),
                page("<b>Hosting</b>\n\n<black>/vb court create\n/vb scoreboard create\n/vb rules <court>\n"
                        + "/vb start <court>\n/vb referee <court>\n\n"
                        + "<black>The whistle opens the referee menu (sneak + right-click)."));
        p.openBook(Book.book(Component.text("Volleyball Guide"), Component.text("PowderPass"), pages));
    }

    private static Component page(String mini) {
        return Msg.mm(mini);
    }
}
