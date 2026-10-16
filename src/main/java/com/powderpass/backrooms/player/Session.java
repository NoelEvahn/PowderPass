package com.powderpass.backrooms.player;

import com.powderpass.backrooms.creature.CreatureType;
import com.powderpass.backrooms.gen.Feature;
import org.bukkit.Location;

/** Volatile per-player state (persistent parts live in the player's PersistentDataContainer). */
public final class Session {
    public double sanity = 100;
    public boolean inTransition;
    public long cooldownUntil;
    public long arrivalTick;
    public Feature zone;
    public int zoneTicks;
    public CreatureType lastCreature;
    public long lastCreatureAt;
    public String lastHazard;
    public long lastHazardAt;
    public long vendCooldownUntil;
    public Location fakeLight;
    /** The flashlight's soft glow just ahead of the holder (a second, dimmer fake light). */
    public Location fakeSpill;
    public int flashlightCharge = -1;
    public int flashlightSaveIn;
    public long noiseCooldownUntil;
    public boolean diedInside;
    public boolean debug;
    public double lastShownSanity = 100;
}
