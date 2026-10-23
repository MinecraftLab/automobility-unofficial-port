package io.github.milkucha.momentum;

/** Ordinary forward S in Original; reverse, engine-off and external owners stay upstream. */
public final class OriginalBrakePolicy {
    public static final float ORDINARY_DECAY = 0.03f;
    private OriginalBrakePolicy() {}

    public static float runningResult(float originalResult, float engineSpeed, float floor,
            boolean alreadyBurning, boolean accelerating, boolean baseBrake,
            boolean rawBrake, boolean unsafeDrift) {
        if (alreadyBurning || accelerating || !baseBrake || !rawBrake || unsafeDrift
                || !Float.isFinite(engineSpeed) || !(engineSpeed > 0f)) return originalResult;
        return Math.max(engineSpeed - ORDINARY_DECAY, floor);
    }
}
