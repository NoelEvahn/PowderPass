package com.powderpass.backrooms.gen;

/**
 * Deterministic description of one display-entity decoration. The generator also uses the
 * {@link #barriers} (invisible collision) and {@link #lights} (invisible light sources) so that
 * purely visual displays get deliberate, honest physical behaviour where gameplay needs it.
 */
public final class DecorSpec {
    public final String model;
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;
    public final float roll;
    public final long seed;
    public int[] barriers = new int[0];
    public int[] lights = new int[0];

    public DecorSpec(String model, double x, double y, double z, float yaw, float pitch, float roll, long seed) {
        this.model = model;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.seed = seed;
    }

    public DecorSpec barrier(int... xyz) {
        int[] n = new int[barriers.length + xyz.length];
        System.arraycopy(barriers, 0, n, 0, barriers.length);
        System.arraycopy(xyz, 0, n, barriers.length, xyz.length);
        barriers = n;
        return this;
    }

    public DecorSpec light(int x, int y, int z, int level) {
        int[] n = new int[lights.length + 4];
        System.arraycopy(lights, 0, n, 0, lights.length);
        n[lights.length] = x;
        n[lights.length + 1] = y;
        n[lights.length + 2] = z;
        n[lights.length + 3] = level;
        lights = n;
        return this;
    }

    /** Stable identity inside its chunk: used to tag spawned entities. */
    public String key() {
        return model + "@" + (int) Math.floor(x * 4) + "," + (int) Math.floor(y * 4) + "," + (int) Math.floor(z * 4);
    }
}
