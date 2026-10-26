package dev.powderpass.volleyball.game;

public enum MatchState {
    COUNTDOWN("Starting"),
    PRE_SERVE("Get ready"),
    SERVE("Serve"),
    RALLY("Rally"),
    POINT_END("Point"),
    TIMEOUT("Timeout"),
    PAUSED("Paused"),
    FINISHED("Finished");

    private final String label;

    MatchState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
