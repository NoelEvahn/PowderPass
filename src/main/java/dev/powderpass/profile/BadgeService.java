package dev.powderpass.profile;

import dev.powderpass.config.Messages;
import dev.powderpass.physics.TrickResult;
import dev.powderpass.util.MathUtil;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

/** Awards badges when their criteria are met and celebrates them. */
public final class BadgeService {

    private final Messages messages;

    public BadgeService(Messages messages) {
        this.messages = messages;
    }

    public void award(Player player, PlayerData data, Badge badge) {
        if (!data.badges.add(badge)) {
            return;
        }
        data.markDirty();
        messages.send(player, "badge.unlocked", Messages.p("badge", badge.display()), Messages.p("description", badge.description()));
        for (SuitPalette suit : SuitPalette.values()) {
            if (suit.unlock() == badge) {
                messages.send(player, "badge.suit-unlocked", Messages.p("suit", suit.display()));
            }
        }
        player.showTitle(Title.title(messages.get("badge.title"),
                messages.get("badge.subtitle", Messages.p("badge", badge.display())),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1800), Duration.ofMillis(500))));
        player.playSound(Sound.sound(org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, Sound.Source.PLAYER, 0.8f, 1.2f));
    }

    public void onSpeed(Player p, PlayerData d, double blocksPerTick) {
        double kmh = MathUtil.toKmh(blocksPerTick);
        if (kmh >= 60) {
            award(p, d, Badge.CRUISER);
        }
        if (kmh >= 100) {
            award(p, d, Badge.SPEED_DEMON);
        }
        if (kmh >= 130) {
            award(p, d, Badge.TERMINAL_VELOCITY);
        }
    }

    public void onAir(Player p, PlayerData d, int airTicks) {
        if (airTicks >= 40) {
            award(p, d, Badge.HANG_TIME);
        }
        if (airTicks >= 80) {
            award(p, d, Badge.BIG_AIR);
        }
    }

    public void onTrick(Player p, PlayerData d, TrickResult trick) {
        if (trick.spinDegrees() >= 360) {
            award(p, d, Badge.FULL_CIRCLE);
        }
        if (trick.spinDegrees() >= 1080) {
            award(p, d, Badge.TEN_EIGHTY);
        }
        int flips = Math.abs(trick.flips());
        if (flips >= 1) {
            award(p, d, Badge.FLIP_SIDE);
        }
        if (flips >= 2) {
            award(p, d, Badge.DOUBLE_TROUBLE);
        }
        if (trick.switchLanding() && (trick.spinDegrees() > 0 || flips > 0)) {
            award(p, d, Badge.SWITCH_HITTER);
        }
    }

    public void onCombo(Player p, PlayerData d, long total) {
        if (total >= 2500) {
            award(p, d, Badge.COMBO_KING);
        }
    }

    public void onTotals(Player p, PlayerData d) {
        if (d.vertical >= 1000) {
            award(p, d, Badge.VERTICAL_KILOMETRE);
        }
        if (d.distance >= 42_000) {
            award(p, d, Badge.MARATHON);
        }
        if (d.powderDistance >= 500) {
            award(p, d, Badge.POWDER_HOUND);
        }
        if (d.liftRides >= 25) {
            award(p, d, Badge.CHAIR_WARMER);
        }
        if (d.crashes >= 10) {
            award(p, d, Badge.YARD_SALE);
        }
    }
}
