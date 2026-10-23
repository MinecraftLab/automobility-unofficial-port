package io.github.foundationgames.automobility.api.dashboard;

import java.util.Objects;

/** Local cosmetic cabin response to an existing output. Never a vehicle light, media or control request. */
public record DashboardDecorationSnapshot(VehicleDashboardKey key, long outputId, long generation,
        long analysisSequence, int rgb, float intensity) {
    public static final float MAX_INTENSITY = .35f;
    public DashboardDecorationSnapshot {
        Objects.requireNonNull(key);
        if (outputId < 0 || generation < 0 || analysisSequence < 0 || (rgb & 0xff000000) != 0
                || !Float.isFinite(intensity) || intensity < 0 || intensity > MAX_INTENSITY
                || intensity > 0 && outputId == 0)
            throw new IllegalArgumentException("invalid cabin decoration snapshot");
    }
    public static DashboardDecorationSnapshot off(VehicleDashboardKey key) {
        return new DashboardDecorationSnapshot(key, 0, 0, 0, 0, 0);
    }
}
