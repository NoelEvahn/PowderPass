package dev.powderpass.volleyball.physics;

import dev.powderpass.volleyball.math.CourtFrame;

/** A net placed in the world. */
public record CourtNet(CourtFrame frame, NetGeometry geometry) {
}
