package dev.powderpass.volleyball.game;

public enum TeamId {
    A, B;

    public TeamId other() {
        return this == A ? B : A;
    }

    public int index() {
        return ordinal();
    }
}
