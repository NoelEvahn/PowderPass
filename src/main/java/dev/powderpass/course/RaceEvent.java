package dev.powderpass.course;

/**
 * Something that happened during a timed run.
 *
 * @param type         what happened
 * @param gate         index of the gate involved (finish = last index)
 * @param elapsedTicks precise run time at the event, excluding penalties
 * @param totalTicks   run time including penalties
 */
public record RaceEvent(Type type, int gate, double elapsedTicks, double totalTicks) {

    public enum Type {
        GATE_PASSED,
        GATE_MISSED,
        FINISHED,
        DISQUALIFIED
    }
}
