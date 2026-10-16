package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.display.Model;
import com.powderpass.backrooms.display.Part;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The creature skeletons: every posed bone exists, poses compose parent-first, and a rest pose changes nothing. */
class PosesTest {
    @BeforeAll
    static void mock() {
        MockBukkit.mock();
    }

    @AfterAll
    static void unmock() {
        MockBukkit.unmock();
    }

    private static void allBonesExist(String what, Model m, Map<String, Matrix4f> pose) {
        for (String bone : pose.keySet()) {
            boolean found = m.parts.stream().anyMatch(p -> p.name != null && (p.name.equals(bone) || p.name.startsWith(bone + ".")));
            assertTrue(found, what + ": pose names bone '" + bone + "' that no part uses");
        }
    }

    @Test
    void everyPosedBoneExists() {
        allBonesExist("smiler", Bodies.smiler(), Poses.smiler(1, 1.2, 1, 1, 10, true, 1));
        allBonesExist("faceling", Bodies.faceling(), Poses.faceling(40, 1, 1, 1));
        allBonesExist("hound", Bodies.hound(), Poses.hound(1, 1, 1, 1, 0.3, 0.2));
        allBonesExist("drifter", Bodies.drifter(), Poses.drifter(1, 0.4, 1, 1));
        allBonesExist("watcher", Bodies.watcher(), Poses.watcherHaze(3, false));
        for (String b : List.of("leg_l", "leg_r", "arm_l", "arm_r")) {
            assertTrue(Bodies.faceling().parts.stream().anyMatch(p -> b.equals(p.name)), "faceling walk bone " + b);
            assertTrue(Bodies.watcher().parts.stream().anyMatch(p -> b.equals(p.name)), "watcher walk bone " + b);
        }
    }

    @Test
    void childFollowsParentThenItsOwnPose() {
        Matrix4f local = new Matrix4f().translate(0, 1, 1);
        Matrix4f head = new Matrix4f().translate(0, 2, 0), jaw = new Matrix4f().rotateY((float) Math.PI);
        Matrix4f m = Body.compose("head.jaw", Map.of("head", head, "head.jaw", jaw), local);
        Vector3f o = m.transformPosition(new Vector3f());
        assertEquals(0, o.x, 1e-5);
        assertEquals(3, o.y, 1e-5);
        assertEquals(-1, o.z, 1e-5, "the jaw's own pose applies before (inside) the head's");
        // a part that is not under the posed bone is untouched
        assertEquals(local, Body.compose("headless", Map.of("head", head), local));
    }

    @Test
    void neutralPosesKeepTheRestShape() {
        Map<String, Matrix4f> shut = Poses.faceling(0, 0, 0, 0);
        assertTrue(shut.get("head.mouth").equals(new Matrix4f(), 1e-5f), "a shut mouth is the rest seam");
    }
}
