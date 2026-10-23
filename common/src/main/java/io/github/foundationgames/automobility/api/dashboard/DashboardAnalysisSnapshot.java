package io.github.foundationgames.automobility.api.dashboard;

import java.util.Objects;

/** Existing decoder's envelope, not a request to decode. Generation is scoped to this vehicle key. */
public record DashboardAnalysisSnapshot(VehicleDashboardKey key, long outputId, long generation, long capturedAtNanos,
        float level, boolean available) {
    public DashboardAnalysisSnapshot {
        Objects.requireNonNull(key);
        if (outputId < 0 || generation < 0 || available && outputId == 0 || !Float.isFinite(level)
                || level < 0f || level > 1f || !available && level != 0f)
            throw new IllegalArgumentException("invalid dashboard analysis snapshot");
    }

    public static DashboardAnalysisSnapshot unavailable(VehicleDashboardKey key, long generation, long now) {
        return new DashboardAnalysisSnapshot(key, 0, generation, now, 0f, false);
    }
}
