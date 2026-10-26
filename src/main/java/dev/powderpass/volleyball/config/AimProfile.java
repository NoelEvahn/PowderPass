package dev.powderpass.volleyball.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Maps the player's view pitch to a landing distance.
 * Minecraft pitch: +90 looks straight down, -90 straight up.
 */
public record AimProfile(double near, double far, double pitchNear, double pitchFar, double lookUp) {

    /** Pitch below which the player is assumed to be watching the ball instead of aiming. */
    public static final double LOOK_UP_PITCH = -32;

    public double distanceFor(float pitch) {
        if (pitch < LOOK_UP_PITCH) {
            return lookUp;
        }
        double f = (pitchNear - pitch) / (pitchNear - pitchFar);
        f = Math.max(0, Math.min(1, f));
        return near + (far - near) * f;
    }

    static AimProfile load(ConfigurationSection s, AimProfile def) {
        if (s == null) {
            return def;
        }
        return new AimProfile(
                s.getDouble("near", def.near()),
                s.getDouble("far", def.far()),
                s.getDouble("pitch-near", def.pitchNear()),
                s.getDouble("pitch-far", def.pitchFar()),
                s.getDouble("look-up", def.lookUp()));
    }
}
