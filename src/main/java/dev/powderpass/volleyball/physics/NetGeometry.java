package dev.powderpass.volleyball.physics;

/**
 * Net geometry in court-local space (u across the net plane, y height above floor, w along the net).
 *
 * @param top            height of the top edge of the net
 * @param bandHeight     vertical size of the mesh (1 m official)
 * @param halfLength     the net spans w in [-halfLength, halfLength]
 * @param antennaW       antennas stand at w = +-antennaW (outer edge of the side lines)
 * @param antennaAbove   antenna height above the top of the net (0.8 m official)
 * @param antennaRadius  collision radius of the antenna rod
 * @param tapeRadius     radius of the top band / cable
 * @param postW          posts stand at w = +-postW
 * @param postRadius     collision radius of the posts
 * @param meshHalfThickness half thickness of the mesh membrane
 */
public record NetGeometry(
        double top,
        double bandHeight,
        double halfLength,
        double antennaW,
        double antennaAbove,
        double antennaRadius,
        double tapeRadius,
        double postW,
        double postRadius,
        double meshHalfThickness
) {

    public double bottom() {
        return top - bandHeight;
    }

    public double tapeCenterY() {
        return top - tapeRadius;
    }
}
