package io.github.milkucha.momentum;

/**
 * Shared brake state between the client-only GLFW polling code and the
 * both-sides AutomobileEntityMixin.
 *
 * Kept in a class with zero client-specific imports so it can be safely
 * loaded on a dedicated server (where brakeHeld stays false permanently).
 *
 * The volatile keyword guarantees the server thread always reads the value
 * written by the client thread in the same tick, without stale caching.
 */
public class MomentumBrakeState {
    /** Accepted physical input, including while the optional brake effect is disabled. */
    public static volatile boolean rawBrakeHeld = false;
    /** Active presentation/cruise state; keeps its existing gameplay-enabled semantics. */
    public static volatile boolean brakeHeld = false;

    public static void sampleInput(boolean rawHeld, boolean enabled) {
        rawBrakeHeld = rawHeld;
        brakeHeld = enabled && rawHeld;
    }

    // Client-side FOV offset for brake zoom effect. Written by MomentumClient each
    // tick, read by CameraMixin. Always 0 on a dedicated server.
    public static float brakeZoomOffset = 0f;
    // Render-only snapshots. The raw spring value above remains a tick-domain target.
    public static final BrakeZoomDisplayState brakeZoomDisplay = new BrakeZoomDisplayState();
}
