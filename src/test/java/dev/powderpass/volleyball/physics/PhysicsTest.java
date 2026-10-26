package dev.powderpass.volleyball.physics;

import dev.powderpass.volleyball.math.CourtFrame;
import dev.powderpass.volleyball.math.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PhysicsTest {

    private static final PhysicsParams P = PhysicsParams.realistic();
    /** Flat floor whose top surface is y = 0. */
    private static final CollisionWorld FLOOR = (minX, minY, minZ, maxX, maxY, maxZ, out) -> {
        if (minY <= 0) {
            out.add(new Aabb(Math.floor(minX) - 1, -1, Math.floor(minZ) - 1, Math.ceil(maxX) + 1, 0, Math.ceil(maxZ) + 1));
        }
    };

    private static NetGeometry net() {
        return new NetGeometry(2.43, 1.0, 5.0, 4.5, 0.8, 0.02, 0.035, 5.5, 0.06, 0.01);
    }

    @Test
    void dropBouncesWithRestitution() {
        // isolate restitution from air drag
        PhysicsParams vac = new PhysicsParams(9.81, 0, 0.105, 0.27, 0.47, 0.22, 17, 1.6,
                1, 0.35, 4, 0, 2.5, 0.7, 0.45, 0.55, 0.35, 0.12, 0.55, 0.35, 0.45, 0.6, 0.08, 32);
        BallSimulator sim = new BallSimulator();
        BallBody b = new BallBody(1);
        b.pos.set(0, 2.0, 0);
        double peakAfter = 0;
        boolean bounced = false;
        for (int i = 0; i < 80; i++) {
            sim.tick(b, vac, FLOOR, null, SimulationListener.NONE, BallSimulator.TICK);
            if (b.vel.y > 0) {
                bounced = true;
            }
            if (bounced) {
                peakAfter = Math.max(peakAfter, b.pos.y - P.radius());
            }
            if (bounced && b.vel.y < 0 && b.pos.y < 0.5) {
                break;
            }
        }
        assertTrue(bounced, "ball should bounce");
        double expected = (2.0 - P.radius()) * vac.floorRestitution() * vac.floorRestitution();
        assertEquals(expected, peakAfter, 0.05, "rebound height ~ e^2 h");
    }

    @Test
    void ballComesToRest() {
        BallSimulator sim = new BallSimulator();
        BallBody b = new BallBody(1);
        b.pos.set(0, 1.0, 0);
        b.vel.set(3, 0, 0);
        for (int i = 0; i < 20 * 30 && !b.resting; i++) {
            sim.tick(b, P, FLOOR, null, SimulationListener.NONE, BallSimulator.TICK);
        }
        assertTrue(b.resting, "ball should eventually rest");
        assertEquals(P.radius(), b.pos.y, 0.02);
    }

    @Test
    void dragShortensRangeAndTopspinDips() {
        TrajectorySolver solver = new TrajectorySolver(P);
        Vec3 start = new Vec3(0, 3, 0);
        Vec3 vel = new Vec3(20, 2, 0);
        Vec3 dir = new Vec3(1, 0, 0);
        double withDrag = solver.simulate(start, vel, new Vec3(), 0, dir).landingDistance();
        TrajectorySolver vacuum = new TrajectorySolver(new PhysicsParams(9.81, 0, 0.105, 0.27, 0.47, 0.22, 17, 1.6,
                1, 0.35, 4, 0, 2.5, 0.7, 0.45, 0.55, 0.35, 0.12, 0.55, 0.35, 0.45, 0.6, 0.08, 32));
        double noDrag = vacuum.simulate(start, vel, new Vec3(), 0, dir).landingDistance();
        assertTrue(withDrag < noDrag - 1.0, "drag must shorten the flight: " + withDrag + " vs " + noDrag);

        Vec3 topspin = TrajectorySolver.SpinFactory.topspin(40).spinFor(vel);
        double spun = solver.simulate(start, vel, topspin, 0, dir).landingDistance();
        assertTrue(spun < withDrag - 0.5, "topspin must make the ball dip: " + spun + " vs " + withDrag);

        Vec3 back = TrajectorySolver.SpinFactory.topspin(-40).spinFor(vel);
        double floated = solver.simulate(start, vel, back, 0, dir).landingDistance();
        assertTrue(floated > withDrag, "backspin must carry further");
    }

    @Test
    void arcSolverHitsTarget() {
        TrajectorySolver solver = new TrajectorySolver(P);
        Vec3 start = new Vec3(-6, 0.9, 1);
        Vec3 target = new Vec3(-1, 0, -2);
        Vec3 v = solver.solveArc(start, target, 0, 2.5, new Vec3());
        Vec3 landing = solver.predictLanding(start, v, new Vec3(), 0);
        assertEquals(target.x, landing.x, 0.15);
        assertEquals(target.z, landing.z, 0.15);
        TrajectorySolver.Flight f = solver.simulate(start, v, new Vec3(), 0, TrajectorySolver.horizontalDir(start, target));
        assertEquals(start.y + 2.5, f.apexY(), 0.2);
    }

    @Test
    void drivenSolverHitsTarget() {
        TrajectorySolver solver = new TrajectorySolver(P);
        Vec3 start = new Vec3(-10, 3.0, 0);
        Vec3 target = new Vec3(6, 0, 2);
        TrajectorySolver.SpinFactory spin = TrajectorySolver.SpinFactory.topspin(30);
        Vec3 v = solver.solveDriven(start, target, 0, 22, spin);
        assertEquals(22, v.length(), 1e-6);
        Vec3 landing = solver.predictLanding(start, v, spin.spinFor(v), 0);
        assertEquals(target.x, landing.x, 0.25);
        assertEquals(target.z, landing.z, 0.25);
    }

    @Test
    void spikeDownwardSolution() {
        TrajectorySolver solver = new TrajectorySolver(P);
        Vec3 start = new Vec3(-0.6, 3.2, 0);
        Vec3 target = new Vec3(3, 0, 0);
        Vec3 v = solver.solveDriven(start, target, 0, 25, TrajectorySolver.SpinFactory.topspin(40));
        assertTrue(v.y < 0, "a spike close to the net must go downward");
    }

    @Test
    void netStopsBallBelowTape() {
        CourtFrame frame = new CourtFrame(0, 0, 0, 1, 0);
        CourtNet courtNet = new CourtNet(frame, net());
        BallSimulator sim = new BallSimulator();
        BallBody b = new BallBody(1);
        b.pos.set(-2, 1.9, 0);
        b.vel.set(15, 0, 0);
        List<NetContact> contacts = new ArrayList<>();
        int[] crossings = {0};
        SimulationListener l = new SimulationListener() {
            @Override
            public void onNetContact(NetContact contact, Vec3 localPoint, double impactSpeed) {
                contacts.add(contact);
            }

            @Override
            public void onNetPlaneCrossed(int fromSide, double localW, double localY) {
                crossings[0]++;
            }
        };
        for (int i = 0; i < 40; i++) {
            sim.tick(b, P, FLOOR, courtNet, l, BallSimulator.TICK);
        }
        assertTrue(contacts.contains(NetContact.MESH));
        assertEquals(0, crossings[0], "ball must not pass through the mesh");
        assertTrue(b.pos.x < 0, "ball stays on its own side");
    }

    @Test
    void ballOverNetCrosses() {
        CourtFrame frame = new CourtFrame(10, 64, 10, 0, 1);
        CourtNet courtNet = new CourtNet(frame, net());
        BallSimulator sim = new BallSimulator();
        BallBody b = new BallBody(1);
        Vec3 start = frame.toWorld(-3, 2.5, 0.5, new Vec3());
        b.pos.set(start);
        frame.dirToWorld(8, 4, 0, b.vel);
        double[] cross = {Double.NaN, Double.NaN};
        SimulationListener l = new SimulationListener() {
            @Override
            public void onNetPlaneCrossed(int fromSide, double localW, double localY) {
                cross[0] = localW;
                cross[1] = localY;
                assertEquals(-1, fromSide);
            }
        };
        CollisionWorld floor = (minX, minY, minZ, maxX, maxY, maxZ, out) -> out.add(new Aabb(-50, 63, -50, 50, 64, 50));
        for (int i = 0; i < 30; i++) {
            sim.tick(b, P, floor, courtNet, l, BallSimulator.TICK);
        }
        assertEquals(0.5, cross[0], 0.05);
        assertTrue(cross[1] > 2.43);
    }

    @Test
    void antennaIsDetected() {
        CourtFrame frame = new CourtFrame(0, 0, 0, 1, 0);
        CourtNet courtNet = new CourtNet(frame, net());
        BallSimulator sim = new BallSimulator();
        BallBody b = new BallBody(1);
        b.pos.set(-1, 2.8, 4.5);
        b.vel.set(10, 0, 0);
        List<NetContact> contacts = new ArrayList<>();
        SimulationListener l = new SimulationListener() {
            @Override
            public void onNetContact(NetContact contact, Vec3 localPoint, double impactSpeed) {
                contacts.add(contact);
            }
        };
        for (int i = 0; i < 10; i++) {
            sim.tick(b, P, CollisionWorld.EMPTY, courtNet, l, BallSimulator.TICK);
        }
        assertTrue(contacts.contains(NetContact.ANTENNA));
    }

    @Test
    void tapeCanRollOver() {
        // a slow ball clipping the tape should be deflected (net cord) rather than pass untouched
        CourtFrame frame = new CourtFrame(0, 0, 0, 1, 0);
        CourtNet courtNet = new CourtNet(frame, net());
        BallSimulator sim = new BallSimulator();
        BallBody b = new BallBody(1);
        b.pos.set(-1, 2.5, 0);
        b.vel.set(6, 0.5, 0);
        List<NetContact> contacts = new ArrayList<>();
        SimulationListener l = new SimulationListener() {
            @Override
            public void onNetContact(NetContact contact, Vec3 localPoint, double impactSpeed) {
                contacts.add(contact);
            }
        };
        for (int i = 0; i < 10; i++) {
            sim.tick(b, P, CollisionWorld.EMPTY, courtNet, l, BallSimulator.TICK);
        }
        assertTrue(contacts.contains(NetContact.TAPE));
    }

    @Test
    void knuckleMovesFloatServe() {
        BallSimulator sim = new BallSimulator();
        BallBody a = new BallBody(7);
        a.pos.set(0, 2.5, 0);
        a.launch(new Vec3(17, 3, 0), new Vec3(), true);
        BallBody b = new BallBody(7);
        b.pos.set(0, 2.5, 0);
        b.launch(new Vec3(17, 3, 0), new Vec3(), false);
        for (int i = 0; i < 16; i++) {
            sim.tick(a, P, CollisionWorld.EMPTY, null, SimulationListener.NONE, BallSimulator.TICK);
            sim.tick(b, P, CollisionWorld.EMPTY, null, SimulationListener.NONE, BallSimulator.TICK);
        }
        double drift = a.pos.distance(b.pos);
        assertTrue(drift > 0.05 && drift < 2.5, "float serve should wobble a little: " + drift);
    }

    @Test
    void courtFrameRoundTrip() {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            CourtFrame f = new CourtFrame(100.5, 64, -20.5, d[0], d[1]);
            Vec3 w = f.toWorld(3.2, 1.1, -2.7, new Vec3());
            Vec3 l = f.toLocal(w, new Vec3());
            assertEquals(3.2, l.x, 1e-9);
            assertEquals(1.1, l.y, 1e-9);
            assertEquals(-2.7, l.z, 1e-9);
        }
    }
}
