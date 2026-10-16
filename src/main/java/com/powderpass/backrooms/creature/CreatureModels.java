package com.powderpass.backrooms.creature;

import com.powderpass.backrooms.display.Model;

import java.util.List;

/** The creatures' bodies as plain display models, standing in their rest pose (for {@code /backrooms assets spawn}). */
public final class CreatureModels {
    public static final List<String> IDS = List.of("smiler", "faceling", "hound", "drifter", "watcher", "bacteria", "skinwalker");

    private CreatureModels() {
    }

    /** The body of a creature type id, or null. */
    public static Model build(String id) {
        return switch (id) {
            case "smiler" -> Bodies.smiler();
            case "faceling" -> Bodies.faceling();
            case "hound" -> Bodies.hound();
            case "drifter" -> Bodies.drifter();
            case "watcher" -> Bodies.watcher();
            case "bacteria" -> Bodies.bacteria();
            case "skinwalker" -> Bodies.skinwalker();
            default -> null;
        };
    }
}
