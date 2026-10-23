package io.github.foundationgames.automobility.automobile.light;

/** Shared bounded hinge clock, sampled in render time but advanced only by game ticks. */
final class TickDrivenHingeAnimation {
    private boolean initialized;
    private float previous, current;

    void tick(boolean open, int durationTicks) {
        if (durationTicks < 1) throw new IllegalArgumentException("Positive hinge duration required");
        float target = open ? 1 : 0;
        if (!initialized) {
            previous = current = target;
            initialized = true;
            return;
        }
        previous = current;
        float step = 1.0f / durationTicks;
        float difference = target - current;
        current = Math.abs(difference) <= step + .000001f ? target : current + Math.copySign(step, difference);
    }

    float sample(float tickDelta, boolean open) {
        if (!initialized) return open ? 1 : 0;
        float partial = Float.isFinite(tickDelta) ? Math.clamp(tickDelta, 0, 1) : 0;
        float t = previous + (current - previous) * partial;
        return t * t * (3 - 2 * t);
    }

    void reset() { initialized = false; previous = current = 0; }
}
