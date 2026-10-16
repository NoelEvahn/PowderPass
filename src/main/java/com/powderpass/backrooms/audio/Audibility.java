package com.powderpass.backrooms.audio;

/**
 * How loud a positional sound really is on a vanilla client, and the volume to send to get a wanted loudness.
 * <p>
 * The client attenuates linearly to zero at {@code attenuation_distance x max(volume, 1)} blocks (16 by default,
 * and the pack sets no other value), and caps loudness at 1. So a sound sent at volume 0.8 from 20 blocks away is
 * <b>silent</b>, and one at 0.5 from 12 blocks away is barely there (0.12). This was why distant events, PA voices
 * and creature sounds were inaudible. Volumes above 1 do not make a sound louder, they only carry it further:
 * heard gain = {@code min(volume,1) * (1 - d / (16 * max(volume,1)))}.
 */
public final class Audibility {
    /** Attenuation distance at volume 1 (vanilla default; the pack does not override it). */
    public static final double RANGE = 16.0;

    private Audibility() {
    }

    /** What a listener {@code distance} blocks away hears from a sound sent at {@code volume}. */
    public static double heard(double volume, double distance) {
        double range = RANGE * Math.max(1, volume);
        return Math.max(0, Math.min(1, volume) * (1 - distance / range));
    }

    /** The volume to send so that a listener {@code distance} blocks away hears {@code gain} (0..1). */
    public static float volumeFor(double gain, double distance) {
        gain = Math.max(0.005, Math.min(1, gain));
        if (distance <= 0.25) return (float) gain;
        double att = 1 - distance / RANGE;
        if (att > 0 && gain / att <= 1) return (float) (gain / att);
        // carried further by a volume above 1: 1 - d / (16 v) = gain  ->  v = d / (16 (1 - gain))
        double v = distance / (RANGE * (1 - Math.min(gain, 0.97)));
        return (float) Math.max(1, Math.min(v, 16));
    }

    /**
     * A sound that should be heard within {@code range} blocks of its source with {@code gain} at the source: the
     * volume that makes it reach that far (natural fall-off, loud close up).
     */
    public static float volumeForRange(double gain, double range) {
        return (float) Math.max(Math.min(1, gain), range / RANGE);
    }
}
