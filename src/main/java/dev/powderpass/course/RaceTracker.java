package dev.powderpass.course;

import java.util.ArrayList;
import java.util.List;

/**
 * Times one run through a course. Gate 0 is the start, the last gate the finish; everything in between must
 * be passed in order. Times have sub-tick precision: the exact crossing point within a tick's movement is
 * interpolated.
 */
public final class RaceTracker {

    public enum MissPolicy {
        PENALTY,
        DISQUALIFY
    }

    private final List<GateLine> gates;
    private final double startTick;
    private final MissPolicy policy;
    private final double penaltyTicks;
    private final double maxOutside;
    private final double maxDy;
    private final double[] splits;

    private int next = 1;
    private int missed;
    private boolean done;

    public RaceTracker(List<GateLine> gates, double startTick, MissPolicy policy, double penaltyTicks,
                       double maxOutside, double maxDy) {
        if (gates.size() < 2) {
            throw new IllegalArgumentException("A course needs at least a start and a finish");
        }
        this.gates = List.copyOf(gates);
        this.startTick = startTick;
        this.policy = policy;
        this.penaltyTicks = penaltyTicks;
        this.maxOutside = maxOutside;
        this.maxDy = maxDy;
        this.splits = new double[gates.size()];
    }

    /**
     * Checks whether a movement crossed the start gate.
     *
     * @return the precise tick of the crossing, or NaN
     */
    public static double startCrossing(GateLine start, double p0x, double p0z, double p1x, double p1z, double y,
                                       long tick, double maxDy) {
        GateLine.Crossing c = start.cross(p0x, p0z, p1x, p1z, y, 0.0, maxDy);
        if (c == null || !c.inside()) {
            return Double.NaN;
        }
        return tick - 1 + c.t();
    }

    /** Processes the rider's movement during {@code tick} (from p0 to p1). */
    public List<RaceEvent> update(double p0x, double p0y, double p0z, double p1x, double p1y, double p1z, long tick) {
        if (done) {
            return List.of();
        }
        List<RaceEvent> events = new ArrayList<>(1);
        double y = (p0y + p1y) / 2.0;
        int last = gates.size() - 1;
        while (next <= last && !done) {
            GateLine gate = gates.get(next);
            GateLine.Crossing c = gate.cross(p0x, p0z, p1x, p1z, y, next == last ? 0.0 : maxOutside, maxDy);
            if (c != null) {
                double elapsed = tick - 1 + c.t() - startTick;
                if (c.inside()) {
                    splits[next] = elapsed;
                    if (next == last) {
                        done = true;
                        events.add(new RaceEvent(RaceEvent.Type.FINISHED, next, elapsed, elapsed + missed * penaltyTicks));
                    } else {
                        events.add(new RaceEvent(RaceEvent.Type.GATE_PASSED, next, elapsed, elapsed + missed * penaltyTicks));
                        next++;
                    }
                } else if (!miss(events, elapsed)) {
                    break;
                }
                continue;
            }
            // Skipped straight past this gate into the next one?
            if (next + 1 <= last) {
                GateLine after = gates.get(next + 1);
                GateLine.Crossing c2 = after.cross(p0x, p0z, p1x, p1z, y, 0.0, maxDy);
                if (c2 != null && c2.inside()) {
                    double elapsed = tick - 1 + c2.t() - startTick;
                    if (!miss(events, elapsed)) {
                        break;
                    }
                    continue;
                }
            }
            break;
        }
        return events;
    }

    /** Records a missed gate. Returns false if the run is now over. */
    private boolean miss(List<RaceEvent> events, double elapsed) {
        missed++;
        if (policy == MissPolicy.DISQUALIFY) {
            done = true;
            events.add(new RaceEvent(RaceEvent.Type.DISQUALIFIED, next, elapsed, elapsed));
            return false;
        }
        events.add(new RaceEvent(RaceEvent.Type.GATE_MISSED, next, elapsed, elapsed + missed * penaltyTicks));
        next++;
        return true;
    }

    public double startTick() {
        return startTick;
    }

    public int nextGate() {
        return next;
    }

    public int gateCount() {
        return gates.size();
    }

    public int missed() {
        return missed;
    }

    public boolean done() {
        return done;
    }

    public double penaltyTicks() {
        return missed * penaltyTicks;
    }

    public double split(int gate) {
        return splits[gate];
    }

    public double elapsed(long tick) {
        return tick - startTick;
    }
}
