package io.github.milkucha.momentum;

/** Forward pedal only. Handbrake, reverse drive and external owners keep their own decay. */
public final class PedalBrakePolicy {
    private PedalBrakePolicy() {}

    /** Explicit registered van, not the arcade weight statistic. Unknown/direct frames stay unchanged. */
    public static float frameMultiplier(String namespace, String framePath) {
        return "riautomobility".equals(namespace) && "lorry".equals(framePath) ? 0.5f : 1f;
    }

    public static float decay(float engineSpeed, float resolvedDecay, float forwardScale,
                              boolean vehicleBalance, String namespace, String framePath) {
        float base = decay(engineSpeed, resolvedDecay, forwardScale);
        return engineSpeed > 0f && vehicleBalance ? base * frameMultiplier(namespace, framePath) : base;
    }

    public static float decay(float engineSpeed, float resolvedDecay, float forwardScale) {
        if (!(engineSpeed > 0f)) return resolvedDecay;
        float scale = Float.isFinite(forwardScale) ? Math.clamp(forwardScale, 0.25f, 1f) : 1f;
        return resolvedDecay * scale;
    }
}
