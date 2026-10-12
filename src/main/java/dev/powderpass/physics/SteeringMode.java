package dev.powderpass.physics;

/** How the rider steers on the ground. */
public enum SteeringMode {
    /** Skis follow the camera; A/D add a sharper carve. Feels like aiming your body down the hill. */
    LOOK,
    /** A/D rotate the skis like a boat; the camera is free to look around. */
    KEYS
}
