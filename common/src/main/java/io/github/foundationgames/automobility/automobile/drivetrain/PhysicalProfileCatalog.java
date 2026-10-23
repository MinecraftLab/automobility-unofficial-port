package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Initial game calibrations, not manufacturer specifications or conversions of legacy parts indices. */
public final class PhysicalProfileCatalog {
    public record EngineDefinition(String id, EngineProfile engine, TransmissionProfile transmission,
                                   double clutchCapacityNewtonMetres) {
        public EngineDefinition {
            namespaced(id); Objects.requireNonNull(engine); Objects.requireNonNull(transmission);
            Units.positive(clutchCapacityNewtonMetres, "clutch capacity");
        }
        public TorqueCurve.Statistics garageStatistics() { return engine.statistics(); }
    }
    public record ChassisDefinition(String id, double emptyMassKg, double equivalentWheelInertiaKgMetresSquared,
                                    double dragAreaSquareMetres, double rollingResistanceCoefficient,
                                    double serviceBrakeTorqueNewtonMetres, double parkingHoldingTorqueNewtonMetres) {
        public ChassisDefinition {
            namespaced(id); Units.positive(emptyMassKg, "empty mass");
            Units.positive(equivalentWheelInertiaKgMetresSquared, "wheel inertia");
            Units.nonnegative(dragAreaSquareMetres, "drag area");
            Units.nonnegative(rollingResistanceCoefficient, "rolling resistance coefficient");
            Units.positive(serviceBrakeTorqueNewtonMetres, "service brake torque");
            Units.positive(parkingHoldingTorqueNewtonMetres, "parking torque");
        }
        public double airResistanceForceNewtons(double speedMetresPerSecond, double airDensityKgPerCubicMetre) {
            Units.finite(speedMetresPerSecond, "speed"); Units.nonnegative(airDensityKgPerCubicMetre, "air density");
            return -0.5 * airDensityKgPerCubicMetre * dragAreaSquareMetres * speedMetresPerSecond * Math.abs(speedMetresPerSecond);
        }
    }

    private static final TransmissionProfile FOUR = new TransmissionProfile(List.of(3.4, 2.0, 1.32, 0.95), 3.2, 4.1);
    private static final TransmissionProfile FIVE = new TransmissionProfile(List.of(3.6, 2.18, 1.5, 1.12, 0.88), 3.3, 4.0);
    private static final TransmissionProfile SIX = new TransmissionProfile(List.of(3.5, 2.2, 1.55, 1.16, 0.93, 0.77), 3.2, 3.9);
    private static final Map<String, EngineDefinition> ENGINES = Map.of(
            "automobility:stone", definition("stone", 0.32, 650, 4200, 145, FOUR,
                    200, 28, 650, 45, 1800, 90, 2800, 80, 3600, 48, 4200, 0),
            "automobility:copper", definition("copper", 0.25, 750, 5500, 195, FIVE,
                    200, 38, 750, 58, 2200, 125, 3500, 118, 4700, 85, 5500, 0),
            "automobility:iron", definition("iron", 0.23, 800, 6500, 270, FIVE,
                    200, 55, 800, 85, 3000, 180, 5000, 165, 6000, 110, 6500, 0),
            "automobility:gold", definition("gold", 0.36, 700, 6000, 390, FIVE,
                    200, 78, 700, 130, 2000, 250, 3500, 260, 5000, 200, 6000, 0),
            "automobility:diamond", definition("diamond", 0.21, 900, 7500, 470, SIX,
                    250, 60, 900, 110, 2800, 230, 4800, 310, 6500, 285, 7500, 0),
            "automobility:creative", definition("creative", 0.25, 850, 8500, 650, SIX,
                    200, 90, 850, 150, 2500, 320, 5000, 420, 7200, 400, 8500, 0));

    public static final ChassisDefinition GENERIC = new ChassisDefinition("automobility:generic_physical_chassis", 1100, 2.2, 0.75, 0.015, 4200, 4500);
    private static final Map<String, ChassisDefinition> CHASSIS = Map.of(
            "automobility:mx5_na", new ChassisDefinition("automobility:mx5_na", 960, 1.8, 0.62, 0.012, 3900, 4200),
            "automobility:defender_90", new ChassisDefinition("automobility:defender_90", 1850, 4.0, 1.25, 0.022, 7600, 8500),
            "automobility:vw_t3", new ChassisDefinition("automobility:vw_t3", 1550, 3.2, 1.15, 0.018, 6500, 7200));

    private PhysicalProfileCatalog() { }
    public static Map<String, EngineDefinition> engines() { return ENGINES; }
    public static Optional<EngineDefinition> engine(String registryId) { namespaced(registryId); return Optional.ofNullable(ENGINES.get(registryId)); }
    public static Optional<ChassisDefinition> chassis(String registryId) { namespaced(registryId); return Optional.ofNullable(CHASSIS.get(registryId)); }

    /** Generic fallback is explicit in presentation and never invents a profile for an unknown engine. */
    public static DrivetrainParameters mechanics(EngineDefinition engine, ChassisDefinition chassis, double wheelRadiusMetres, double payloadMassKg) {
        Objects.requireNonNull(engine); Objects.requireNonNull(chassis); Units.nonnegative(payloadMassKg, "payload mass");
        return DrivetrainParameters.forEngine(engine.engine(), chassis.equivalentWheelInertiaKgMetresSquared(),
                chassis.emptyMassKg() + payloadMassKg, wheelRadiusMetres, engine.clutchCapacityNewtonMetres(), engine.transmission());
    }
    private static EngineDefinition definition(String name, double inertia, double idle, double redline, double clutch,
                                               TransmissionProfile transmission, double... samples) {
        var points = new java.util.ArrayList<TorqueCurve.Point>();
        if (samples.length % 2 != 0) throw new IllegalArgumentException("RPM/torque pairs required");
        for (int i = 0; i < samples.length; i += 2) points.add(new TorqueCurve.Point(samples[i], samples[i + 1]));
        return new EngineDefinition("automobility:" + name, new EngineProfile(new TorqueCurve(points), inertia, idle, redline), transmission, clutch);
    }
    private static void namespaced(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("namespaced profile key required");
    }
}
