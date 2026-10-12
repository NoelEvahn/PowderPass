package dev.powderpass.lift.geom;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out a chairlift: places towers along the line, sets rope heights so every seat clears the terrain,
 * sags the rope between supports and stitches the up line, top bullwheel, down line and bottom bullwheel
 * into one closed loop.
 */
public final class LiftGeometry {

    private static final double STEP = 0.5;

    private LiftGeometry() {
    }

    public static LiftLayout build(LiftSpec spec, GroundFunction ground) {
        double dxTotal = spec.tx() - spec.bx();
        double dzTotal = spec.tz() - spec.bz();
        double D = Math.hypot(dxTotal, dzTotal);
        if (D < spec.minimumLength()) {
            throw new IllegalArgumentException("Stations are too close together (" + Math.round(D)
                    + " blocks, need at least " + Math.round(spec.minimumLength()) + ")");
        }
        double ux = dxTotal / D;
        double uz = dzTotal / D;
        // right-hand side when facing uphill
        double rx = -uz;
        double rz = ux;
        double r = spec.lineHalfSpacing();
        double hanger = spec.hangerLength();
        double hB = spec.by() + spec.seatHeight() + hanger;
        double hT = spec.ty() + spec.seatHeight() + hanger;
        double d0 = spec.platformLength();
        double dN = D - spec.platformLength();
        List<String> warnings = new ArrayList<>();

        // ---------------------------------------------------------------- supports
        int towerCount = Math.max(0, (int) Math.ceil((dN - d0) / spec.maxTowerSpacing()) - 1);
        int supports = towerCount + 2;
        double[] sd = new double[supports];
        double[] sh = new double[supports];
        sd[0] = d0;
        sh[0] = hB;
        sd[supports - 1] = dN;
        sh[supports - 1] = hT;
        for (int i = 1; i <= towerCount; i++) {
            double d = d0 + (dN - d0) * i / (towerCount + 1.0);
            sd[i] = d;
            double straight = hB + (hT - hB) * (d - d0) / (dN - d0);
            double groundMax = groundAcross(spec, ground, ux, uz, rx, rz, r, d);
            sh[i] = Math.max(straight, groundMax + spec.minSeatClearance() + hanger);
        }

        // Raise towers until every seat along every span clears the terrain.
        for (int iteration = 0; iteration < 24; iteration++) {
            boolean changed = false;
            for (int k = 0; k < supports - 1; k++) {
                double deficit = 0.0;
                double worstT = 0.5;
                for (double d = sd[k]; d <= sd[k + 1]; d += 1.0) {
                    double seat = cableY(sd, sh, spec.sagRatio(), d) - hanger;
                    double need = groundAcross(spec, ground, ux, uz, rx, rz, r, d) + requiredClearance(spec, d, d0, dN);
                    if (need - seat > deficit) {
                        deficit = need - seat;
                        worstT = (d - sd[k]) / (sd[k + 1] - sd[k]);
                    }
                }
                if (deficit > 0.05) {
                    boolean canRaiseStart = k > 0;
                    boolean canRaiseEnd = k + 1 < supports - 1;
                    if (canRaiseStart && canRaiseEnd) {
                        sh[k] += deficit;
                        sh[k + 1] += deficit;
                        changed = true;
                    } else if (canRaiseStart) {
                        sh[k] += Math.min(deficit * 4.0, deficit / Math.max(0.05, 1.0 - worstT));
                        changed = true;
                    } else if (canRaiseEnd) {
                        sh[k + 1] += Math.min(deficit * 4.0, deficit / Math.max(0.05, worstT));
                        changed = true;
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
        for (int k = 0; k < supports - 1; k++) {
            for (double d = sd[k]; d <= sd[k + 1]; d += 2.0) {
                double seat = cableY(sd, sh, spec.sagRatio(), d) - hanger;
                double need = groundAcross(spec, ground, ux, uz, rx, rz, r, d) + requiredClearance(spec, d, d0, dN) * 0.5;
                if (seat < need) {
                    warnings.add("Seats pass close to the ground about " + Math.round(d) + " blocks from the base station");
                    break;
                }
            }
        }

        for (int k = 0; k < supports - 1; k++) {
            double grade = Math.abs(sh[k + 1] - sh[k]) / Math.max(1.0, sd[k + 1] - sd[k]);
            if (grade > 1.2) {
                warnings.add("The rope is very steep about " + Math.round(sd[k]) + " blocks from the base station"
                        + " - consider a flatter station site");
            }
        }

        // ---------------------------------------------------------------- loop points
        List<Vector3d> points = new ArrayList<>();
        List<double[]> upMarks = new ArrayList<>();   // {d, pointIndex}
        List<double[]> downMarks = new ArrayList<>();

        // up line, d: 0 -> D, on the right-hand side
        for (double d = 0.0; d < D; d += STEP) {
            upMarks.add(new double[]{d, points.size()});
            points.add(linePoint(spec, ux, uz, rx, rz, r, d, profile(sd, sh, spec.sagRatio(), d, d0, dN, hB, hT)));
        }
        upMarks.add(new double[]{D, points.size()});
        points.add(linePoint(spec, ux, uz, rx, rz, r, D, hT));
        // top bullwheel: from +right around the far side (+axis) to -right
        Vector3d topWheel = new Vector3d(spec.bx() + ux * D, hT, spec.bz() + uz * D);
        int arcSteps = Math.max(6, (int) Math.ceil(Math.PI * r / STEP));
        for (int i = 1; i < arcSteps; i++) {
            double phi = Math.PI * i / arcSteps;
            double ox = rx * r * Math.cos(phi) + ux * r * Math.sin(phi);
            double oz = rz * r * Math.cos(phi) + uz * r * Math.sin(phi);
            points.add(new Vector3d(topWheel.x + ox, hT, topWheel.z + oz));
        }
        // down line, d: D -> 0, on the left-hand side
        for (double d = D; d > 0.0; d -= STEP) {
            downMarks.add(new double[]{d, points.size()});
            points.add(linePoint(spec, ux, uz, rx, rz, -r, d, profile(sd, sh, spec.sagRatio(), d, d0, dN, hB, hT)));
        }
        downMarks.add(new double[]{0.0, points.size()});
        points.add(linePoint(spec, ux, uz, rx, rz, -r, 0.0, hB));
        // bottom bullwheel: from -right around the back (-axis) to +right
        Vector3d bottomWheel = new Vector3d(spec.bx(), hB, spec.bz());
        for (int i = 1; i < arcSteps; i++) {
            double phi = Math.PI * i / arcSteps;
            double ox = -rx * r * Math.cos(phi) - ux * r * Math.sin(phi);
            double oz = -rz * r * Math.cos(phi) - uz * r * Math.sin(phi);
            points.add(new Vector3d(bottomWheel.x + ox, hB, bottomWheel.z + oz));
        }

        CablePath path = new CablePath(points);
        double[] cumAt = new double[points.size() + 1];
        Vector3d a = new Vector3d();
        Vector3d b = new Vector3d();
        for (int i = 1; i <= points.size(); i++) {
            path.point(i - 1, a);
            path.point(i, b);
            cumAt[i] = cumAt[i - 1] + a.distance(b);
        }

        // ---------------------------------------------------------------- zones and towers
        LiftLayout.Zone bottomLoad = new LiftLayout.Zone(sAt(upMarks, cumAt, 0.6), sAt(upMarks, cumAt, d0 - 0.8));
        LiftLayout.Zone topUnload = new LiftLayout.Zone(sAt(upMarks, cumAt, dN + 0.8), sAt(upMarks, cumAt, D - 0.4));
        LiftLayout.Zone topLoad = new LiftLayout.Zone(sAt(downMarks, cumAt, D - 0.6), sAt(downMarks, cumAt, dN + 0.8));
        LiftLayout.Zone bottomUnload = new LiftLayout.Zone(sAt(downMarks, cumAt, d0 - 0.8), sAt(downMarks, cumAt, 0.4));

        List<LiftLayout.Tower> towers = new ArrayList<>();
        double axisYaw = Math.toDegrees(Math.atan2(-ux, uz));
        for (int i = 1; i <= towerCount; i++) {
            double d = sd[i];
            double x = spec.bx() + ux * d;
            double z = spec.bz() + uz * d;
            double base = Math.min(ground.groundAt(x + rx * r, z + rz * r),
                    Math.min(ground.groundAt(x, z), ground.groundAt(x - rx * r, z - rz * r)));
            towers.add(new LiftLayout.Tower(x, base, z, sh[i], axisYaw, sAt(upMarks, cumAt, d), sAt(downMarks, cumAt, d)));
        }

        // ---------------------------------------------------------------- speed profile
        double length = path.length();
        int samples = Math.max(8, (int) Math.ceil(length / STEP));
        double sampleStep = length / samples;
        double[] raw = new double[samples];
        double slow = Math.max(0.1, Math.min(1.0, spec.stationSlow()));
        double sUpExit = sAt(upMarks, cumAt, d0 + 1.5);
        double sUpEnter = sAt(upMarks, cumAt, dN - 1.5);
        double sDownExit = sAt(downMarks, cumAt, dN - 1.5);
        double sDownEnter = sAt(downMarks, cumAt, d0 + 1.5);
        for (int i = 0; i < samples; i++) {
            double s = i * sampleStep;
            boolean onLine = (s > sUpExit && s < sUpEnter) || (s > sDownExit && s < sDownEnter);
            raw[i] = onLine ? 1.0 : slow;
        }
        double[] smooth = new double[samples];
        int window = Math.max(1, (int) Math.round(3.0 / sampleStep));
        for (int i = 0; i < samples; i++) {
            double sum = 0.0;
            for (int k = -window; k <= window; k++) {
                sum += raw[Math.floorMod(i + k, samples)];
            }
            smooth[i] = sum / (2 * window + 1);
        }

        double rideUp = topUnload.start() - bottomLoad.end();
        return new LiftLayout(path, towers, bottomWheel, topWheel, ux, uz, rx, rz, hanger, r,
                bottomLoad, topUnload, topLoad, bottomUnload, smooth, sampleStep, rideUp, warnings);
    }

    /** Rope height at distance {@code d} along the axis, including station platforms. */
    private static double profile(double[] sd, double[] sh, double sag, double d, double d0, double dN,
                                  double hB, double hT) {
        if (d <= d0) {
            return hB;
        }
        if (d >= dN) {
            return hT;
        }
        return cableY(sd, sh, sag, d);
    }

    /** Seats may run low just outside the stations, then must reach full clearance within a few blocks. */
    private static double requiredClearance(LiftSpec spec, double d, double d0, double dN) {
        double fromStation = Math.min(d - d0, dN - d);
        return Math.min(spec.minSeatClearance(), spec.seatHeight() + Math.max(0.0, fromStation) * 0.4);
    }

    /** Parabolic sag between consecutive supports. */
    static double cableY(double[] sd, double[] sh, double sag, double d) {
        int n = sd.length;
        if (d <= sd[0]) {
            return sh[0];
        }
        if (d >= sd[n - 1]) {
            return sh[n - 1];
        }
        int k = 0;
        while (k < n - 2 && d > sd[k + 1]) {
            k++;
        }
        double span = sd[k + 1] - sd[k];
        double t = (d - sd[k]) / span;
        double straight = sh[k] + (sh[k + 1] - sh[k]) * t;
        return straight - sag * span * 4.0 * t * (1.0 - t);
    }

    private static double groundAcross(LiftSpec spec, GroundFunction ground, double ux, double uz,
                                       double rx, double rz, double r, double d) {
        double x = spec.bx() + ux * d;
        double z = spec.bz() + uz * d;
        double best = ground.groundAt(x, z);
        best = Math.max(best, ground.groundAt(x + rx * r, z + rz * r));
        best = Math.max(best, ground.groundAt(x - rx * r, z - rz * r));
        return best;
    }

    private static Vector3d linePoint(LiftSpec spec, double ux, double uz, double rx, double rz, double side,
                                  double d, double y) {
        return new Vector3d(spec.bx() + ux * d + rx * side, y, spec.bz() + uz * d + rz * side);
    }

    /** Arc length of the point at axis distance {@code d} on a line, interpolating between marks. */
    private static double sAt(List<double[]> marks, double[] cumAt, double d) {
        double[] first = marks.get(0);
        boolean ascending = marks.get(marks.size() - 1)[0] > first[0];
        for (int i = 0; i < marks.size() - 1; i++) {
            double[] m0 = marks.get(i);
            double[] m1 = marks.get(i + 1);
            boolean within = ascending ? (d >= m0[0] && d <= m1[0]) : (d <= m0[0] && d >= m1[0]);
            if (within) {
                double span = m1[0] - m0[0];
                double t = Math.abs(span) < 1.0e-9 ? 0.0 : (d - m0[0]) / span;
                double s0 = cumAt[(int) m0[1]];
                double s1 = cumAt[(int) m1[1]];
                return s0 + (s1 - s0) * t;
            }
        }
        double[] last = marks.get(marks.size() - 1);
        boolean beforeFirst = ascending ? d < first[0] : d > first[0];
        return cumAt[(int) (beforeFirst ? first[1] : last[1])];
    }
}
