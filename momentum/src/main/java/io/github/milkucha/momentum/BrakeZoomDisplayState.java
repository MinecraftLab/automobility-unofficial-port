package io.github.milkucha.momentum;

/** Tick snapshots for render interpolation; never changes vehicle motion or the spring target. */
public final class BrakeZoomDisplayState {
    private float previous;
    private float current;
    private boolean comfort;
    private float cap;
    private double stageOne, stageTwo;
    private double beforeOne, beforeTwo, target;
    private static final double OMEGA = 4.0;
    private static final double TICK_SECONDS = 0.05;

    public void tick(float offset) {
        tick(offset, false, 0f);
    }

    /** Private opt-in: exact cascaded low-pass dynamics, including sub-tick rendering. */
    public void tick(float offset, boolean useComfort, float configuredMaximum) {
        float limit = Float.isFinite(configuredMaximum) ? Math.clamp(configuredMaximum, 0f, 5f) : 0f;
        if (comfort != useComfort || (useComfort && cap != limit)) reset();
        comfort = useComfort;
        cap = limit;
        if (comfort) {
            beforeOne = stageOne;
            beforeTwo = stageTwo;
            target = Float.isFinite(offset) ? Math.clamp(offset, 0f, cap) : 0f;
            double e = Math.exp(-OMEGA * TICK_SECONDS);
            stageOne = target + (beforeOne - target) * e;
            stageTwo = output(TICK_SECONDS);
            return;
        }
        previous = current;
        current = Float.isFinite(offset) ? Math.max(0f, offset) : 0f;
    }

    public float sample(float partialTick) {
        float delta = Float.isNaN(partialTick) ? 1f : Math.max(0f, Math.min(1f, partialTick));
        if (comfort) return (float) Math.clamp(output(delta * TICK_SECONDS), 0.0, cap);
        return previous + (current - previous) * delta;
    }

    private double output(double seconds) {
        return target + ((beforeTwo - target) + OMEGA * seconds * (beforeOne - target))
                * Math.exp(-OMEGA * seconds);
    }

    public void reset() {
        previous = current = 0f;
        comfort = false;
        cap = 0f;
        stageOne = stageTwo = beforeOne = beforeTwo = target = 0.0;
    }

    /** Signed horizontal speed describes direction, not the amount of kinetic speed lost. */
    public static float deceleration(float previousSpeed, float currentSpeed) {
        if (!Float.isFinite(previousSpeed) || !Float.isFinite(currentSpeed)) return 0f;
        return Math.max(0f, Math.abs(previousSpeed) - Math.abs(currentSpeed));
    }
}
