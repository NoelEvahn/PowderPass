package com.powderpass.backrooms.gen;

/** Mutable palette + proportions for one cell; filled in by each level's {@code style()} hook. */
public final class Style {
    public String name = "default";
    public B under;
    public B floor;
    public B floorAlt;
    public double floorAltChance = 0.06;
    public B wall;
    public B wallAlt;
    public int stripe = 0;
    public double wallStain = 0.0;
    public B wallStainBlock;
    public B base;
    public B trim;
    public B ceil;
    public B ceilAlt;
    public B above;
    public B light;
    public B deadLight;
    public double lit = 1.0;
    public int lightEveryX = 4;
    public int lightEveryZ = 4;
    public int lightOffX = 1;
    public int lightOffZ = 1;
    public int lightLength = 1;
    public int ceilY;
    public int doorH = 3;
    /** Corner posts survive even where all four walls are open (pillar halls). */
    public boolean keepCorner;
    public boolean pillar2x2;
}
