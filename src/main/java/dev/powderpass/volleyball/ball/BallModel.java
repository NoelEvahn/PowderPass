package dev.powderpass.volleyball.ball;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A voxel sphere split into a few coloured cuboids, so a ball can be drawn with a handful of block displays.
 * <p>
 * Surface voxels are coloured by the axis their outward normal points along (three colours, like the three
 * panel groups of a volleyball). A greedy merge then grows each cuboid as far as possible without covering
 * another colour on the surface; interior voxels are free to merge with anything because they are never seen.
 * Visible faces never overlap, so there is no z-fighting.
 */
public final class BallModel {

    /** A cuboid in unit-sphere coordinates ([-1, 1] on every axis). */
    public record Part(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color) {

        public double sizeX() {
            return maxX - minX;
        }

        public double sizeY() {
            return maxY - minY;
        }

        public double sizeZ() {
            return maxZ - minZ;
        }
    }

    private final int resolution;
    private final List<Part> parts;

    private BallModel(int resolution, List<Part> parts) {
        this.resolution = resolution;
        this.parts = Collections.unmodifiableList(parts);
    }

    public int resolution() {
        return resolution;
    }

    public List<Part> parts() {
        return parts;
    }

    /** Builds a ball {@code resolution} voxels across. Odd sizes merge badly, so they are rounded up to even. */
    public static BallModel build(int resolution) {
        int n = Math.max(4, Math.min(24, resolution));
        n += n % 2;
        double c = (n - 1) / 2.0;
        double radius = n / 2.0 - 0.35;
        boolean[][][] inside = new boolean[n][n][n];
        int[][][] color = new int[n][n][n];
        boolean[][][] surface = new boolean[n][n][n];
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                for (int z = 0; z < n; z++) {
                    double dx = x - c;
                    double dy = y - c;
                    double dz = z - c;
                    inside[x][y][z] = dx * dx + dy * dy + dz * dz <= radius * radius;
                }
            }
        }
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                for (int z = 0; z < n; z++) {
                    if (!inside[x][y][z]) {
                        continue;
                    }
                    if (exposed(inside, n, x, y, z)) {
                        surface[x][y][z] = true;
                        double ax = Math.abs(x - c);
                        double ay = Math.abs(y - c);
                        double az = Math.abs(z - c);
                        color[x][y][z] = ax >= ay && ax >= az ? 0 : (ay >= az ? 1 : 2);
                    }
                }
            }
        }
        boolean[][][] used = new boolean[n][n][n];
        List<Part> parts = new ArrayList<>();
        // sweep bottom-up so caps merge into tall columns
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                for (int z = 0; z < n; z++) {
                    if (!surface[x][y][z] || used[x][y][z]) {
                        continue;
                    }
                    int col = color[x][y][z];
                    int[] b = {x, x + 1, y, y + 1, z, z + 1};
                    boolean grew = true;
                    while (grew) {
                        grew = false;
                        for (int axis = 0; axis < 3; axis++) {
                            for (int dir = 0; dir < 2; dir++) {
                                int[] t = b.clone();
                                if (dir == 0) {
                                    t[axis * 2 + 1]++;
                                } else {
                                    t[axis * 2]--;
                                }
                                if (t[axis * 2] < 0 || t[axis * 2 + 1] > n) {
                                    continue;
                                }
                                if (fits(t, inside, surface, color, used, col)) {
                                    b = t;
                                    grew = true;
                                }
                            }
                        }
                    }
                    for (int i = b[0]; i < b[1]; i++) {
                        for (int j = b[2]; j < b[3]; j++) {
                            for (int k = b[4]; k < b[5]; k++) {
                                if (surface[i][j][k]) {
                                    used[i][j][k] = true;
                                }
                            }
                        }
                    }
                    double s = 2.0 / n;
                    parts.add(new Part(b[0] * s - 1, b[2] * s - 1, b[4] * s - 1, b[1] * s - 1, b[3] * s - 1, b[5] * s - 1, col));
                }
            }
        }
        return new BallModel(n, parts);
    }

    private static boolean exposed(boolean[][][] inside, int n, int x, int y, int z) {
        int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] d : nb) {
            int i = x + d[0];
            int j = y + d[1];
            int k = z + d[2];
            if (i < 0 || j < 0 || k < 0 || i >= n || j >= n || k >= n || !inside[i][j][k]) {
                return true;
            }
        }
        return false;
    }

    private static boolean fits(int[] b, boolean[][][] inside, boolean[][][] surface, int[][][] color,
                                boolean[][][] used, int col) {
        for (int x = b[0]; x < b[1]; x++) {
            for (int y = b[2]; y < b[3]; y++) {
                for (int z = b[4]; z < b[5]; z++) {
                    if (!inside[x][y][z]) {
                        return false;
                    }
                    if (surface[x][y][z] && (color[x][y][z] != col || used[x][y][z])) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
