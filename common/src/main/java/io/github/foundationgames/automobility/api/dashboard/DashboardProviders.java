package io.github.foundationgames.automobility.api.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Dependency-free registry; only the new Core client discovers the optional Fabric entrypoint. */
public final class DashboardProviders {
    public static final String ENTRYPOINT = "automobility:dashboard_provider";
    private final Map<String, DashboardProvider> providers = new LinkedHashMap<>();

    public synchronized void register(DashboardProvider provider) {
        Objects.requireNonNull(provider);
        String id = Objects.requireNonNull(provider.id());
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || providers.containsKey(id))
            throw new IllegalArgumentException("invalid or duplicate dashboard provider");
        providers.put(id, provider);
    }

    public synchronized List<String> ids() { return List.copyOf(providers.keySet()); }
    private synchronized List<DashboardProvider> providers() { return new ArrayList<>(providers.values()); }

    public Optional<VehicleDashboardKey> resolveKey(String dimensionId, UUID vehicleId, int entityId) {
        Objects.requireNonNull(dimensionId); Objects.requireNonNull(vehicleId);
        for (DashboardProvider provider : providers()) {
            try {
                var key = provider.resolveKey(dimensionId, vehicleId, entityId);
                if (key != null && key.isPresent() && key.get().dimensionId().equals(dimensionId)
                        && key.get().vehicleId().equals(vehicleId) && key.get().entityId() == entityId) return key;
            } catch (RuntimeException | LinkageError ignored) { /* Optional integration failure cannot break vehicle controls. */ }
        }
        return Optional.empty();
    }

    public Optional<DashboardSnapshot> snapshot(VehicleDashboardKey key) {
        Objects.requireNonNull(key);
        for (DashboardProvider provider : providers()) {
            try {
                var result = provider.snapshot(key);
                if (result != null && result.isPresent() && result.get().key().equals(key)) return result;
            } catch (RuntimeException | LinkageError ignored) { /* Fail closed; do not substitute another vehicle's metadata. */ }
        }
        return Optional.empty();
    }
}
