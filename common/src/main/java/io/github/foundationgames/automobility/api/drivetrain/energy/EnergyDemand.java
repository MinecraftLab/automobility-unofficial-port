package io.github.foundationgames.automobility.api.drivetrain.energy;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable SI request. No field is an energy store or permission to mutate one. */
public record EnergyDemand(Identity identity, long tick, UUID requestId, Source source, Range range,
                           double shaftRpm, double loadFraction, double throttleFraction, double seconds,
                           Work work, BrakeIntent brake, Flags flags) {
    public enum Source { FUEL, ELECTRIC, STARTER }
    public enum Range { FORWARD, REVERSE, NEUTRAL, PARK }

    public record Identity(UUID worldSessionId, UUID runtimeId, UUID vehicleId, Optional<UUID> physicalMotorId) {
        public Identity {
            Objects.requireNonNull(worldSessionId, "world session");
            Objects.requireNonNull(runtimeId, "runtime");
            Objects.requireNonNull(vehicleId, "vehicle");
            physicalMotorId = Objects.requireNonNull(physicalMotorId, "physical motor optional");
        }
        public static Identity vehicle(UUID session, UUID runtime, UUID vehicle) {
            return new Identity(session, runtime, vehicle, Optional.empty());
        }
        public static Identity motor(UUID session, UUID runtime, UUID vehicle, UUID motor) {
            return new Identity(session, runtime, vehicle, Optional.of(motor));
        }
    }

    /**
     * Shaft work is signed; active-source work components are nonnegative. Regen is explicit measured extraction
     * from an immutable mechanics preview, not merely an available motor capability.
     * The electric torque argument is RMS-equivalent current-cost data, not signed control/display torque.
     * Electric delivered work is the integral of positive electric shaft power, never max(0, signed net work).
     */
    public record Work(double combustionTorqueNewtonMetres, double starterTorqueNewtonMetres,
                       double electricTorqueNewtonMetres, double combustionWorkJoules,
                       double starterWorkJoules, double shaftWorkJoules, double regenerationMechanicalJoules,
                       double electricDeliveredMechanicalJoules) {
        public Work {
            nonnegative(combustionTorqueNewtonMetres, "combustion torque");
            nonnegative(starterTorqueNewtonMetres, "starter torque");
            finite(electricTorqueNewtonMetres, "electric torque");
            nonnegative(combustionWorkJoules, "combustion work");
            nonnegative(starterWorkJoules, "starter work");
            finite(shaftWorkJoules, "shaft work");
            nonnegative(regenerationMechanicalJoules, "regeneration request");
            nonnegative(electricDeliveredMechanicalJoules, "electric delivered work");
            double positiveWork = finite(combustionWorkJoules + starterWorkJoules + electricDeliveredMechanicalJoules, "positive source work");
            if (shaftWorkJoules > positiveWork && !sameWork(shaftWorkJoules, positiveWork)) {
                throw new IllegalArgumentException("signed shaft work exceeds positive source work");
            }
            if (regenerationMechanicalJoules > Math.max(0.0, -shaftWorkJoules)) {
                throw new IllegalArgumentException("regeneration exceeds negative mechanical work");
            }
        }
        public Work(double combustionTorqueNewtonMetres, double starterTorqueNewtonMetres,
                    double electricTorqueNewtonMetres, double combustionWorkJoules,
                    double starterWorkJoules, double shaftWorkJoules, double regenerationMechanicalJoules) {
            this(combustionTorqueNewtonMetres, starterTorqueNewtonMetres, electricTorqueNewtonMetres,
                    combustionWorkJoules, starterWorkJoules, shaftWorkJoules, regenerationMechanicalJoules,
                    legacyElectricDeliveredWork(shaftWorkJoules, combustionWorkJoules, starterWorkJoules));
        }
        public double electricTorqueRmsNewtonMetres() { return Math.abs(electricTorqueNewtonMetres); }
    }

    /** Physical pedal intent remains distinct from derived/active braking. */
    public record BrakeIntent(boolean physicalBrake, boolean activeBrake) { }
    public record Flags(boolean combustionRunning, boolean fuelCut, boolean grounded, boolean hazard,
                        boolean activePowerPermitted, boolean starterPermitted, boolean regenerationPermitted) { }

    public EnergyDemand {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(requestId, "request id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(brake, "brake intent");
        Objects.requireNonNull(flags, "flags");
        if (tick < 0L) throw new IllegalArgumentException("negative tick");
        finite(shaftRpm, "shaft RPM");
        between(loadFraction, 0.0, 1.0, "load");
        between(throttleFraction, 0.0, 1.0, "throttle");
        positive(seconds, "seconds");
        if (source == Source.ELECTRIC && identity.physicalMotorId().isEmpty()) {
            throw new IllegalArgumentException("traction energy requires physical motor identity");
        }
    }

    static double finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        return value;
    }
    static double nonnegative(double value, String name) {
        if (finite(value, name) < 0.0) throw new IllegalArgumentException(name + " must be nonnegative");
        return value;
    }
    static double positive(double value, String name) {
        if (finite(value, name) <= 0.0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
    static double between(double value, double lo, double hi, String name) {
        if (finite(value, name) < lo || value > hi) throw new IllegalArgumentException(name + " outside range");
        return value;
    }
    static boolean sameWork(double a, double b) {
        return Double.isFinite(a) && Double.isFinite(b)
                && Math.abs(a - b) <= 1.0e-10 * Math.max(1.0, Math.max(Math.abs(a), Math.abs(b)));
    }
    static double legacyElectricDeliveredWork(double shaft, double combustion, double starter) {
        double otherPositive = combustion + starter;
        if (otherPositive > 0.0 && sameWork(shaft, otherPositive)) return 0.0;
        return Math.max(0.0, shaft - combustion - starter);
    }
}
