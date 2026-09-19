package io.github.milkucha.momentum;

/**
 * Shared brake state between the client-only SDL polling code and the
 * both-sides AutomobileEntityMixin.
 *
 * Kept in a class with zero client-specific imports so it can be safely
 * loaded on a dedicated server (where brakeHeld stays false permanently).
 *
 * The volatile keyword guarantees the server thread always reads the value
 * written by the client thread in the same tick, without stale caching.
 */
public class MomentumBrakeState {
    public static volatile boolean brakeHeld = false;

    // Client-side FOV offset for brake zoom effect. Written by MomentumClient each
    // tick, read by CameraMixin. Always 0 on a dedicated server.
    public static float brakeZoomOffset = 0f;
}
