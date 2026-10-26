package dev.powderpass.volleyball.ball;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BallModelTest {

    @Test
    void sphereIsCompactAndBounded() {
        for (int n = 6; n <= 14; n += 2) {
          for (BallModel.Style style : BallModel.Style.values()) {
            BallModel m = BallModel.build(n, style);
            assertTrue(m.parts().size() <= 50, n + " voxels used " + m.parts().size() + " parts");
            for (BallModel.Part p : m.parts()) {
                assertTrue(p.minX() >= -1 && p.maxX() <= 1 && p.minY() >= -1 && p.maxY() <= 1 && p.minZ() >= -1 && p.maxZ() <= 1);
                assertTrue(p.color() >= 0 && p.color() < 3);
            }
            System.out.println(style + " " + n + " voxels -> " + m.parts().size() + " block displays");
            StringBuilder sb = new StringBuilder("PARTS" + n + " ");
            for (BallModel.Part p : m.parts()) {
                sb.append(String.format(java.util.Locale.ROOT, "%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%d;", p.minX(), p.minY(), p.minZ(), p.maxX(), p.maxY(), p.maxZ(), p.color()));
            }
            System.out.println(sb);
          }
        }
    }

    @Test
    void coversEveryDirection() {
        BallModel m = BallModel.build(12, BallModel.Style.CLASSIC);
        // a ray from the centre in any direction must leave through some part at radius > 0.75
        java.util.Random r = new java.util.Random(1);
        for (int i = 0; i < 2000; i++) {
            double x = r.nextGaussian();
            double y = r.nextGaussian();
            double z = r.nextGaussian();
            double l = Math.sqrt(x * x + y * y + z * z);
            double px = x / l * 0.78;
            double py = y / l * 0.78;
            double pz = z / l * 0.78;
            boolean covered = false;
            for (BallModel.Part p : m.parts()) {
                if (px >= p.minX() && px <= p.maxX() && py >= p.minY() && py <= p.maxY() && pz >= p.minZ() && pz <= p.maxZ()) {
                    covered = true;
                    break;
                }
            }
            assertTrue(covered, "hole in the ball surface");
        }
    }
}
