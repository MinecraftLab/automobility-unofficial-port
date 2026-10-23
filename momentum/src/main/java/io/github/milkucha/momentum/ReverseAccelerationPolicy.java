package io.github.milkucha.momentum;

/** Reduces an existing authorized reverse proposal; never creates power or changes braking. */
public final class ReverseAccelerationPolicy {
    public static final float MAX_REVERSE_INCREMENT = .008f;
    private ReverseAccelerationPolicy() {}
    public static float constrain(float before, float proposed, float floor, boolean peerSupports, boolean ordinary) {
        if (!peerSupports || !ordinary || !Float.isFinite(before) || !Float.isFinite(proposed)
                || !Float.isFinite(floor) || before > 0 || floor >= 0 || proposed >= before) return proposed;
        return Math.max(proposed, before - MAX_REVERSE_INCREMENT);
    }
}
