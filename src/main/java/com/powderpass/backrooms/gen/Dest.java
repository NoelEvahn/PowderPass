package com.powderpass.backrooms.gen;

import com.powderpass.backrooms.Level;

/** Destination of a transition: another level or the way out ("exit"). */
public record Dest(Level level) {
    public static final Dest EXIT = new Dest(null);

    public static Dest to(Level l) {
        return new Dest(l);
    }

    public boolean isExit() {
        return level == null;
    }

    public String describe() {
        return isExit() ? "EXIT" : level.displayName();
    }
}
