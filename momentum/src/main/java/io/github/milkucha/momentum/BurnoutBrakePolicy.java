package io.github.milkucha.momentum;

/** Only the new, unintended forward S-only burnout start is intercepted. */
public final class BurnoutBrakePolicy {
    private BurnoutBrakePolicy() {}
    public static boolean suppressStart(boolean enabled, boolean externalOwner,
            boolean alreadyBurning, boolean accelerating, boolean baseBrake,
            boolean rawBrake, float forwardSpeed, boolean unsafeDrift) {
        return enabled && !externalOwner && !alreadyBurning && !accelerating
                && baseBrake && rawBrake && forwardSpeed > 0.05d && !unsafeDrift;
    }
}
