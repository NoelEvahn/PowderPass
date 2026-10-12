package dev.powderpass.physics;

/** Things that happened during a single physics step. Reused between ticks to avoid allocation. */
public final class StepResult {

    public boolean launched;
    public boolean jumped;
    public boolean landed;
    public boolean wallBump;
    public CrashReason crash;

    // landing details
    public double landingImpact;
    public double landingSpin;
    public double landingFlip;
    public int landingGrabTicks;
    public int landingAirTicks;
    public boolean landingSwitch;
    public double landingDrop;

    // telemetry
    public double horizontalDistance;
    public double verticalDrop;

    public void reset() {
        launched = false;
        jumped = false;
        landed = false;
        wallBump = false;
        crash = null;
        landingImpact = 0;
        landingSpin = 0;
        landingFlip = 0;
        landingGrabTicks = 0;
        landingAirTicks = 0;
        landingSwitch = false;
        landingDrop = 0;
        horizontalDistance = 0;
        verticalDrop = 0;
    }

    public boolean crashed() {
        return crash != null;
    }
}
