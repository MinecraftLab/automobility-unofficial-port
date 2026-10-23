package io.github.foundationgames.automobility.api.vehicle;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.*;
import net.minecraft.server.level.ServerLevel;

/** A discovered, immutable observer list, separate from transaction and energy provider registries. */
public final class VehicleQuiescenceProviders {
    private static List<VehicleQuiescenceProvider> providers = List.of();
    private static boolean installed, failed;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("AutomobilityVehicleLifecycle");
    private VehicleQuiescenceProviders() { }

    public static boolean available() { return installed && !failed; }

    public static synchronized void install(List<VehicleQuiescenceProvider> candidates, boolean discoveryFailed) {
        if (installed) throw new IllegalStateException("vehicle quiescence discovery installs once");
        var selected = new TreeMap<String, VehicleQuiescenceProvider>();
        failed = discoveryFailed;
        for (var provider : candidates) {
            try {
                String id = provider.id();
                if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                        || selected.putIfAbsent(id, provider) != null || selected.size() > 16) failed = true;
            } catch (RuntimeException | LinkageError invalid) { failed = true; }
        }
        providers = List.copyOf(selected.values());
        installed = true;
    }

    public static void garageHold(AutomobileEntity car, UUID transaction, long generation) {
        dispatch(new VehicleQuiescenceProvider.Notice(car, VehicleQuiescenceProvider.Reason.GARAGE_HOLD, transaction, generation));
    }

    public static void entityUnload(AutomobileEntity car) {
        dispatch(new VehicleQuiescenceProvider.Notice(car, VehicleQuiescenceProvider.Reason.ENTITY_UNLOAD, null, 0));
    }

    private static void dispatch(VehicleQuiescenceProvider.Notice notice) {
        boolean unloading = notice.reason() == VehicleQuiescenceProvider.Reason.ENTITY_UNLOAD;
        if (!(notice.car().level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            if (unloading) { LOGGER.error("Vehicle unload observer invoked outside its actual server thread"); return; }
            throw new IllegalStateException("vehicle quiescence requires the actual server thread");
        }
        if (!installed || failed) {
            if (!unloading) throw new IllegalStateException("vehicle quiescence discovery unavailable");
            LOGGER.warn("Vehicle unload observers have incomplete discovery; continuing normal entity removal");
        }
        RuntimeException problem = null;
        for (var provider : providers) {
            try { provider.quiesce(notice); }
            catch (RuntimeException | LinkageError failure) {
                if (problem == null) problem = new IllegalStateException("vehicle quiescence observer failed: " + provider.getClass().getName(), failure);
                else problem.addSuppressed(failure);
            }
        }
        if (problem != null) {
            if (unloading) LOGGER.warn("Vehicle unload observer cleanup failed; continuing normal entity removal", problem);
            else throw problem;
        }
    }
}
