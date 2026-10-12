package dev.powderpass.physics;

/** Broad classes of ground a skier can be travelling over. */
public enum SurfaceKind {
    /** Snow blocks and snow layers: the ideal groomed piste. */
    SNOW,
    /** Powder snow blocks: deep, draggy and spectacular. */
    POWDER,
    /** Ice variants: very fast, very little edge grip. */
    ICE,
    /** Any other solid block. Skis scrape and grind to a halt. */
    ROUGH,
    /** Water or lava. Ends the run. */
    LIQUID,
    /** Nothing found (void / unloaded). */
    NONE
}
