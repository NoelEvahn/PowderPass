package dev.powderpass.volleyball.config;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.configuration.ConfigurationSection;

/** A configurable sound. A blank key means "silent". */
public record SoundSpec(Key key, float volume, float pitch) {

    public Sound toSound(float pitchJitter, float volumeScale) {
        return Sound.sound(key, Sound.Source.PLAYER, volume * volumeScale,
                Math.max(0.5f, Math.min(2f, pitch + pitchJitter)));
    }

    @SuppressWarnings("PatternValidation")
    static SoundSpec load(ConfigurationSection s, String defKey, float defVolume, float defPitch) {
        String key = s == null ? defKey : s.getString("key", defKey);
        if (key == null || key.isBlank()) {
            return null;
        }
        Key k;
        try {
            k = Key.key(key);
        } catch (Exception e) {
            k = Key.key(defKey);
        }
        float volume = s == null ? defVolume : (float) s.getDouble("volume", defVolume);
        float pitch = s == null ? defPitch : (float) s.getDouble("pitch", defPitch);
        return new SoundSpec(k, volume, pitch);
    }
}
