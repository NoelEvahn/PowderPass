package dev.powderpass.volleyball.ball;

import dev.powderpass.volleyball.court.Court;
import dev.powderpass.volleyball.hit.HitResult;
import dev.powderpass.volleyball.math.Vec3;
import dev.powderpass.volleyball.physics.Aabb;
import dev.powderpass.volleyball.physics.NetContact;
import org.bukkit.entity.Player;

/** Owner of a ball: a match or free play. Receives gameplay events. */
public interface BallContext {

    /** Court whose net the ball collides with (null = none). */
    Court court(Volleyball ball);

    /** Whether {@code player} may touch this ball right now. */
    boolean canTouch(Player player, Volleyball ball);

    void onHit(Player player, Volleyball ball, HitResult result);

    void onBlockContact(Volleyball ball, Vec3 position, Vec3 normal, double impact, Aabb box);

    void onNetContact(Volleyball ball, NetContact contact, Vec3 localPoint, double impact);

    void onNetCross(Volleyball ball, int fromSide, double localW, double localY);

    /** Called every tick after the ball moved. */
    default void onTick(Volleyball ball) {
    }

    /**
     * Aim assist: a floor point the touch should go to instead of the player's free aim (null = free aim).
     * Contact quality still adds its error on top.
     */
    default Vec3 assistTarget(Player player, Volleyball ball, dev.powderpass.volleyball.hit.HitType type) {
        return null;
    }

    /** Maximum outgoing speed (m/s) for a touch type, e.g. controlled hits in a pepper drill. */
    default double speedCap(dev.powderpass.volleyball.hit.HitType type) {
        return Double.MAX_VALUE;
    }

    /** True if the ball should be removed (e.g. idle practice ball). */
    default boolean shouldDespawn(Volleyball ball) {
        return false;
    }
}
