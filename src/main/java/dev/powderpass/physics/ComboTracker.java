package dev.powderpass.physics;

/**
 * Chains tricks landed in quick succession into a combo with a growing multiplier. A combo is banked when
 * no new trick lands within the combo window or when the rider crashes (in which case it is lost).
 */
public final class ComboTracker {

    private final int windowTicks;
    private final double stepMultiplier;
    private final double maxMultiplier;

    private int count;
    private long total;
    private long lastTrickTick = Long.MIN_VALUE;

    public ComboTracker(int windowTicks, double stepMultiplier, double maxMultiplier) {
        this.windowTicks = windowTicks;
        this.stepMultiplier = stepMultiplier;
        this.maxMultiplier = maxMultiplier;
    }

    /** Adds a trick and returns the points it earned after the multiplier. */
    public int addTrick(int baseScore, long tick) {
        if (count > 0 && tick - lastTrickTick > windowTicks) {
            reset();
        }
        count++;
        int earned = (int) Math.round(baseScore * multiplier());
        total += earned;
        lastTrickTick = tick;
        return earned;
    }

    /**
     * Checks whether the current combo has expired.
     *
     * @return the banked combo total if it just ended, otherwise -1
     */
    public long tick(long tick) {
        if (count > 0 && tick - lastTrickTick > windowTicks) {
            long banked = total;
            reset();
            return banked;
        }
        return -1;
    }

    public void lose() {
        reset();
    }

    public double multiplier() {
        return Math.min(maxMultiplier, 1.0 + stepMultiplier * Math.max(0, count - 1));
    }

    public int count() {
        return count;
    }

    public long total() {
        return total;
    }

    /** Fraction of the combo window remaining, for HUD bars. */
    public double remaining(long tick) {
        if (count == 0) {
            return 0.0;
        }
        return Math.max(0.0, 1.0 - (tick - lastTrickTick) / (double) windowTicks);
    }

    private void reset() {
        count = 0;
        total = 0;
        lastTrickTick = Long.MIN_VALUE;
    }
}
