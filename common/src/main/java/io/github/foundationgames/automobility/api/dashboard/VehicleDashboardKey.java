package io.github.foundationgames.automobility.api.dashboard;

import java.util.Objects;
import java.util.UUID;

/** Exact loaded vehicle identity. Incarnation is provider-issued and never a player identity. */
public record VehicleDashboardKey(String dimensionId, UUID vehicleId, int entityId, long incarnation) {
    public VehicleDashboardKey {
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(vehicleId, "vehicleId");
        if (!dimensionId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || dimensionId.length() > 256
                || entityId < 0 || incarnation <= 0) throw new IllegalArgumentException("invalid vehicle dashboard key");
    }
}
