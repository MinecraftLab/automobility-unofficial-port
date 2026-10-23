package io.github.foundationgames.automobility.api.vehicle;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.Objects;
import java.util.UUID;

/** Optional server-only lifecycle observer; it owns no Garage decision, capsule, item or energy. */
public interface VehicleQuiescenceProvider {
    String ENTRYPOINT = "automobility:vehicle_quiescence_provider";
    enum Reason { GARAGE_HOLD, ENTITY_UNLOAD }
    record Notice(AutomobileEntity car, Reason reason, UUID transaction, long generation) {
        public Notice {
            Objects.requireNonNull(car);
            Objects.requireNonNull(reason);
            if (reason == Reason.GARAGE_HOLD ? transaction == null || generation < 1 : transaction != null || generation != 0)
                throw new IllegalArgumentException("invalid quiescence notice");
        }
    }
    String id();
    /** Synchronous server-thread cleanup of this exact object only. Never restart on rollback. */
    void quiesce(Notice notice);
}
