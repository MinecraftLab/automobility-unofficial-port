package io.github.foundationgames.automobility.client.lighting;

import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;
import io.github.foundationgames.automobility.automobile.light.VehicleSignalMode;
import io.github.foundationgames.automobility.entity.AutomobileEntity;

import java.util.Objects;

/** One immutable authoritative lamp state shared by lens and surface submissions. */
public record VehicleLampSnapshot(VehicleLightMode lightMode, VehicleSignalMode signalMode, boolean signalLit,
                                  boolean brakeLit, boolean reverseLit) {
    public VehicleLampSnapshot {
        Objects.requireNonNull(lightMode);
        Objects.requireNonNull(signalMode);
    }

    public VehicleLampSnapshot(VehicleLightMode lightMode, VehicleSignalMode signalMode, boolean signalLit) {
        this(lightMode, signalMode, signalLit, false, false);
    }

    public static VehicleLampSnapshot capture(AutomobileEntity car) {
        // Render auxiliary lock flashes without changing the ordinary mode used by the HUD and relay audio.
        return new VehicleLampSnapshot(car.getEffectiveVehicleLightMode(), car.getVisibleVehicleSignalMode(), car.vehicleVisibleSignalLit(),
                car.vehicleBrakeLightsLit(), car.vehicleReverseLightsLit());
    }

    /** Keep discovery/culling stable through a signal's dark half; there is no client blink clock. */
    public boolean hasPotentialSmallLight() {
        return lightMode.hasPositionLights() || signalMode != VehicleSignalMode.OFF || brakeLit || reverseLit;
    }
}
