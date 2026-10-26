package dev.powderpass.volleyball.config;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.configuration.ConfigurationSection;

/** A configurable sound. */
public record SoundSpec(Key key, float volume, float pitch) {

    public Sound toSound(float pitchJitter) {
        return Sound.sound(key, Sound.Source.PLAYER, volume, Math.max(0.5f, Math.min(2f, pitch + pitchJitter)));
    }

    @SuppressWarnings("PatternValidation")
    static SoundSpec load(ConfigurationSection s, String def) {
        if (s == null) {
            return new SoundSpec(Key.key(def), 1f, 1f);
        }
        String key = s.getString("key", def);
        Key k;
        try {
            k = Key.key(key);
        } catch (Exception e) {
            k = Key.key(def);
        }
        return new SoundSpec(k, (float) s.getDouble("volume", 1), (float) s.getDouble("pitch", 1));
    }
}
