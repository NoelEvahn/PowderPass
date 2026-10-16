package com.powderpass.backrooms.display;

import org.bukkit.Material;
import org.joml.Matrix4f;

/**
 * Classical Greek faces carved in relief, set into walls ({@code statue_face:<scale>:<expression>}).
 * <p>
 * Built like a sculptor blocks out a head: about forty angled planes rather than a voxel height map (which reads
 * as a stepped blob). A raised mask; a forehead leaning back; temples and cheek planes turned away to give the
 * face its roundness; a heavy straight brow over deep sockets with blank eyeballs; a straight Greek nose (a
 * tilted ridge, a ball, nostril wings) in one line with the forehead; high cheekbones; full lips with sharp
 * corners; a rounded chin and jaw lines running back; a cap of hair with a fringe of round curls and heavy locks
 * at the sides. Four expressions: 0 serene, 1 sorrow (inner brows raised, mouth corners down), 2 sleeping
 * (closed lids), 3 singing (lips parted, mouth open). Origin: the wall surface below the chin, face toward +Z;
 * a face {@code scale} blocks tall (chin to crown).
 */
public final class StatueModels {
    private StatueModels() {
    }

    private static final Material STONE = Material.SMOOTH_QUARTZ, HAIR = Material.CALCITE;

    /** One angled block: centre, size, then rotations (degrees) about Y, X and Z through its centre. */
    private static void blk(Model m, Material mat, double cx, double cy, double cz, double sx, double sy, double sz,
                            double ry, double rx, double rz) {
        Matrix4f t = new Matrix4f().translate((float) cx, (float) cy, (float) cz)
                .rotateY((float) Math.toRadians(ry)).rotateX((float) Math.toRadians(rx)).rotateZ((float) Math.toRadians(rz))
                .translate((float) (-sx / 2), (float) (-sy / 2), (float) (-sz / 2)).scale((float) sx, (float) sy, (float) sz);
        m.boxM(mat, t);
    }

    /** Builds a face {@code scale} blocks tall into {@code m}. */
    static void face(Model m, double scale, int expr) {
        m.push().scale(scale / 1.15);
        boolean sorrow = expr == 1, sleeping = expr == 2, singing = expr == 3;
        // the mask everything sits on, sunk a little into the wall
        blk(m, STONE, 0, 0.56, 0.03, 0.62, 0.86, 0.16, 0, 0, 0);
        blk(m, STONE, 0, 0.05, 0.02, 0.3, 0.16, 0.12, 0, 0, 0);                 // the neck below the chin
        // forehead leaning back, temples turned away
        blk(m, STONE, 0, 0.87, 0.14, 0.54, 0.2, 0.12, 0, -10, 0);
        for (int s = -1; s <= 1; s += 2) {
            blk(m, STONE, s * 0.29, 0.82, 0.09, 0.12, 0.26, 0.12, -s * 38, 0, 0);
            // cheek planes and the jaw running back to the ear
            blk(m, STONE, s * 0.2, 0.5, 0.13, 0.22, 0.34, 0.14, -s * 26, 0, 0);
            blk(m, STONE, s * 0.27, 0.32, 0.09, 0.16, 0.22, 0.12, -s * 40, 0, s * 18);
            blk(m, STONE, s * 0.15, 0.2, 0.14, 0.17, 0.11, 0.12, -s * 24, 0, s * 26);
            // high cheekbones
            blk(m, STONE, s * 0.21, 0.6, 0.19, 0.15, 0.06, 0.08, -s * 20, 0, s * 8);
            // the brow, heavy and straight; the inner ends lift in sorrow
            blk(m, STONE, s * 0.14, 0.745, 0.22, 0.25, 0.06, 0.1, -s * 6, 0, s * (sorrow ? -14 : 4));
            // eyes: blank eyeballs deep in the sockets, or closed lids
            if (sleeping) blk(m, STONE, s * 0.135, 0.668, 0.185, 0.14, 0.032, 0.07, -s * 8, 8, s * 4);
            else blk(m, STONE, s * 0.135, 0.665, 0.16, 0.12, 0.07, 0.06, -s * 8, 0, 0);
            // the lower lids and the soft hollows under the eyes
            blk(m, STONE, s * 0.14, 0.625, 0.165, 0.13, 0.03, 0.07, -s * 10, 0, 0);
            // nostril wings
            blk(m, STONE, s * 0.052, 0.468, 0.255, 0.05, 0.05, 0.06, -s * 25, 0, 0);
            // lips: each half turns at the corner (down in sorrow)
            double corner = sorrow ? -11 : 3;
            blk(m, STONE, s * 0.055, 0.372, 0.235, 0.11, 0.04, 0.06, -s * 14, 0, s * corner);
            blk(m, STONE, s * 0.05, singing ? 0.29 : 0.322, 0.23, 0.1, 0.05, 0.065, -s * 14, 0, -s * corner * 0.6);
            // heavy locks of hair at the sides, and the curls along them
            blk(m, HAIR, s * 0.37, 0.72, 0.07, 0.11, 0.48, 0.14, -s * 15, 0, -s * 4);
            for (int k = 0; k < 3; k++) blk(m, HAIR, s * 0.4, 0.58 + k * 0.13, 0.13, 0.09, 0.09, 0.09, 45, 0, 45);
        }
        // the nose: a ridge in one line with the forehead, tilted out at the bottom, a ball at the tip
        blk(m, STONE, 0, 0.6, 0.235, 0.075, 0.26, 0.1, 0, -16, 0);
        blk(m, STONE, 0, 0.476, 0.29, 0.1, 0.07, 0.08, 0, 0, 0);
        blk(m, STONE, 0, 0.42, 0.24, 0.035, 0.05, 0.05, 0, 0, 0);               // philtrum
        if (singing) blk(m, Material.LIGHT_GRAY_CONCRETE, 0, 0.335, 0.2, 0.12, 0.05, 0.03, 0, 0, 0); // the open mouth, in shadow
        // chin
        blk(m, STONE, 0, 0.21, 0.21, 0.19, 0.12, 0.1, 0, 8, 0);
        // the cap of hair and a fringe of round curls over the forehead
        blk(m, HAIR, 0, 1.03, 0.08, 0.78, 0.18, 0.16, 0, 0, 0);
        blk(m, HAIR, 0, 1.11, 0.05, 0.6, 0.08, 0.12, 0, 0, 0);
        for (int k = -3; k <= 3; k++) {
            double x = k * 0.1;
            blk(m, HAIR, x, 0.975 - Math.abs(k) * 0.012, 0.18, 0.1, 0.1, 0.1, 45, 0, 45 + k * 7);
            if (k > -3 && k < 3) blk(m, HAIR, x + 0.05, 1.07, 0.155, 0.09, 0.09, 0.09, 45, 0, 45);
        }
        m.pop();
        m.viewRange = (float) Math.min(4, 1.2 + scale * 0.18);
    }
}
