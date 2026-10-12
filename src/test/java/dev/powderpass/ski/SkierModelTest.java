package dev.powderpass.ski;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkierModelTest {

    private static final float OFFSET = 0.6f;

    private static Map<RigPart, Matrix4f> pose(RigPose p) {
        Map<RigPart, Matrix4f> out = new EnumMap<>(RigPart.class);
        SkierModel.compute(p, OFFSET, true, out);
        return out;
    }

    private static RigPose standing(double yaw) {
        return new RigPose(yaw, 0, 0, 0, 1, 0, 0, 0.1, 0, false, false, -1, false);
    }

    /** World-space position (relative to the anchor) of a box's local point. */
    private static Vector3f at(Matrix4f m, float x, float y, float z) {
        return m.transformPosition(new Vector3f(x, y, z));
    }

    @Test
    void skisRestOnTheSnowAndHeadIsAtHeadHeight() {
        Map<RigPart, Matrix4f> m = pose(standing(0));
        Vector3f skiBottom = at(m.get(RigPart.SKI_L), 0.5f, 0f, 0.5f);
        assertEquals(-OFFSET, skiBottom.y, 1e-4, "ski underside sits on the snow");
        Vector3f headBase = at(m.get(RigPart.HEAD), 0f, -0.5f, 0f); // bottom of a skull item
        float headAboveSnow = headBase.y + OFFSET;
        assertTrue(headAboveSnow > 1.2f && headAboveSnow < 1.7f, "neck height " + headAboveSnow);
        for (Matrix4f matrix : m.values()) {
            assertTrue(matrix.isFinite());
        }
    }

    @Test
    void skisPointAlongTheHeading() {
        // Facing yaw 90 (towards -X): ski tips must be further along -X than ski tails.
        Map<RigPart, Matrix4f> m = pose(standing(90));
        Vector3f tail = at(m.get(RigPart.SKI_L), 0.5f, 0f, 0f);
        Vector3f tip = at(m.get(RigPart.SKI_L), 0.5f, 0f, 1f);
        assertTrue(tip.x < tail.x - 1.0f, "tip " + tip + " tail " + tail);
        assertEquals(tip.z, tail.z, 1e-3);
    }

    @Test
    void tuckLowersTheBody() {
        Map<RigPart, Matrix4f> tall = pose(standing(0));
        Map<RigPart, Matrix4f> tuck = pose(new RigPose(0, 0, 0, 0, 1, 0, 0, 1.0, 0, false, false, -1, false));
        float tallHead = at(tall.get(RigPart.HEAD), 0, 0, 0).y;
        float tuckHead = at(tuck.get(RigPart.HEAD), 0, 0, 0).y;
        assertTrue(tuckHead < tallHead - 0.3f, "tuck " + tuckHead + " tall " + tallHead);
    }

    @Test
    void halfFlipTurnsTheSkierUpsideDown() {
        Map<RigPart, Matrix4f> m = pose(new RigPose(0, 0, 0, 0, 1, 0, 0, 0.3, 180, true, false, -1, false));
        float head = at(m.get(RigPart.HEAD), 0, 0, 0).y;
        float ski = at(m.get(RigPart.SKI_L), 0.5f, 0, 0.5f).y;
        assertTrue(head < ski, "head " + head + " should be below skis " + ski);
    }

    @Test
    void slopeTiltsTheSkis() {
        double n = Math.sqrt(0.5);
        // Slope falling towards +Z: normal leans towards +Z.
        Map<RigPart, Matrix4f> m = pose(new RigPose(0, 0, 0, 0, n, n, 0, 0.1, 0, false, false, -1, false));
        Vector3f tail = at(m.get(RigPart.SKI_L), 0.5f, 0f, 0f);
        Vector3f tip = at(m.get(RigPart.SKI_L), 0.5f, 0f, 1f);
        assertTrue(tip.y < tail.y - 0.8f, "skis follow the 45 degree slope downhill");
    }

    @Test
    void snowboardStanceUsesABoard() {
        Map<RigPart, Matrix4f> m = pose(new RigPose(0, 0, 0, 0, 1, 0, 0, 0.1, 0, false, false, -1, true));
        assertTrue(m.containsKey(RigPart.BOARD));
        assertFalse(m.containsKey(RigPart.SKI_L));
        assertFalse(m.containsKey(RigPart.POLE_L));
        // Feet are spread along the board (Z), not across it.
        Vector3f left = at(m.get(RigPart.LEG_L), 0.5f, 0f, 0.5f);
        Vector3f right = at(m.get(RigPart.LEG_R), 0.5f, 0f, 0.5f);
        assertTrue(Math.abs(left.z - right.z) > 0.3f);
        assertTrue(Math.abs(left.x - right.x) < 0.05f);
    }
}
