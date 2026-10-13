package com.powderpass.backrooms.display;

import org.bukkit.Particle;
import org.bukkit.Sound;

/** Periodic particles and/or sound emitted by a decoration while players are near. */
public record Emitter(Particle particle, Object data, float ox, float oy, float oz, int count, float spread, float speed,
                      int interval, float chance, Sound sound, float volume, float pitch) {
}
