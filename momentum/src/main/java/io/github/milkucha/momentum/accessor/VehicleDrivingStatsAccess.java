package io.github.milkucha.momentum.accessor;

import io.github.foundationgames.automobility.entity.AutomobileEntity;

/** Momentum-only owner context for the entity's existing, stable AutomobileStats instance. */
public interface VehicleDrivingStatsAccess {
    void momentum$bindDrivingOwner(AutomobileEntity automobile);
}
