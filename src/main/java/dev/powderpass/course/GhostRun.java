package dev.powderpass.course;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A recorded run, one frame per tick, used to replay a translucent "ghost" of the course record.
 * Positions are stored as floats relative to an origin to keep precision while staying compact.
 */
public final class GhostRun {

    private static final int MAGIC = 0x50504748; // "PPGH"
    private static final int VERSION = 1;

    /** One interpolated pose of the ghost. */
    public record Frame(double x, double y, double z, float yaw, float flip, float crouch, float lean,
                        boolean airborne, boolean board) {
    }

    private final double ox;
    private final double oy;
    private final double oz;
    private int size;
    private float[] xs;
    private float[] ys;
    private float[] zs;
    private float[] yaws;
    private float[] flips;
    private byte[] crouches;
    private byte[] leans;
    private byte[] flags;
    private final int maxFrames;

    public GhostRun(double ox, double oy, double oz, int maxFrames) {
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.maxFrames = maxFrames;
        int initial = Math.min(maxFrames, 256);
        xs = new float[initial];
        ys = new float[initial];
        zs = new float[initial];
        yaws = new float[initial];
        flips = new float[initial];
        crouches = new byte[initial];
        leans = new byte[initial];
        flags = new byte[initial];
    }

    /** Appends a frame. Returns false (and records nothing) once the frame limit is reached. */
    public boolean add(double x, double y, double z, double yaw, double flip, double crouch, double lean,
                       boolean airborne, boolean board) {
        if (size >= maxFrames) {
            return false;
        }
        if (size == xs.length) {
            int cap = Math.min(maxFrames, xs.length * 2);
            xs = Arrays.copyOf(xs, cap);
            ys = Arrays.copyOf(ys, cap);
            zs = Arrays.copyOf(zs, cap);
            yaws = Arrays.copyOf(yaws, cap);
            flips = Arrays.copyOf(flips, cap);
            crouches = Arrays.copyOf(crouches, cap);
            leans = Arrays.copyOf(leans, cap);
            flags = Arrays.copyOf(flags, cap);
        }
        xs[size] = (float) (x - ox);
        ys[size] = (float) (y - oy);
        zs[size] = (float) (z - oz);
        yaws[size] = (float) yaw;
        flips[size] = (float) flip;
        crouches[size] = (byte) Math.round(Math.max(0.0, Math.min(1.0, crouch)) * 100.0);
        leans[size] = (byte) Math.round(Math.max(-60.0, Math.min(60.0, lean)) * 2.0);
        flags[size] = (byte) ((airborne ? 1 : 0) | (board ? 2 : 0));
        size++;
        return true;
    }

    public int size() {
        return size;
    }

    public boolean full() {
        return size >= maxFrames;
    }

    /** Samples the ghost at a fractional frame index, interpolating position and angles. */
    public Frame sample(double index) {
        if (size == 0) {
            return new Frame(ox, oy, oz, 0f, 0f, 0f, 0f, false, false);
        }
        double clamped = Math.max(0.0, Math.min(size - 1, index));
        int i = (int) Math.floor(clamped);
        int j = Math.min(size - 1, i + 1);
        float t = (float) (clamped - i);
        float yawDelta = wrap(yaws[j] - yaws[i]);
        return new Frame(
                ox + lerp(xs[i], xs[j], t),
                oy + lerp(ys[i], ys[j], t),
                oz + lerp(zs[i], zs[j], t),
                yaws[i] + yawDelta * t,
                lerp(flips[i], flips[j], t),
                lerp(crouches[i], crouches[j], t) / 100f,
                lerp(leans[i], leans[j], t) / 2f,
                (flags[i] & 1) != 0,
                (flags[i] & 2) != 0);
    }

    public void write(OutputStream raw) throws IOException {
        GZIPOutputStream gzip = new GZIPOutputStream(raw);
        DataOutputStream out = new DataOutputStream(gzip);
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeDouble(ox);
        out.writeDouble(oy);
        out.writeDouble(oz);
        out.writeInt(maxFrames);
        out.writeInt(size);
        for (int k = 0; k < size; k++) {
            out.writeFloat(xs[k]);
            out.writeFloat(ys[k]);
            out.writeFloat(zs[k]);
            out.writeFloat(yaws[k]);
            out.writeFloat(flips[k]);
            out.writeByte(crouches[k]);
            out.writeByte(leans[k]);
            out.writeByte(flags[k]);
        }
        out.flush();
        gzip.finish();
    }

    public static GhostRun read(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(new GZIPInputStream(raw));
        if (in.readInt() != MAGIC) {
            throw new IOException("Not a PowderPass ghost file");
        }
        int version = in.readInt();
        if (version != VERSION) {
            throw new IOException("Unsupported ghost version " + version);
        }
        double ox = in.readDouble();
        double oy = in.readDouble();
        double oz = in.readDouble();
        int maxFrames = in.readInt();
        int size = in.readInt();
        if (size < 0 || maxFrames < 0 || size > maxFrames || maxFrames > 1_000_000) {
            throw new IOException("Corrupt ghost header");
        }
        GhostRun run = new GhostRun(ox, oy, oz, maxFrames);
        for (int k = 0; k < size; k++) {
            float x = in.readFloat();
            float y = in.readFloat();
            float z = in.readFloat();
            float yaw = in.readFloat();
            float flip = in.readFloat();
            byte crouch = in.readByte();
            byte lean = in.readByte();
            byte flag = in.readByte();
            run.add(ox + x, oy + y, oz + z, yaw, flip, crouch / 100.0, lean / 2.0, (flag & 1) != 0, (flag & 2) != 0);
        }
        return run;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) {
            d -= 360f;
        } else if (d < -180f) {
            d += 360f;
        }
        return d;
    }
}
