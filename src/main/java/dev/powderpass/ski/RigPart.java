package dev.powderpass.ski;

/** Every display entity an animated skier can be made of. */
public enum RigPart {
    SKI_L, SKI_R, TIP_L, TIP_R,
    BOARD, NOSE, TAIL,
    LEG_L, LEG_R, TORSO, ARM_L, ARM_R, POLE_L, POLE_R,
    HEAD, GOGGLES, POMPOM,
    NAMETAG;

    public boolean skisOnly() {
        return this == SKI_L || this == SKI_R || this == TIP_L || this == TIP_R || this == POLE_L || this == POLE_R;
    }

    public boolean boardOnly() {
        return this == BOARD || this == NOSE || this == TAIL;
    }

    /** Parts hidden from the rider's own first-person view. */
    public boolean hiddenFromRider() {
        return this == HEAD || this == GOGGLES || this == POMPOM || this == NAMETAG;
    }
}
