package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.Objects;

/** Forward-gear policy and timed control phases; never changes mechanical velocities or energy stores. */
public final class AutomaticShiftController {
    public enum Mode { TOURING, BALANCED, SPORT }
    public enum Phase { DRIVE, TORQUE_CUT, RATIO_SWITCH, REENGAGE }

    /** Thresholds are fractions of the engine's idle-to-redline working range, not vehicle speeds. */
    public record ModeMap(double upshiftLowDemand, double upshiftHighDemand,
                          double downshiftLowDemand, double downshiftHighDemand,
                          double kickdownThrottle, double kickdownTarget) {
        public ModeMap {
            for (double value : new double[]{upshiftLowDemand, upshiftHighDemand, downshiftLowDemand,
                    downshiftHighDemand, kickdownThrottle, kickdownTarget}) {
                Units.between(value, 0.0, 1.0, "mode fraction");
            }
            if (downshiftLowDemand > downshiftHighDemand || upshiftLowDemand > upshiftHighDemand
                    || downshiftHighDemand >= upshiftLowDemand || kickdownThrottle == 0.0) {
                throw new IllegalArgumentException("mode thresholds require an ordered up/down dead band");
            }
        }
    }

    public record Config(double torqueCutSeconds, double ratioSwitchSeconds, double reengageSeconds,
                         double minimumDwellSeconds, double modeChangeDwellSeconds, double hysteresisRpm,
                         ModeMap touring, ModeMap balanced, ModeMap sport) {
        public Config {
            Units.positive(torqueCutSeconds, "torque cut seconds");
            Units.positive(ratioSwitchSeconds, "ratio switch seconds");
            Units.positive(reengageSeconds, "reengage seconds");
            Units.positive(minimumDwellSeconds, "minimum gear dwell seconds");
            Units.positive(modeChangeDwellSeconds, "mode change dwell seconds");
            Units.positive(hysteresisRpm, "RPM hysteresis");
            Objects.requireNonNull(touring, "touring map");
            Objects.requireNonNull(balanced, "balanced map");
            Objects.requireNonNull(sport, "sport map");
        }

        /** Prototype calibration, not a final car-specific handling promise. */
        public static Config defaults() {
            return new Config(0.08, 0.04, 0.18, 0.60, 0.50, 150.0,
                    new ModeMap(0.34, 0.74, 0.14, 0.29, 0.86, 0.60),
                    new ModeMap(0.45, 0.86, 0.16, 0.40, 0.80, 0.72),
                    new ModeMap(0.62, 0.96, 0.22, 0.52, 0.72, 0.85));
        }

        public ModeMap map(Mode mode) {
            return switch (mode) { case TOURING -> touring; case BALANCED -> balanced; case SPORT -> sport; };
        }
    }

    /**
     * currentGear changes only at entry to RATIO_SWITCH, while the clutch and requested torque are zero.
     * latchedMode remains unchanged through an in-flight shift. N/R selection belongs to a separate owner.
     */
    public record State(int currentGear, int targetGear, Phase phase, double phaseRemainingSeconds,
                        double dwellRemainingSeconds, Mode latchedMode) {
        public State {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(latchedMode, "latched mode");
            Units.nonnegative(phaseRemainingSeconds, "phase seconds");
            Units.nonnegative(dwellRemainingSeconds, "dwell seconds");
            if (currentGear < -1 || targetGear < -1) throw new IllegalArgumentException("invalid gear");
            if (phase == Phase.DRIVE && (currentGear != targetGear || phaseRemainingSeconds != 0.0)) {
                throw new IllegalArgumentException("DRIVE requires one settled gear and no phase timer");
            }
            if (phase != Phase.DRIVE && (currentGear < 1 || targetGear < 1)) {
                throw new IllegalArgumentException("automatic shifts are forward-only");
            }
            if (phase != Phase.DRIVE && dwellRemainingSeconds != 0.0) {
                throw new IllegalArgumentException("gear dwell belongs only to settled DRIVE");
            }
            if (phase == Phase.TORQUE_CUT && currentGear == targetGear) {
                throw new IllegalArgumentException("torque cut requires a pending ratio change");
            }
            if ((phase == Phase.RATIO_SWITCH || phase == Phase.REENGAGE) && currentGear != targetGear) {
                throw new IllegalArgumentException("ratio must already be selected after torque cut");
            }
        }

        public static State drive(int gear, Mode mode) { return new State(gear, gear, Phase.DRIVE, 0.0, 0.0, mode); }
    }

    /** Wheel omega is measured at the driven wheels, not inferred from vehicle speed or slipping engine RPM. */
    public record Input(double engineRpm, double drivenWheelRadiansPerSecond, double throttle,
                        double load, Mode mode) {
        public Input {
            Units.nonnegative(engineRpm, "engine RPM");
            Units.finite(drivenWheelRadiansPerSecond, "driven wheel omega");
            Units.between(throttle, 0.0, 1.0, "throttle");
            Units.between(load, 0.0, 1.0, "load");
            Objects.requireNonNull(mode, "mode");
        }
    }

    /** Command multipliers, not direct engine, clutch, vehicle or energy mutations. */
    public record Controls(double torqueMultiplier, double clutchEngagement) { }

    private AutomaticShiftController() { }

    public static double secondsToBoundary(State state) {
        Objects.requireNonNull(state, "state");
        return state.phase() == Phase.DRIVE
                ? state.dwellRemainingSeconds() > 0.0 ? state.dwellRemainingSeconds() : Double.POSITIVE_INFINITY
                : state.phaseRemainingSeconds();
    }

    public static double predictedRpm(TransmissionProfile transmission, Input input, int targetGear) {
        return Math.abs(Units.radiansPerSecondToRpm(input.drivenWheelRadiansPerSecond() * transmission.totalRatio(targetGear)));
    }

    public static Controls controls(Config config, State state) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(state, "state");
        return switch (state.phase()) {
            case DRIVE -> new Controls(1.0, 1.0);
            case TORQUE_CUT -> new Controls(0.0, Units.clamp(state.phaseRemainingSeconds() / config.torqueCutSeconds(), 0.0, 1.0));
            case RATIO_SWITCH -> new Controls(0.0, 0.0);
            case REENGAGE -> {
                double engagement = Units.clamp(1.0 - state.phaseRemainingSeconds() / config.reengageSeconds(), 0.0, 1.0);
                yield new Controls(engagement, engagement);
            }
        };
    }

    /**
     * At most one new decision per input snapshot; all elapsed phase time is consumed, including dwell.
     * Zero seconds is allowed to sample a decision before mechanical stepping. A future adapter must split
     * mechanical integration at phase boundaries rather than apply end-of-step controls to an entire interval.
     */
    public static State advance(Config config, EngineProfile engine, TransmissionProfile transmission,
                                State state, Input input, double seconds) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(transmission, "transmission");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(input, "input");
        Units.nonnegative(seconds, "elapsed seconds");
        transmission.totalRatio(state.currentGear());
        transmission.totalRatio(state.targetGear());
        if (config.hysteresisRpm() >= engine.redlineRpm() - engine.idleRpm()) {
            throw new IllegalArgumentException("hysteresis must fit the engine working range");
        }

        double left = seconds;
        if (state.phase() == Phase.DRIVE) {
            if (input.mode() != state.latchedMode()) {
                // Latch at the observation boundary, then consume this interval from the new dwell.
                // A decision is allowed only after that dwell, including within a longer interval.
                state = settled(state.currentGear(), Math.max(state.dwellRemainingSeconds(), config.modeChangeDwellSeconds()), input.mode());
            }
            double waiting = Math.min(left, state.dwellRemainingSeconds());
            left -= waiting;
            double dwell = Math.max(0.0, state.dwellRemainingSeconds() - waiting);
            if (dwell > 1.0e-12) return settled(state.currentGear(), dwell, state.latchedMode());
            int target = chooseGear(config, engine, transmission, state.currentGear(), input);
            if (target == state.currentGear()) return settled(target, 0.0, state.latchedMode());
            state = new State(state.currentGear(), target, Phase.TORQUE_CUT, config.torqueCutSeconds(), 0.0, state.latchedMode());
        }

        while (state.phase() != Phase.DRIVE) {
            // A jump/landing may change wheel RPM after selection. Never reengage an overspeed target.
            if (state.phase() == Phase.REENGAGE && predictedRpm(transmission, input, state.currentGear()) >= engine.redlineRpm()) {
                return new State(state.currentGear(), state.currentGear(), Phase.REENGAGE, config.reengageSeconds(), 0.0, state.latchedMode());
            }
            double elapsed = Math.min(left, state.phaseRemainingSeconds());
            left -= elapsed;
            double remaining = Math.max(0.0, state.phaseRemainingSeconds() - elapsed);
            if (remaining > 1.0e-12) {
                return new State(state.currentGear(), state.targetGear(), state.phase(), remaining, 0.0, state.latchedMode());
            }
            state = switch (state.phase()) {
                case TORQUE_CUT -> safeTarget(engine, transmission, input, state.targetGear())
                        ? new State(state.targetGear(), state.targetGear(), Phase.RATIO_SWITCH, config.ratioSwitchSeconds(), 0.0, state.latchedMode())
                        : new State(state.currentGear(), state.currentGear(), Phase.REENGAGE, config.reengageSeconds(), 0.0, state.latchedMode());
                case RATIO_SWITCH -> new State(state.currentGear(), state.currentGear(), Phase.REENGAGE, config.reengageSeconds(), 0.0, state.latchedMode());
                case REENGAGE -> settled(state.currentGear(), Math.max(0.0,
                        Math.max(config.minimumDwellSeconds(), input.mode() == state.latchedMode() ? 0.0 : config.modeChangeDwellSeconds()) - left), input.mode());
                case DRIVE -> throw new IllegalStateException("unreachable phase");
            };
        }
        return state;
    }

    private static State settled(int gear, double dwell, Mode mode) {
        return new State(gear, gear, Phase.DRIVE, 0.0, dwell, mode);
    }

    private static int chooseGear(Config config, EngineProfile engine, TransmissionProfile transmission, int gear, Input input) {
        if (gear <= 0) return gear;
        // At a stop every forward ratio predicts zero shaft RPM. That is a clutch/launch
        // condition, not an unsafe downshift. Select the launch ratio in one timed shift
        // instead of making the next departure wait through several gear dwell periods.
        if (gear > 1 && Math.abs(predictedRpm(transmission, input, 1)) <= engine.idleRpm()
                && input.engineRpm() <= engine.idleRpm() + config.hysteresisRpm()) return 1;
        // Stopped tyres may retain a tiny negative solver residue. A slow hill rollback
        // also needs the safe launch ratio; faster reverse rotation keeps its guard.
        if (input.drivenWheelRadiansPerSecond() < 0.0) return gear;
        ModeMap map = config.map(input.mode());
        double demand = Math.max(input.throttle(), input.load());
        double up = threshold(engine, interpolate(map.upshiftLowDemand(), map.upshiftHighDemand(), demand));
        double down = threshold(engine, interpolate(map.downshiftLowDemand(), map.downshiftHighDemand(), demand));
        double kickTarget = Math.min(threshold(engine, map.kickdownTarget()), up - config.hysteresisRpm());
        if (input.throttle() >= map.kickdownThrottle() && input.engineRpm() < kickTarget - config.hysteresisRpm()) {
            int candidate = gear;
            double bestRpm = predictedRpm(transmission, input, gear);
            for (int lower = gear - 1; lower >= 1; lower--) {
                double rpm = predictedRpm(transmission, input, lower);
                if (safeTarget(engine, transmission, input, lower) && rpm <= kickTarget
                        && rpm > bestRpm + config.hysteresisRpm()) {
                    candidate = lower;
                    bestRpm = rpm;
                }
            }
            if (candidate != gear) return candidate;
        }
        if (gear < transmission.forwardRatios().size() && input.engineRpm() >= up
                && transmission.totalRatio(gear + 1) < transmission.totalRatio(gear)
                && safeTarget(engine, transmission, input, gear + 1)
                && predictedRpm(transmission, input, gear + 1) >= down + config.hysteresisRpm()) return gear + 1;
        if (gear > 1 && input.engineRpm() <= down
                && transmission.totalRatio(gear - 1) > transmission.totalRatio(gear)
                && safeTarget(engine, transmission, input, gear - 1)
                && predictedRpm(transmission, input, gear - 1) <= up - config.hysteresisRpm()) return gear - 1;
        return gear;
    }

    private static boolean safeTarget(EngineProfile engine, TransmissionProfile transmission, Input input, int gear) {
        double rpm = predictedRpm(transmission, input, gear);
        return Math.abs(rpm) < engine.redlineRpm();
    }

    private static double threshold(EngineProfile engine, double fraction) {
        return engine.idleRpm() + (engine.redlineRpm() - engine.idleRpm()) * fraction;
    }

    private static double interpolate(double low, double high, double amount) { return low + (high - low) * amount; }
}
