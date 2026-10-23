package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.automobile.access.VehicleAccessState;
import io.github.foundationgames.automobility.automobile.driving.VehicleDrivingMode;
import io.github.foundationgames.automobility.automobile.engine.VehicleEngineSettings;
import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;
import java.util.Objects;

/** Operational preferences only: no owner, keys, lock state, ignition or global Momentum tuning. */
public record VehicleConfigurationProfile(String name, VehicleEngineSettings engine, VehicleLightMode lights,
                                          VehicleAccessState.Feedback feedback, boolean signalAutoCancel,
                                          boolean comfortSignals, boolean lockSignalConfirmation,
                                          VehicleDrivingMode drivingMode) {
    public VehicleConfigurationProfile(String name, VehicleEngineSettings engine, VehicleLightMode lights,
                                       VehicleAccessState.Feedback feedback, boolean signalAutoCancel,
                                       boolean comfortSignals, boolean lockSignalConfirmation) {
        this(name, engine, lights, feedback, signalAutoCancel, comfortSignals, lockSignalConfirmation,
                VehicleDrivingMode.INHERIT);
    }

    public VehicleConfigurationProfile(String name, VehicleEngineSettings engine, VehicleLightMode lights,
                                       VehicleAccessState.Feedback feedback) {
        this(name, engine, lights, feedback, true, true, false);
    }

    public VehicleConfigurationProfile(String name, VehicleEngineSettings engine, VehicleLightMode lights,
                                       VehicleAccessState.Feedback feedback, boolean signalAutoCancel) {
        this(name, engine, lights, feedback, signalAutoCancel, true, false);
    }

    public VehicleConfigurationProfile {
        name = Objects.requireNonNull(name).strip();
        if (name.isEmpty() || name.length() > 32 || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid profile name");
        Objects.requireNonNull(engine); Objects.requireNonNull(lights);
        Objects.requireNonNull(drivingMode);
        feedback = Objects.requireNonNull(feedback).withoutHorn();
        lockSignalConfirmation = false;
        if (!VehicleEngineSettings.validDelay(engine.idleShutdownSeconds())) throw new IllegalArgumentException("Invalid shutdown delay");
    }
}
