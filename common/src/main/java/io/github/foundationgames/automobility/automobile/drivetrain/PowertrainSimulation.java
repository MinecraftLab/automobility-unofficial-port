package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.Objects;

/** Ordered controller/mechanics integration. All input and energy permissions must come from the server adapter. */
public final class PowertrainSimulation {
    public enum ControlMode { AUTOMATIC, MANUAL_ASSISTED, MANUAL_CLUTCH }
    public enum RangeRequest { KEEP, DRIVE, NEUTRAL, REVERSE, PARK }
    public enum Refusal { NONE, MODE_CHANGE_REQUIRES_STOP, MODE_CHANGE_REQUIRES_NEUTRAL_OR_CLUTCH,
        PARK_REQUIRES_STOP_AND_BRAKE, PARK_EXIT_REQUIRES_BRAKE, DIRECTION_OR_RPM_GUARD }

    public record Setup(EngineProfile engine, DrivetrainParameters mechanics,
                        EngineController.Config engineControl, ClutchController.Config clutch,
                        AutomaticShiftController.Config automatic, ManualShiftController.Config manual,
                        ThrottleController.Config throttle, LaunchController.Config launch,
                        double maximumControllerStepSeconds, double parkingHoldingTorqueNewtonMetres,
                        double manualClutchAssistanceStrength) {
        public Setup(EngineProfile engine, DrivetrainParameters mechanics, EngineController.Config engineControl,
                     ClutchController.Config clutch, AutomaticShiftController.Config automatic, ManualShiftController.Config manual,
                     ThrottleController.Config throttle, LaunchController.Config launch,
                     double maximumControllerStepSeconds, double parkingHoldingTorqueNewtonMetres) {
            this(engine, mechanics, engineControl, clutch, automatic, manual, throttle, launch,
                    maximumControllerStepSeconds, parkingHoldingTorqueNewtonMetres, manual.clutchAssist() ? 1.0 : 0.0);
        }
        public Setup {
            Objects.requireNonNull(engine); Objects.requireNonNull(mechanics); Objects.requireNonNull(engineControl);
            Objects.requireNonNull(clutch); Objects.requireNonNull(automatic); Objects.requireNonNull(manual);
            Objects.requireNonNull(throttle); Objects.requireNonNull(launch);
            Units.positive(maximumControllerStepSeconds, "controller step");
            if (maximumControllerStepSeconds < 0.001 || maximumControllerStepSeconds > 0.01) {
                throw new IllegalArgumentException("controller step must be 1..10 milliseconds");
            }
            Units.nonnegative(parkingHoldingTorqueNewtonMetres, "parking holding torque");
            Units.between(manualClutchAssistanceStrength, 0.0, 1.0, "manual clutch assistance");
            if (mechanics.engineInertiaKgMetresSquared() != engine.inertiaKgMetresSquared()) {
                throw new IllegalArgumentException("physics and engine profile must share inertia");
            }
        }
        public Setup withManualClutchAssistance(double strength) {
            return new Setup(engine, mechanics, engineControl, clutch, automatic, manual, throttle, launch,
                    maximumControllerStepSeconds, parkingHoldingTorqueNewtonMetres, strength);
        }
    }

    public record State(DrivetrainState mechanics, EngineController.State engine,
                        ClutchController.State pedal, ThrottleController.State throttle,
                        LaunchController.State launch, AutomaticShiftController.State automatic,
                        ManualShiftController.State manual, ControlMode mode, boolean rangeShiftActive,
                        boolean parkingLocked, double lastTransmittedCrankTorqueNewtonMetres) {
        public State {
            Objects.requireNonNull(mechanics); Objects.requireNonNull(engine); Objects.requireNonNull(pedal);
            Objects.requireNonNull(throttle); Objects.requireNonNull(launch); Objects.requireNonNull(automatic);
            Objects.requireNonNull(manual); Objects.requireNonNull(mode);
            Units.finite(lastTransmittedCrankTorqueNewtonMetres, "transmitted crank torque");
            if (mechanics.gear() != automatic.currentGear() || mechanics.gear() != manual.currentGear()) {
                throw new IllegalArgumentException("controllers must observe the same committed mechanical ratio");
            }
            if (rangeShiftActive && mode != ControlMode.AUTOMATIC) throw new IllegalArgumentException("range override is automatic-only");
            if (parkingLocked && mechanics.gear() != 0) throw new IllegalArgumentException("park requires neutral coupling");
        }
        public static State stopped(ControlMode mode) {
            return new State(new DrivetrainState(0.0, 0.0, 0.0, 0), EngineController.State.off(),
                    new ClutchController.State(0.0), new ThrottleController.State(0.0), LaunchController.State.stopped(),
                    AutomaticShiftController.State.drive(0, AutomaticShiftController.Mode.BALANCED),
                    ManualShiftController.State.drive(0), mode, false, false, 0.0);
        }
    }

    public record DriverInput(ControlMode requestedMode, AutomaticShiftController.Mode drivingMode,
                              ThrottleController.Input throttle, ClutchController.Input clutch,
                              ManualShiftController.Request shift, RangeRequest range,
                              double physicalBrake, double appliedServiceBrakeFraction, boolean ignitionOn, boolean startHeld,
                              boolean controlsPresent, boolean starterEnergyPermitted, boolean combustionEnergyPermitted) {
        public DriverInput(ControlMode requestedMode, AutomaticShiftController.Mode drivingMode,
                           ThrottleController.Input throttle, ClutchController.Input clutch,
                           ManualShiftController.Request shift, RangeRequest range,
                           double physicalBrake, boolean ignitionOn, boolean startHeld,
                           boolean controlsPresent, boolean starterEnergyPermitted, boolean combustionEnergyPermitted) {
            this(requestedMode, drivingMode, throttle, clutch, shift, range, physicalBrake, physicalBrake,
                    ignitionOn, startHeld, controlsPresent, starterEnergyPermitted, combustionEnergyPermitted);
        }
        public DriverInput {
            Objects.requireNonNull(requestedMode); Objects.requireNonNull(drivingMode); Objects.requireNonNull(throttle);
            Objects.requireNonNull(clutch); Objects.requireNonNull(shift); Objects.requireNonNull(range);
            Units.between(physicalBrake, 0.0, 1.0, "physical brake");
            Units.between(appliedServiceBrakeFraction, 0.0, 1.0, "applied service brake");
        }
    }

    public record Environment(boolean grounded, double gradeSine, double normalLoadNewtons,
                              double tyreFrictionCoefficient, double rollingResistanceNewtons,
                              double externalLongitudinalForceNewtons, double serviceBrakeCapacityNewtonMetres) {
        public Environment {
            Units.between(gradeSine, -1.0, 1.0, "grade");
            Units.nonnegative(normalLoadNewtons, "normal load");
            Units.nonnegative(tyreFrictionCoefficient, "tyre friction");
            Units.nonnegative(rollingResistanceNewtons, "rolling resistance");
            Units.finite(externalLongitudinalForceNewtons, "external force");
            Units.nonnegative(serviceBrakeCapacityNewtonMetres, "brake capacity");
        }
    }

    public record Energy(double initialKineticJoules, double finalKineticJoules,
                         double combustionWorkJoules, double starterWorkJoules, double longitudinalWorkJoules,
                         double clutchSlipJoules, double serviceBrakeJoules, double tyreSlipJoules,
                         double rollingResistanceJoules, double engineDragJoules) {
        public double dissipatedJoules() { return clutchSlipJoules + serviceBrakeJoules + tyreSlipJoules + rollingResistanceJoules + engineDragJoules; }
        public double residualJoules() { return initialKineticJoules + combustionWorkJoules + starterWorkJoules + longitudinalWorkJoules - finalKineticJoules - dissipatedJoules(); }
    }
    /** Time-weighted paid actuator requests, including zero-speed starter torque; no storage balance. */
    public record Activity(double meanEngineRpm, double meanCombustionTorqueNewtonMetres,
                           double meanStarterTorqueNewtonMetres, double meanCombustionLoadFraction,
                           double runningSeconds, double startingSeconds) { }
    public record Result(State state, Energy energy, Refusal refusal, int mechanicalSubsteps,
                         double appliedClutchEngagement, double appliedThrottle, double physicalBrake, Activity activity) { }

    private PowertrainSimulation() { }

    public static Result advance(Setup setup, State initial, DriverInput driver, Environment environment, double seconds) {
        Objects.requireNonNull(setup); Objects.requireNonNull(initial); Objects.requireNonNull(driver); Objects.requireNonNull(environment);
        Units.positive(seconds, "simulation seconds");
        if (seconds > DrivetrainStep.MAX_STEP_SECONDS) throw new IllegalArgumentException("outer simulation step must be at most 0.05 seconds");
        DrivetrainParameters parameters = setup.mechanics();
        TransmissionProfile transmission = parameters.transmission();
        transmission.totalRatio(initial.mechanics().gear());
        DrivetrainState mechanical = initial.mechanics();
        EngineController.State engine = initial.engine();
        ClutchController.State pedal = initial.pedal();
        ThrottleController.State throttle = initial.throttle();
        LaunchController.State launch = initial.launch();
        AutomaticShiftController.State automatic = initial.automatic();
        ManualShiftController.State manual = initial.manual();
        ControlMode mode = initial.mode();
        boolean rangeActive = initial.rangeShiftActive();
        boolean parking = initial.parkingLocked();
        double transmitted = initial.lastTransmittedCrankTorqueNewtonMetres();
        Refusal refusal = Refusal.NONE;
        if (driver.controlsPresent() && driver.requestedMode() != mode) {
            if (Math.abs(mechanical.speedMetresPerSecond()) > 0.1 || automatic.phase() != AutomaticShiftController.Phase.DRIVE
                    || manual.phase() != ManualShiftController.Phase.DRIVE || driver.throttle().value() > 0.01) {
                refusal = Refusal.MODE_CHANGE_REQUIRES_STOP;
            } else if (mechanical.gear() != 0 && driver.clutch().pedalDepression() < 0.95) {
                refusal = Refusal.MODE_CHANGE_REQUIRES_NEUTRAL_OR_CLUTCH;
            } else {
                mode = driver.requestedMode(); rangeActive = false;
                automatic = AutomaticShiftController.State.drive(mechanical.gear(), driver.drivingMode());
                manual = ManualShiftController.State.drive(mechanical.gear());
            }
        }
        double clutchAssistance = mode == ControlMode.AUTOMATIC ? 1.0
                : mode == ControlMode.MANUAL_ASSISTED ? setup.manualClutchAssistanceStrength() : 0.0;
        ManualShiftController.Config manualConfig = setup.manual().withAssistance(clutchAssistance > 0.0,
                setup.manual().revMatchAssist());
        ThrottleController.Input throttleInput = driver.controlsPresent() ? driver.throttle() : ThrottleController.Input.digital(false);
        if (!driver.controlsPresent()) throttle = new ThrottleController.State(0.0);
        ClutchController.Input clutchInput = driver.controlsPresent() ? driver.clutch() : ClutchController.Input.digital(true);
        ManualShiftController.Request shiftRequest = driver.controlsPresent() ? driver.shift() : ManualShiftController.Request.NONE;
        RangeRequest rangeRequest = driver.controlsPresent() ? driver.range() : RangeRequest.KEEP;
        double[] totals = new double[8];
        double[] activity = new double[6];
        double initialEnergy = DrivetrainStep.kineticEnergyJoules(parameters, mechanical);
        double remaining = seconds;
        int steps = 0;
        double appliedClutch = 0.0, appliedThrottle = throttle.demand();

        while (remaining > 0.0) {
            if (++steps > 128) throw new IllegalArgumentException("controller event budget exceeded");
            throttle = ThrottleController.advance(setup.throttle(), throttle, throttleInput, 0.0);
            pedal = ClutchController.advance(setup.clutch(), pedal, clutchInput, 0.0);
            var engineInput = engineInput(mechanical, driver, throttle.demand());
            engine = EngineController.advance(setup.engineControl(), setup.engine(), engine, engineInput, 0.0);
            var engineControls = EngineController.controls(setup.engineControl(), setup.engine(), engine, engineInput);
            double physicalPedal = mode == ControlMode.AUTOMATIC ? 1.0 : pedal.engagement();
            var measuredManual = manualInput(mechanical, physicalPedal, ManualShiftController.Request.NONE);

            if (rangeRequest != RangeRequest.KEEP) {
                if (parking && rangeRequest != RangeRequest.PARK && driver.physicalBrake() < 0.2) refusal = Refusal.PARK_EXIT_REQUIRES_BRAKE;
                else if (rangeRequest == RangeRequest.PARK) {
                    if (Math.abs(mechanical.speedMetresPerSecond()) > 0.15 || Math.abs(mechanical.wheelRadiansPerSecond()) > 0.75 || driver.physicalBrake() < 0.2) {
                        refusal = Refusal.PARK_REQUIRES_STOP_AND_BRAKE;
                    } else {
                        parking = true; rangeActive = false; mechanical = mechanical.withGear(0);
                        manual = ManualShiftController.State.drive(0);
                        automatic = AutomaticShiftController.State.drive(0, driver.drivingMode());
                    }
                } else {
                    int target = switch (rangeRequest) {
                        case NEUTRAL -> 0;
                        case REVERSE -> -1;
                        case DRIVE -> chooseForwardRange(setup, mechanical);
                        case KEEP, PARK -> throw new IllegalStateException();
                    };
                    var attempted = ManualShiftController.requestGear(manualConfig, setup.engine(), transmission,
                            ManualShiftController.State.drive(mechanical.gear()), measuredManual, target);
                    if (attempted.reason() == ManualShiftController.Reason.NONE) {
                        manual = attempted;
                        parking = false; rangeActive = mode == ControlMode.AUTOMATIC && manual.phase() != ManualShiftController.Phase.DRIVE;
                        automatic = AutomaticShiftController.State.drive(mechanical.gear(), driver.drivingMode());
                    } else refusal = Refusal.DIRECTION_OR_RPM_GUARD;
                }
                rangeRequest = RangeRequest.KEEP;
                shiftRequest = ManualShiftController.Request.NONE;
            }

            boolean useManual = mode != ControlMode.AUTOMATIC || rangeActive;
            if (useManual) {
                manual = ManualShiftController.advance(manualConfig, setup.engine(), transmission, manual,
                        manualInput(mechanical, physicalPedal, shiftRequest), 0.0);
            } else {
                automatic = AutomaticShiftController.advance(setup.automatic(), setup.engine(), transmission, automatic,
                        automaticInput(mechanical, driver.drivingMode(), throttle.demand()), 0.0);
                manual = ManualShiftController.State.drive(mechanical.gear());
            }
            shiftRequest = ManualShiftController.Request.NONE;
            double boundary = EngineController.secondsToBoundary(setup.engineControl(), setup.engine(), engine, engineInput);
            boundary = Math.min(boundary, useManual ? ManualShiftController.secondsToBoundary(manualConfig, manual)
                    : AutomaticShiftController.secondsToBoundary(automatic));
            if (launch.hillHoldRemainingSeconds() > 0.0 && (!driver.controlsPresent() || driver.physicalBrake() <= 0.1)) {
                boundary = Math.min(boundary, launch.hillHoldRemainingSeconds());
            }
            double duration = Math.min(remaining, setup.maximumControllerStepSeconds());
            if (Double.isFinite(boundary)) duration = Math.min(duration, Math.max(boundary, Math.ulp(remaining)));
            if (!(duration > 0.0) || remaining - duration == remaining) throw new IllegalArgumentException("controller event cannot advance time");
            var middleThrottle = ThrottleController.advance(setup.throttle(), throttle, throttleInput, duration * 0.5);
            var middlePedal = ClutchController.advance(setup.clutch(), pedal, clutchInput, duration * 0.5);
            double torqueMultiplier, shiftCeiling, revMatch;
            if (useManual) {
                var midState = ManualShiftController.advance(manualConfig, setup.engine(), transmission, manual, measuredManual.withoutRequest(), duration * 0.5);
                var commands = ManualShiftController.controls(manualConfig, setup.engine(), transmission, midState, measuredManual.withoutRequest());
                torqueMultiplier = 1.0 - clutchAssistance * (1.0 - commands.torqueMultiplier());
                shiftCeiling = 1.0 - clutchAssistance * (1.0 - commands.clutchEngagementCeiling());
                revMatch = commands.requestedRevMatchThrottle();
            } else {
                // A sampled DRIVE decision belongs to the next real boundary, not the midpoint preview.
                var midState = automatic.phase() == AutomaticShiftController.Phase.DRIVE ? automatic
                        : AutomaticShiftController.advance(setup.automatic(), setup.engine(), transmission, automatic,
                        automaticInput(mechanical, driver.drivingMode(), middleThrottle.demand()), duration * 0.5);
                var commands = AutomaticShiftController.controls(setup.automatic(), midState);
                torqueMultiplier = commands.torqueMultiplier(); shiftCeiling = commands.clutchEngagement(); revMatch = 0.0;
            }
            appliedThrottle = Math.max(middleThrottle.demand() * torqueMultiplier, revMatch);
            var activeEngineInput = engineInput(mechanical, driver, appliedThrottle);
            engineControls = EngineController.controls(setup.engineControl(), setup.engine(), engine, activeEngineInput);
            var launchInput = launchInput(setup, mechanical, environment, driver, engine, engineControls, middleThrottle.demand(), transmitted);
            var middleLaunch = LaunchController.advance(setup.launch(), setup.engine(), parameters, launch, launchInput, duration * 0.5);
            var launchCommands = LaunchController.controls(parameters, middleLaunch, launchInput);
            double antiStallCeiling = 1.0 - clutchAssistance * (1.0 - engineControls.clutchEngagementCeiling());
            double launchCeiling = 1.0 - clutchAssistance * (1.0 - launchCommands.clutchEngagementCeiling());
            appliedClutch = Math.min(mode == ControlMode.AUTOMATIC ? 1.0 : middlePedal.engagement(), Math.min(shiftCeiling, antiStallCeiling));
            if (mode != ControlMode.MANUAL_CLUTCH) appliedClutch = Math.min(appliedClutch, launchCeiling);
            if (!driver.controlsPresent() || parking) appliedClutch = 0.0;
            double brakeTorque = Math.max(driver.controlsPresent() ? driver.appliedServiceBrakeFraction() : 0.0,
                    launchCommands.assistedBrakeFraction()) * environment.serviceBrakeCapacityNewtonMetres();
            if (parking) brakeTorque = Math.max(brakeTorque, setup.parkingHoldingTorqueNewtonMetres());
            var mechanicsInput = new DrivetrainInput(engineControls.appliedActiveCrankTorqueNewtonMetres(), appliedClutch,
                    brakeTorque, environment.rollingResistanceNewtons(), environment.grounded(), environment.gradeSine(),
                    environment.normalLoadNewtons(), environment.tyreFrictionCoefficient(), environment.externalLongitudinalForceNewtons(),
                    engineControls.passiveDragCapacityNewtonMetres());
            var mechanicalResult = DrivetrainStep.advance(parameters, mechanical, mechanicsInput, duration);
            activity[0] += 0.5 * (Math.abs(Units.radiansPerSecondToRpm(mechanical.engineRadiansPerSecond()))
                    + Math.abs(Units.radiansPerSecondToRpm(mechanicalResult.state().engineRadiansPerSecond()))) * duration;
            activity[1] += engineControls.permittedCombustionTorqueNewtonMetres() * duration;
            activity[2] += engineControls.permittedStarterTorqueNewtonMetres() * duration;
            double availableCombustion = setup.engine().torqueCurve().torqueAtRpm(Math.max(0.0,
                    Units.radiansPerSecondToRpm(mechanical.engineRadiansPerSecond())));
            activity[3] += (availableCombustion > 0.0 ? engineControls.permittedCombustionTorqueNewtonMetres() / availableCombustion : 0.0) * duration;
            if (engine.phase() == EngineController.Phase.RUNNING) activity[4] += duration;
            if (engine.phase() == EngineController.Phase.STARTING) activity[5] += duration;
            double active = engineControls.appliedActiveCrankTorqueNewtonMetres();
            if (active > 0.0) {
                totals[0] += mechanicalResult.crankWorkJoules() * engineControls.permittedCombustionTorqueNewtonMetres() / active;
                totals[1] += mechanicalResult.crankWorkJoules() * engineControls.permittedStarterTorqueNewtonMetres() / active;
            }
            totals[2] += mechanicalResult.longitudinalWorkJoules(); totals[3] += mechanicalResult.clutchSlipEnergyJoules();
            totals[4] += mechanicalResult.serviceBrakeEnergyJoules(); totals[5] += mechanicalResult.tyreSlipEnergyJoules();
            totals[6] += mechanicalResult.rollingResistanceEnergyJoules(); totals[7] += mechanicalResult.engineDragEnergyJoules();
            transmitted = mechanicalResult.clutchImpulseNewtonMetreSeconds() / duration;
            throttle = ThrottleController.advance(setup.throttle(), throttle, throttleInput, duration);
            pedal = ClutchController.advance(setup.clutch(), pedal, clutchInput, duration);
            launch = LaunchController.advance(setup.launch(), setup.engine(), parameters, launch, launchInput, duration);
            engine = EngineController.advance(setup.engineControl(), setup.engine(), engine, activeEngineInput, duration);
            mechanical = mechanicalResult.state();
            var measuredEngineInput = engineInput(mechanical, driver, throttle.demand());
            engine = EngineController.advance(setup.engineControl(), setup.engine(), engine, measuredEngineInput, 0.0);
            if (useManual) {
                double endCeiling = ManualShiftController.controls(manualConfig, setup.engine(), transmission, manual,
                        manualInput(mechanical, pedal.engagement(), ManualShiftController.Request.NONE)).clutchEngagementCeiling();
                endCeiling = 1.0 - clutchAssistance * (1.0 - endCeiling);
                double endClutch = Math.min(mode == ControlMode.AUTOMATIC ? 1.0 : pedal.engagement(), endCeiling);
                manual = ManualShiftController.advance(manualConfig, setup.engine(), transmission, manual,
                        manualInput(mechanical, endClutch, ManualShiftController.Request.NONE), duration);
                mechanical = mechanical.withGear(manual.currentGear());
                automatic = AutomaticShiftController.State.drive(mechanical.gear(), driver.drivingMode());
                if (rangeActive && manual.phase() == ManualShiftController.Phase.DRIVE) rangeActive = false;
            } else {
                if (automatic.phase() == AutomaticShiftController.Phase.DRIVE) {
                    // Decisions use the actual boundary sample, never new RPM applied retrospectively to elapsed time.
                    automatic = new AutomaticShiftController.State(automatic.currentGear(), automatic.currentGear(),
                            AutomaticShiftController.Phase.DRIVE, 0.0,
                            Math.max(0.0, automatic.dwellRemainingSeconds() - duration), automatic.latchedMode());
                } else {
                    automatic = AutomaticShiftController.advance(setup.automatic(), setup.engine(), transmission, automatic,
                            automaticInput(mechanical, driver.drivingMode(), throttle.demand()), duration);
                }
                automatic = AutomaticShiftController.advance(setup.automatic(), setup.engine(), transmission, automatic,
                        automaticInput(mechanical, driver.drivingMode(), throttle.demand()), 0.0);
                mechanical = mechanical.withGear(automatic.currentGear());
                manual = ManualShiftController.State.drive(mechanical.gear());
            }
            remaining -= duration;
        }
        State state = new State(mechanical, engine, pedal, throttle, launch, automatic, manual, mode, rangeActive, parking, transmitted);
        Energy energy = new Energy(initialEnergy, DrivetrainStep.kineticEnergyJoules(parameters, mechanical),
                totals[0], totals[1], totals[2], totals[3], totals[4], totals[5], totals[6], totals[7]);
        double scale = Math.max(1.0, initialEnergy + Math.abs(totals[0]) + Math.abs(totals[1]) + Math.abs(totals[2]));
        if (!Double.isFinite(energy.residualJoules()) || Math.abs(energy.residualJoules()) > 1.0e-8 * scale) throw new IllegalArgumentException("combined energy ledger failed");
        return new Result(state, energy, refusal, steps, appliedClutch, appliedThrottle, driver.controlsPresent() ? driver.physicalBrake() : 0.0,
                new Activity(activity[0] / seconds, activity[1] / seconds, activity[2] / seconds,
                        Units.clamp(activity[3] / seconds, 0.0, 1.0), activity[4], activity[5]));
    }

    private static EngineController.Input engineInput(DrivetrainState mechanics, DriverInput driver, double throttle) {
        return new EngineController.Input(mechanics.engineRadiansPerSecond(), driver.ignitionOn(),
                driver.controlsPresent() && driver.startHeld(), throttle, driver.starterEnergyPermitted(), driver.combustionEnergyPermitted());
    }
    private static ManualShiftController.Input manualInput(DrivetrainState state, double clutch, ManualShiftController.Request request) {
        return new ManualShiftController.Input(state.engineRadiansPerSecond(), state.wheelRadiansPerSecond(), state.speedMetresPerSecond(), clutch, request);
    }
    private static AutomaticShiftController.Input automaticInput(DrivetrainState state, AutomaticShiftController.Mode mode, double throttle) {
        return new AutomaticShiftController.Input(Math.max(0.0, Units.radiansPerSecondToRpm(state.engineRadiansPerSecond())), state.wheelRadiansPerSecond(), throttle, throttle, mode);
    }
    private static LaunchController.Input launchInput(Setup setup, DrivetrainState state, Environment env, DriverInput driver,
                                                      EngineController.State engine, EngineController.Controls controls, double throttle, double transmitted) {
        return new LaunchController.Input(engine.phase() == EngineController.Phase.RUNNING, env.grounded(),
                state.engineRadiansPerSecond(), state.wheelRadiansPerSecond(), state.speedMetresPerSecond(), throttle,
                driver.controlsPresent() ? driver.physicalBrake() : 0.0, setup.mechanics().transmission().totalRatio(state.gear()),
                controls.appliedActiveCrankTorqueNewtonMetres(), controls.passiveDragCapacityNewtonMetres(), transmitted,
                env.gradeSine(), env.grounded() ? env.normalLoadNewtons() * env.tyreFrictionCoefficient() : 0.0, env.serviceBrakeCapacityNewtonMetres());
    }
    private static int chooseForwardRange(Setup setup, DrivetrainState state) {
        double desired = setup.engine().idleRpm() + 0.3 * (setup.engine().redlineRpm() - setup.engine().idleRpm());
        int best = 1;
        double bestError = Double.POSITIVE_INFINITY;
        for (int gear = 1; gear <= setup.mechanics().transmission().forwardRatios().size(); gear++) {
            double rpm = Math.abs(Units.radiansPerSecondToRpm(state.wheelRadiansPerSecond() * setup.mechanics().transmission().totalRatio(gear)));
            if (rpm >= setup.engine().redlineRpm() * setup.manual().overRevFraction()) continue;
            double error = Math.abs(rpm - desired);
            if (error < bestError) { bestError = error; best = gear; }
        }
        return best;
    }
}
