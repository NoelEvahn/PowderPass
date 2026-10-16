package com.powderpass.backrooms.display;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Procedural animation of one part group. Evaluated on the main thread every animation interval and sent to
 * clients with display interpolation, so motion stays smooth between server updates.
 */
public record Anim(int group, Type type, Vector3f axis, Vector3f pivot, float amp, float period, float phase) {
    public enum Type {
        /** continuous rotation around axis; amp = direction multiplier (negative = reverse) */
        SPIN,
        /** sinusoidal rotation around axis, amp = degrees */
        SWAY,
        /** sinusoidal translation along axis, amp = blocks */
        BOB,
        /** random small translation each update (vibration), amp = blocks */
        JITTER,
        /** ping-pong translation along axis (pistons), amp = blocks */
        SLIDE,
        /** circular drift in the plane perpendicular to axis, amp = radius */
        ORBIT,
        /** randomly swap block with alt; amp = probability per update */
        BLINK,
        /** brightness flicker; amp = probability of dropping to dim */
        FLICKER,
        /** one-way translation along axis, amp blocks per period, then a jump back that is not interpolated (running water) */
        FLOW
    }

    public boolean transforms() {
        return type != Type.BLINK && type != Type.FLICKER;
    }

    /** A FLOW animation that started a new cycle since the previous update: its jump back must not be interpolated. */
    public boolean wrapped(long t, int interval) {
        if (type != Type.FLOW) return false;
        return Math.floor((t - interval) / (double) period + phase) != Math.floor(t / (double) period + phase);
    }

    /** Pose matrix at time t (ticks). Steps must stay under 180 degrees per update for clean interpolation. */
    public Matrix4f pose(long t) {
        Matrix4f m = new Matrix4f();
        double ph = (t / (double) period + phase) * Math.PI * 2;
        switch (type) {
            case SPIN -> {
                float ang = (float) (ph * amp);
                m.translate(pivot).rotate(ang, axis).translate(-pivot.x, -pivot.y, -pivot.z);
            }
            case SWAY -> {
                float ang = (float) Math.toRadians(amp * Math.sin(ph));
                m.translate(pivot).rotate(ang, axis).translate(-pivot.x, -pivot.y, -pivot.z);
            }
            case BOB -> {
                float d = (float) (amp * Math.sin(ph));
                m.translate(axis.x * d, axis.y * d, axis.z * d);
            }
            case SLIDE -> {
                double tri = Math.abs(((t / (double) period + phase) % 1.0) * 2 - 1); // 1..0..1
                float d = (float) (amp * (1 - tri));
                m.translate(axis.x * d, axis.y * d, axis.z * d);
            }
            case FLOW -> {
                float d = (float) (amp * (((t / (double) period + phase) % 1.0 + 1.0) % 1.0));
                m.translate(axis.x * d, axis.y * d, axis.z * d);
            }
            case JITTER -> {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                m.translate((float) (r.nextGaussian() * amp), (float) (r.nextGaussian() * amp * 0.5), (float) (r.nextGaussian() * amp));
            }
            case ORBIT -> {
                Vector3f a = new Vector3f(axis).normalize();
                Vector3f u = Math.abs(a.y) > 0.9 ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
                Vector3f v = new Vector3f(a).cross(u).normalize();
                u = new Vector3f(v).cross(a).normalize();
                float c = (float) (Math.cos(ph) * amp), s = (float) (Math.sin(ph) * amp);
                m.translate(u.x * c + v.x * s, u.y * c + v.y * s, u.z * c + v.z * s);
            }
            default -> {
            }
        }
        return m;
    }
}
