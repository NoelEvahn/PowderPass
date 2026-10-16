package com.powderpass.backrooms.player;

import com.powderpass.backrooms.BackroomsPlugin;
import com.powderpass.backrooms.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Nobody dies in the Backrooms. A blow that would kill is cancelled and the player gets this instead: the screen
 * goes black, a glitch tears through the sound, and one line ("You can't escape", "You tried", ...) stutters on the
 * screen in corrupted letters that settle into place. Then they wake up wherever {@code death.mode} sends them
 * (the return point, Level 0, or the same level), inventory intact unless {@code death.keep-inventory} is off.
 *
 * <p>Lethal damage is caught on {@link EntityDamageEvent}; anything that kills without damage (a plugin setting
 * health to 0) is caught by cancelling {@link PlayerDeathEvent} as a safety net.</p>
 */
public final class DeathScreen implements Listener {
    /** Length of the sequence before they wake (ticks). */
    public static final int LENGTH = 90;
    private static final String NOISE = "#$%&@!?/\\|<>*+=~^0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final BackroomsPlugin plugin;
    private final Map<UUID, BukkitTask> playing = new HashMap<>();

    public DeathScreen(BackroomsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean playing(Player p) {
        return playing.containsKey(p.getUniqueId());
    }

    public void stop() {
        for (Map.Entry<UUID, BukkitTask> e : new HashMap<>(playing).entrySet()) {
            e.getValue().cancel();
            Player p = plugin.getServer().getPlayer(e.getKey());
            if (p != null) wake(p, plugin.worlds().levelOf(p.getWorld()));
        }
        playing.clear();
    }

    private boolean applies(Player p) {
        return plugin.cfg().deathScreen && plugin.worlds().isBackrooms(p.getWorld())
                && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (playing(p)) {
            e.setCancelled(true); // nothing touches them while it plays
            return;
        }
        if (!applies(p)) return;
        if (e.getFinalDamage() < p.getHealth() + p.getAbsorptionAmount()) return;
        if (holdsTotem(p)) return; // a totem still saves them the vanilla way
        e.setCancelled(true);
        play(p);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!applies(p) && !playing(p)) return;
        e.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            heal(p);
            if (!playing(p)) play(p);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        BukkitTask t = playing.remove(e.getPlayer().getUniqueId());
        if (t == null) return;
        t.cancel();
        wake(e.getPlayer(), plugin.worlds().levelOf(e.getPlayer().getWorld())); // never logs back in mid-death
    }

    private static boolean holdsTotem(Player p) {
        return p.getInventory().getItemInMainHand().getType() == org.bukkit.Material.TOTEM_OF_UNDYING
                || p.getInventory().getItemInOffHand().getType() == org.bukkit.Material.TOTEM_OF_UNDYING;
    }

    private static void heal(Player p) {
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.setFireTicks(0);
        p.setFallDistance(0);
    }

    /** The death screen for this player, now. */
    public void play(Player p) {
        if (playing(p)) return;
        Level level = plugin.worlds().levelOf(p.getWorld());
        Location at = p.getLocation().clone();
        // out of anything holding them
        if (plugin.possessions() != null) plugin.possessions().forget(p);
        if (plugin.vehicles() != null) plugin.vehicles().forget(p);
        if (plugin.slides() != null) plugin.slides().forget(p);
        if (plugin.cutscenes() != null) plugin.cutscenes().forget(p);
        p.leaveVehicle();
        heal(p);
        p.setVelocity(new org.bukkit.util.Vector());
        p.setGravity(false); // hanging in the dark, not still falling into whatever killed them
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, LENGTH + 30, 0, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, LENGTH + 10, 6, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, LENGTH + 10, 4, false, false, false));
        Session s = plugin.sessions().get(p);
        s.inTransition = true;
        if (!plugin.cfg().keepInventory) dropAll(p, at);
        // the glitch: the pack's sound for those who have it, a vanilla stand-in otherwise
        if (plugin.audio() != null && plugin.audio().custom(p) && plugin.audio().index().has("glitch.death")) {
            p.playSound(net.kyori.adventure.sound.Sound.sound(net.kyori.adventure.key.Key.key("backrooms", "glitch.death"),
                    net.kyori.adventure.sound.Sound.Source.MASTER, 1f, 1f), net.kyori.adventure.sound.Sound.Emitter.self());
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.MASTER, 1f, 0.5f);
            p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.MASTER, 0.6f, 0.5f);
        }
        List<String> lines = plugin.cfg().deathLines;
        String line = lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
        String sub = SUBS[ThreadLocalRandom.current().nextInt(SUBS.length)];
        int[] t = {0};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) return;
            t[0] += 2;
            // corruption fades as the line settles; it stutters again before the end
            double settle = t[0] < 50 ? 1 - t[0] / 50.0 : t[0] > LENGTH - 16 ? 0.35 : 0.04;
            Component title = glitch(line, 0.15 + 0.85 * settle, true);
            Component subtitle = t[0] < 24 ? Component.empty() : glitch(sub, 0.1 + 0.6 * settle, false);
            p.showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ZERO, Duration.ofMillis(300), Duration.ofMillis(t[0] >= LENGTH - 2 ? 600 : 0))));
            if (t[0] % 18 == 0 && t[0] < LENGTH - 10) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.MASTER, 0.25f, 0.5f);
            if (t[0] >= LENGTH) {
                BukkitTask self = playing.remove(p.getUniqueId());
                if (self != null) self.cancel();
                wake(p, level);
            }
        }, 1L, 2L);
        playing.put(p.getUniqueId(), task);
    }

    private static final String[] SUBS = {"signal lost", "tape ends", "no signal", "rewinding", "tracking error"};

    /** A line with some letters corrupted (obfuscated noise, shifted colour) and the odd stutter. */
    static Component glitch(String line, double amount, boolean big) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        TextComponent.Builder b = Component.text();
        if (big && r.nextDouble() < amount * 0.5) b.append(Component.text(" ".repeat(r.nextInt(1, 3))));
        TextColor base = big ? TextColor.color(0xE8E2D0) : NamedTextColor.GRAY;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c != ' ' && r.nextDouble() < amount) {
                char n = NOISE.charAt(r.nextInt(NOISE.length()));
                TextColor col = r.nextDouble() < 0.5 ? TextColor.color(0xB0151A) : r.nextBoolean() ? TextColor.color(0x3A3A3A) : base;
                b.append(Component.text(String.valueOf(n), col).decoration(TextDecoration.OBFUSCATED, r.nextDouble() < 0.6));
            } else {
                b.append(Component.text(String.valueOf(c), r.nextDouble() < amount * 0.3 ? TextColor.color(0xB0151A) : base));
            }
            if (big && r.nextDouble() < amount * 0.08) b.append(Component.text(String.valueOf(c), TextColor.color(0x7A0D10))); // a stutter
        }
        return b.build();
    }

    private void dropAll(Player p, Location at) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && !it.getType().isAir() && (plugin.vhs() == null || !plugin.vhs().isVhs(it))) at.getWorld().dropItemNaturally(at, it);
        }
        p.getInventory().clear();
        p.setLevel(0);
        p.setExp(0);
    }

    /** They come to: where {@code death.mode} says (the same choices a real death would have had). */
    private void wake(Player p, Level level) {
        Session s = plugin.sessions().get(p);
        p.setGravity(true);
        heal(p);
        p.setFoodLevel(Math.max(p.getFoodLevel(), 14));
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        Location target = switch (plugin.cfg().deathMode) {
            case "LEVEL0" -> plugin.worlds().levelSpawn(Level.LEVEL_0);
            case "STAY" -> level == null ? null : plugin.worlds().levelSpawn(level);
            default -> null;
        };
        if (target == null) {
            Location home = plugin.sessions().getReturn(p);
            if (home == null) home = plugin.getServer().getWorlds().get(0).getSpawnLocation();
            Location dest = home;
            plugin.teleports().teleportSafe(p, dest, l -> {
                plugin.sessions().clearInside(p);
                s.inTransition = false;
            }).thenAccept(ok -> {
                if (!ok) s.inTransition = false;
            });
        } else {
            Location dest = target;
            plugin.teleports().teleportSafe(p, dest, l -> plugin.sessions().arrived(p, plugin.worlds().levelOf(dest.getWorld())))
                    .thenAccept(ok -> {
                        if (!ok) s.inTransition = false;
                    });
        }
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, false));
    }
}
