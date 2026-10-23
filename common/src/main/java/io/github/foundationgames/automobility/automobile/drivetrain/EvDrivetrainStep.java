package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.Objects;

/** Fixed rigid reduction with separately measured motor work, regenerative extraction and passive losses. */
public final class EvDrivetrainStep {
    public enum Range { D, N, R, P }
    public enum RangeRequest { KEEP, D, N, R, P }
    public enum Refusal { NONE, PARK_REQUIRES_STOP_AND_BRAKE, PARK_EXIT_REQUIRES_BRAKE,
        REVERSE_REQUIRES_STOP, FORWARD_REQUIRES_STOP, DIRECTION_CHANGE_REQUIRES_BRAKE,
        DRIVE_SELECTION_REQUIRES_BRAKE }

    public record RangePolicy(double stoppedSpeedMetresPerSecond, double stoppedWheelRadiansPerSecond,
                              double minimumPhysicalBrakeFraction, boolean requireBrakeForDriveSelection,
                              boolean directionChangeRequiresBrake) {
        public RangePolicy(double stoppedSpeedMetresPerSecond, double stoppedWheelRadiansPerSecond,
                           double minimumPhysicalBrakeFraction, boolean requireBrakeForDriveSelection) {
            this(stoppedSpeedMetresPerSecond, stoppedWheelRadiansPerSecond, minimumPhysicalBrakeFraction, requireBrakeForDriveSelection, true);
        }
        public RangePolicy {
            Units.nonnegative(stoppedSpeedMetresPerSecond, "stopped speed");
            Units.nonnegative(stoppedWheelRadiansPerSecond, "stopped wheel omega");
            Units.between(minimumPhysicalBrakeFraction, 0.0, 1.0, "range brake threshold");
        }
        public static RangePolicy defaults() { return new RangePolicy(0.15, 0.75, 0.2, true); }
    }

    public record Parameters(EvMotorProfile motor, double reductionRatio, double axleInertiaKgMetresSquared,
                             double vehicleMassKg, double wheelRadiusMetres,
                             double parkingAxleHoldingTorqueNewtonMetres, RangePolicy rangePolicy) {
        public Parameters {
            Objects.requireNonNull(motor, "motor");
            Objects.requireNonNull(rangePolicy, "range policy");
            Units.positive(reductionRatio, "fixed reduction");
            Units.positive(axleInertiaKgMetresSquared, "axle inertia");
            Units.positive(vehicleMassKg, "mass");
            Units.positive(wheelRadiusMetres, "wheel radius");
            Units.nonnegative(parkingAxleHoldingTorqueNewtonMetres, "parking capacity");
            Units.positive(axleInertiaKgMetresSquared + motor.rotorInertiaKgMetresSquared() * reductionRatio * reductionRatio,
                    "combined rotor/axle inertia");
        }
        public double combinedAxleInertiaKgMetresSquared() {
            return axleInertiaKgMetresSquared + motor.rotorInertiaKgMetresSquared() * reductionRatio * reductionRatio;
        }
    }

    /** Rotor speed is derived, never independently stored or sign-flipped when a range is selected. */
    public record State(double wheelRadiansPerSecond, double speedMetresPerSecond, Range range) {
        public State {
            Units.finite(wheelRadiansPerSecond, "wheel omega");
            Units.finite(speedMetresPerSecond, "speed");
            Objects.requireNonNull(range, "range");
        }
        public State withRange(Range selected) { return new State(wheelRadiansPerSecond, speedMetresPerSecond, selected); }
        public double motorRadiansPerSecond(Parameters parameters) {
            return Units.finite(wheelRadiansPerSecond * parameters.reductionRatio(), "motor omega");
        }
        public double motorRpm(Parameters parameters) { return Units.radiansPerSecondToRpm(motorRadiansPerSecond(parameters)); }
    }

    /** Applied service brake is already decoded/blended by its owner; physical intent is independent metadata. */
    public record Control(RangeRequest requestedRange, double throttle,
                          double requestedRegenerationMotorTorqueNewtonMetres,
                          double authorizedDriveMotorTorqueLimitNewtonMetres,
                          double authorizedRegenerationMotorTorqueLimitNewtonMetres,
                          double serviceBrakeAxleTorqueNewtonMetres, double physicalBrakeFraction,
                          boolean physicalBrakeIntent, boolean motorPowerPermitted, boolean regenerationPermitted,
                          boolean coastRegenerationConfigured, boolean hazard) {
        public Control {
            Objects.requireNonNull(requestedRange, "range request");
            Units.between(throttle, 0.0, 1.0, "throttle");
            for (double value : new double[]{requestedRegenerationMotorTorqueNewtonMetres, authorizedDriveMotorTorqueLimitNewtonMetres,
                    authorizedRegenerationMotorTorqueLimitNewtonMetres, serviceBrakeAxleTorqueNewtonMetres}) Units.nonnegative(value, "torque limit");
            Units.between(physicalBrakeFraction, 0.0, 1.0, "physical brake");
        }
        public Control withoutRangeRequest() {
            return new Control(RangeRequest.KEEP, throttle, requestedRegenerationMotorTorqueNewtonMetres,
                    authorizedDriveMotorTorqueLimitNewtonMetres, authorizedRegenerationMotorTorqueLimitNewtonMetres,
                    serviceBrakeAxleTorqueNewtonMetres, physicalBrakeFraction, physicalBrakeIntent, motorPowerPermitted,
                    regenerationPermitted, coastRegenerationConfigured, hazard);
        }
    }

    public record Environment(boolean grounded, double gradeSine, double normalLoadNewtons, double tyreFrictionCoefficient,
                              double rollingResistanceNewtons, double externalLongitudinalForceNewtons,
                              double passiveMotorDragCapacityNewtonMetres) {
        public Environment {
            Units.between(gradeSine, -1.0, 1.0, "grade");
            for (double value : new double[]{normalLoadNewtons, tyreFrictionCoefficient, rollingResistanceNewtons,
                    passiveMotorDragCapacityNewtonMetres}) Units.nonnegative(value, "environment capacity");
            Units.finite(externalLongitudinalForceNewtons, "external force");
        }
    }
    public record Input(Control control, Environment environment) {
        public Input { Objects.requireNonNull(control, "control"); Objects.requireNonNull(environment, "environment"); }
    }

    /**
     * Regen work is nonpositive shaft work, not battery credit or tyre heat. RMS is for explicit current-squared costs.
     * Positive drive work is measured before signed absorption can cancel it. Friction impulses use axle-row signs/units.
     */
    public record Result(State state, Refusal refusal, double initialKineticEnergyJoules, double finalKineticEnergyJoules,
                         double driveShaftWorkJoules, double regenerationShaftWorkJoules, double longitudinalWorkJoules,
                         double positiveDriveShaftWorkJoules, double absorbedDriveShaftWorkJoules,
                         double serviceAndParkingBrakeEnergyJoules, double tyreSlipEnergyJoules,
                         double rollingResistanceEnergyJoules, double passiveMotorDragEnergyJoules,
                         double commandedDriveMotorTorqueNewtonMetres, double meanRegenerationMotorTorqueNewtonMetres,
                         double motorTorqueRmsNewtonMetres, double regenerationEnabledSeconds, boolean regenerationCutAtZero,
                         double serviceBrakeImpulseNewtonMetreSeconds, double tyreImpulseNewtonSeconds,
                         double rollingImpulseNewtonSeconds, double passiveDragImpulseNewtonMetreSeconds,
                         double regenerationImpulseNewtonMetreSeconds, int solverSegments,
                         double physicalBrakeFraction, boolean physicalBrakeIntent, boolean motorOverspeed) {
        public double motorShaftWorkJoules() { return driveShaftWorkJoules + regenerationShaftWorkJoules; }
        public double recoveredMechanicalJoules() { return -regenerationShaftWorkJoules; }
        public double passiveDissipationJoules() {
            return serviceAndParkingBrakeEnergyJoules + tyreSlipEnergyJoules + rollingResistanceEnergyJoules + passiveMotorDragEnergyJoules;
        }
        public double energyResidualJoules() {
            return initialKineticEnergyJoules + motorShaftWorkJoules() + longitudinalWorkJoules - finalKineticEnergyJoules - passiveDissipationJoules();
        }
    }
    private EvDrivetrainStep() { }

    public static double kineticEnergyJoules(Parameters parameters, State state) {
        return 0.5 * parameters.combinedAxleInertiaKgMetresSquared() * state.wheelRadiansPerSecond() * state.wheelRadiansPerSecond()
                + 0.5 * parameters.vehicleMassKg() * state.speedMetresPerSecond() * state.speedMetresPerSecond();
    }

    public static Result advance(Parameters parameters, State initial, Input input, double seconds) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(initial, "state");
        Objects.requireNonNull(input, "input");
        Units.positive(seconds, "EV seconds");
        if (seconds > DrivetrainStep.MAX_STEP_SECONDS) throw new IllegalArgumentException("subdivide EV steps longer than 0.05 seconds");
        Control control = input.control();
        Environment environment = input.environment();
        Refusal refusal = rangeRefusal(parameters.rangePolicy(), initial, control);
        Range selected = refusal == Refusal.NONE && control.requestedRange() != RangeRequest.KEEP
                ? Range.valueOf(control.requestedRange().name()) : initial.range();
        State sampled = initial.withRange(selected);
        double initialEnergy = Units.finite(kineticEnergyJoules(parameters, initial), "initial EV energy");
        double rpm = sampled.motorRpm(parameters);
        double ratio = parameters.reductionRatio();
        boolean driveRange = selected == Range.D || selected == Range.R;
        double driveTorque = driveRange && control.motorPowerPermitted()
                ? Math.min(parameters.motor().maximumMotoringTorque(rpm) * control.throttle(), control.authorizedDriveMotorTorqueLimitNewtonMetres())
                    * (selected == Range.R ? -1.0 : 1.0) : 0.0;
        boolean regenIntent = control.physicalBrakeIntent() || (control.coastRegenerationConfigured() && control.throttle() == 0.0);
        double regenTorque = driveRange && sampled.wheelRadiansPerSecond() != 0.0 && environment.grounded() && !control.hazard()
                && control.regenerationPermitted() && regenIntent
                ? Math.min(control.requestedRegenerationMotorTorqueNewtonMetres(), Math.min(parameters.motor().maximumRegenerationTorque(rpm),
                    control.authorizedRegenerationMotorTorqueLimitNewtonMetres())) : 0.0;
        // An explicitly authorized regen request wins. Service brake + drive remains available for burnouts when regen is absent.
        if (regenTorque > 0.0) driveTorque = 0.0;
        double grade = environment.grounded() ? -parameters.vehicleMassKg() * DrivetrainStep.GRAVITY_METRES_PER_SECOND_SQUARED * environment.gradeSine() : 0.0;
        double[] external = {0.0, Units.finite(ratio * driveTorque, "axle motor torque"),
                Units.finite(grade + environment.externalLongitudinalForceNewtons(), "longitudinal force")};
        double[] inverseMass = {1.0, 1.0 / parameters.combinedAxleInertiaKgMetresSquared(), 1.0 / parameters.vehicleMassKg()};
        for (double value : inverseMass) Units.positive(value, "EV inverse inertia/mass");
        double[][] rows = {{0, 1, 0}, {0, parameters.wheelRadiusMetres(), -1}, {0, 0, 1}, {0, 1, 0}, {0, 1, 0}};
        double[] capacities = {Math.max(control.serviceBrakeAxleTorqueNewtonMetres(), selected == Range.P ? parameters.parkingAxleHoldingTorqueNewtonMetres() : 0.0),
                environment.grounded() ? environment.tyreFrictionCoefficient() * environment.normalLoadNewtons() : 0.0,
                environment.grounded() ? environment.rollingResistanceNewtons() : 0.0,
                environment.passiveMotorDragCapacityNewtonMetres() * ratio, regenTorque * ratio};
        for (double capacity : capacities) Units.nonnegative(capacity, "EV capacity");
        double[] velocity = {0.0, sampled.wheelRadiansPerSecond(), sampled.speedMetresPerSecond()};
        double[] work = new double[3], positiveWork = new double[3], impulse = new double[5], removed = new double[5];
        double regenerationSeconds = 0.0, regenSquaredForceSeconds = 0.0;
        int segments = 0;
        boolean cut = false;
        double remaining = seconds;
        if (regenTorque > 0.0) {
            var first = CoulombSolver.integrateUntilAxisZero(velocity, inverseMass, external, rows, capacities, seconds, 1);
            accumulate(first.solution(), work, impulse, removed);
            accumulateWork(first.positiveExternalWork(), positiveWork);
            velocity = first.solution().velocity();
            segments += first.solution().segments();
            regenerationSeconds = first.elapsedSeconds();
            regenSquaredForceSeconds = first.forceSquareTimeIntegrals()[4];
            cut = first.axisZeroEvent();
            remaining = Math.max(0.0, seconds - first.elapsedSeconds());
            capacities[4] = 0.0;
        }
        if (remaining > 0.0) {
            var rest = CoulombSolver.integrateMeasured(velocity, inverseMass, external, rows, capacities, remaining);
            accumulate(rest.solution(), work, impulse, removed);
            accumulateWork(rest.positiveExternalWork(), positiveWork);
            velocity = rest.solution().velocity();
            segments += rest.solution().segments();
        }
        State state = new State(velocity[1], velocity[2], selected);
        double finalEnergy = Units.finite(kineticEnergyJoules(parameters, state), "final EV energy");
        double regenRms = Units.finite(Math.sqrt(regenSquaredForceSeconds / seconds) / ratio, "regen RMS torque");
        double totalRms = Units.finite(Math.hypot(driveTorque, regenRms), "motor RMS torque");
        Result result = new Result(state, refusal, initialEnergy, finalEnergy, work[1], -removed[4], work[2],
                positiveWork[1], Math.max(0.0, positiveWork[1] - work[1]),
                removed[0], removed[1], removed[2], removed[3], driveTorque, -impulse[4] / ratio / seconds,
                totalRms, regenerationSeconds, cut, impulse[0], impulse[1], impulse[2], impulse[3], impulse[4], segments,
                control.physicalBrakeFraction(), control.physicalBrakeIntent(), Math.abs(state.motorRpm(parameters)) >= parameters.motor().maximumRpm());
        double scale = Math.max(1.0, initialEnergy + Math.abs(result.driveShaftWorkJoules()) + Math.abs(result.longitudinalWorkJoules()));
        if (!Double.isFinite(result.energyResidualJoules()) || Math.abs(result.energyResidualJoules()) > 1.0e-9 * scale) {
            throw new IllegalArgumentException("EV energy ledger failed");
        }
        return result;
    }

    private static void accumulate(CoulombSolver.Solution source, double[] work, double[] impulse, double[] removed) {
        for (int i = 0; i < work.length; i++) work[i] = Units.finite(work[i] + source.work()[i], "EV work");
        for (int i = 0; i < impulse.length; i++) {
            impulse[i] = Units.finite(impulse[i] + source.impulse()[i], "EV impulse");
            removed[i] = Units.finite(removed[i] + source.dissipation()[i], "EV removed energy");
        }
    }

    private static void accumulateWork(double[] source, double[] work) {
        for (int i = 0; i < work.length; i++) work[i] = Units.finite(work[i] + source[i], "EV positive work");
    }

    private static Refusal rangeRefusal(RangePolicy policy, State state, Control input) {
        if (input.requestedRange() == RangeRequest.KEEP) return Refusal.NONE;
        Range target = Range.valueOf(input.requestedRange().name());
        if (target == state.range()) return Refusal.NONE;
        boolean stopped = Math.abs(state.speedMetresPerSecond()) <= policy.stoppedSpeedMetresPerSecond()
                && Math.abs(state.wheelRadiansPerSecond()) <= policy.stoppedWheelRadiansPerSecond();
        boolean brake = input.physicalBrakeIntent() && input.physicalBrakeFraction() >= policy.minimumPhysicalBrakeFraction();
        if (target == Range.P) return stopped && brake ? Refusal.NONE : Refusal.PARK_REQUIRES_STOP_AND_BRAKE;
        if (state.range() == Range.P && !brake) return Refusal.PARK_EXIT_REQUIRES_BRAKE;
        if (target == Range.N) return Refusal.NONE;
        if (target == Range.R && (state.speedMetresPerSecond() > policy.stoppedSpeedMetresPerSecond()
                || state.wheelRadiansPerSecond() > policy.stoppedWheelRadiansPerSecond()
                || (state.range() == Range.D && !stopped))) return Refusal.REVERSE_REQUIRES_STOP;
        if (target == Range.D && (state.speedMetresPerSecond() < -policy.stoppedSpeedMetresPerSecond()
                || state.wheelRadiansPerSecond() < -policy.stoppedWheelRadiansPerSecond()
                || (state.range() == Range.R && !stopped))) return Refusal.FORWARD_REQUIRES_STOP;
        if (policy.directionChangeRequiresBrake() && (state.range() == Range.D && target == Range.R || state.range() == Range.R && target == Range.D) && !brake) {
            return Refusal.DIRECTION_CHANGE_REQUIRES_BRAKE;
        }
        return policy.requireBrakeForDriveSelection() && !brake ? Refusal.DRIVE_SELECTION_REQUIRES_BRAKE : Refusal.NONE;
    }
}
