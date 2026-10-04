package io.github.milkucha.momentum;

/**
 * Small, deterministic store for a cruise-control set speed.
 *
 * <p>The vehicle-facing controller owns activation and cancellation. This class
 * owns only target memory and clamping, which keeps resume and step adjustment
 * behaviour easy to test without constructing a Minecraft entity.</p>
 */
final class CruiseTargetState {
    private boolean stored;
    private float targetKmh;

    float set(float requestedKmh, float minimumKmh, float maximumKmh) {
        targetKmh = clamp(requestedKmh, minimumKmh, maximumKmh);
        stored = true;
        return targetKmh;
    }

    float adjust(float deltaKmh, float minimumKmh, float maximumKmh) {
        if (!stored) {
            return 0f;
        }
        return set(targetKmh + deltaKmh, minimumKmh, maximumKmh);
    }

    boolean hasStoredTarget() {
        return stored;
    }

    float targetKmh() {
        return stored ? targetKmh : 0f;
    }

    void clear() {
        stored = false;
        targetKmh = 0f;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
