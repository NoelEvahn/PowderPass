package dev.powderpass.volleyball.math;

/**
 * Court-local coordinate frame.
 * <ul>
 *   <li>{@code u}: along the court length, the net lies at u = 0. Side A is u &lt; 0, side B is u &gt; 0.</li>
 *   <li>{@code w}: across the court width.</li>
 *   <li>{@code y}: height above the playing surface.</li>
 * </ul>
 * Courts are aligned to the world axes (rotations in 90 degree steps), so the transform is exact.
 */
public final class CourtFrame {

    private final double originX;
    private final double originY;
    private final double originZ;
    /** Unit vector of +u in world space (one of +-X / +-Z). */
    private final int lx;
    private final int lz;

    public CourtFrame(double originX, double originY, double originZ, int lengthDirX, int lengthDirZ) {
        if (Math.abs(lengthDirX) + Math.abs(lengthDirZ) != 1) {
            throw new IllegalArgumentException("length direction must be a unit world axis");
        }
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.lx = lengthDirX;
        this.lz = lengthDirZ;
    }

    public double originX() {
        return originX;
    }

    public double originY() {
        return originY;
    }

    public double originZ() {
        return originZ;
    }

    public int lengthDirX() {
        return lx;
    }

    public int lengthDirZ() {
        return lz;
    }

    /** +w direction in world space: the length direction rotated 90 degrees. */
    public int widthDirX() {
        return -lz;
    }

    public int widthDirZ() {
        return lx;
    }

    public double toU(double wx, double wz) {
        return (wx - originX) * lx + (wz - originZ) * lz;
    }

    public double toW(double wx, double wz) {
        return (wx - originX) * widthDirX() + (wz - originZ) * widthDirZ();
    }

    public double toLocalY(double wy) {
        return wy - originY;
    }

    /** World -> local (u, y, w) written into {@code out} as (x=u, y=y, z=w). */
    public Vec3 toLocal(Vec3 world, Vec3 out) {
        double u = toU(world.x, world.z);
        double w = toW(world.x, world.z);
        return out.set(u, world.y - originY, w);
    }

    /** Local (x=u, y, z=w) -> world. */
    public Vec3 toWorld(Vec3 local, Vec3 out) {
        return toWorld(local.x, local.y, local.z, out);
    }

    public Vec3 toWorld(double u, double y, double w, Vec3 out) {
        double x = originX + u * lx + w * widthDirX();
        double z = originZ + u * lz + w * widthDirZ();
        return out.set(x, originY + y, z);
    }

    /** Rotates a local direction (x=u, y, z=w) into world space. */
    public Vec3 dirToWorld(double du, double dy, double dw, Vec3 out) {
        return out.set(du * lx + dw * widthDirX(), dy, du * lz + dw * widthDirZ());
    }

    /** Rotates a world direction into local (x=u, y, z=w). */
    public Vec3 dirToLocal(Vec3 dir, Vec3 out) {
        double u = dir.x * lx + dir.z * lz;
        double w = dir.x * widthDirX() + dir.z * widthDirZ();
        return out.set(u, dir.y, w);
    }

    /** Minecraft yaw (degrees) of a horizontal local direction. */
    public float yawOf(double du, double dw) {
        Vec3 d = dirToWorld(du, 0, dw, new Vec3());
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }
}
