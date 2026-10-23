package io.github.foundationgames.automobility.api.dashboard;

import java.util.Optional;
import java.util.UUID;

/** Optional client integration; implementations must not start playback, downloads or control commands. */
public interface DashboardProvider {
    String id();
    Optional<VehicleDashboardKey> resolveKey(String dimensionId, UUID vehicleId, int entityId);
    Optional<DashboardSnapshot> snapshot(VehicleDashboardKey exactKey);
}
