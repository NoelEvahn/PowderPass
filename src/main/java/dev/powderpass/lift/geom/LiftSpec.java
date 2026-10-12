package dev.powderpass.lift.geom;

/**
 * Inputs for laying out a chairlift between two station floor points.
 *
 * @param bx,by,bz          bottom station floor (where riders stand to load)
 * @param tx,ty,tz          top station floor
 * @param lineHalfSpacing   half the distance between the up and down rope (also the bullwheel radius)
 * @param hangerLength      distance from the rope down to the seat
 * @param seatHeight        seat height above the station floor while loading
 * @param platformLength    length of the level station section before the rope climbs
 * @param maxTowerSpacing   maximum horizontal distance between supports
 * @param minSeatClearance  minimum height of a seat above the terrain along the line
 * @param sagRatio          rope sag as a fraction of the span length
 * @param stationSlow       speed factor inside stations (detachable-style slow loading), 1 = fixed grip
 */
public record LiftSpec(double bx, double by, double bz,
                       double tx, double ty, double tz,
                       double lineHalfSpacing,
                       double hangerLength,
                       double seatHeight,
                       double platformLength,
                       double maxTowerSpacing,
                       double minSeatClearance,
                       double sagRatio,
                       double stationSlow) {

    public double horizontalLength() {
        return Math.hypot(tx - bx, tz - bz);
    }

    public double minimumLength() {
        return platformLength * 2.0 + 8.0;
    }
}
