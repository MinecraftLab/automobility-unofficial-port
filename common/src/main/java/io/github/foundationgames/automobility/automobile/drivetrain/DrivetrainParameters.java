package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.Objects;

/** One equivalent driven axle; values are physical SI data, never legacy handling indices. */
public record DrivetrainParameters(double engineInertiaKgMetresSquared, double wheelInertiaKgMetresSquared,
                                   double vehicleMassKg, double wheelRadiusMetres,
                                   double clutchCapacityNewtonMetres, TransmissionProfile transmission) {
    public DrivetrainParameters {
        Units.positive(engineInertiaKgMetresSquared, "engine inertia");
        Units.positive(wheelInertiaKgMetresSquared, "wheel inertia");
        Units.positive(vehicleMassKg, "mass");
        Units.positive(wheelRadiusMetres, "wheel radius");
        Units.nonnegative(clutchCapacityNewtonMetres, "clutch capacity");
        Objects.requireNonNull(transmission, "transmission");
    }

    /** Production ICE adapters must bind the same profile used by the garage. */
    public static DrivetrainParameters forEngine(EngineProfile engine, double wheelInertiaKgMetresSquared,
                                                double vehicleMassKg, double wheelRadiusMetres,
                                                double clutchCapacityNewtonMetres, TransmissionProfile transmission) {
        Objects.requireNonNull(engine, "engine");
        return new DrivetrainParameters(engine.inertiaKgMetresSquared(), wheelInertiaKgMetresSquared,
                vehicleMassKg, wheelRadiusMetres, clutchCapacityNewtonMetres, transmission);
    }
}
