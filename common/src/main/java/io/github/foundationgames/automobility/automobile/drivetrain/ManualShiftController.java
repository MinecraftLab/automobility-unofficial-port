package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.Objects;

/** One-shot manual requests and shift protection; no velocity, inventory or energy mutations. */
public final class ManualShiftController {
    public enum Request { NONE, UP, DOWN, NEUTRAL, REVERSE }
    public enum Phase { DRIVE, TORQUE_CUT, RATIO_SWITCH, REENGAGE }
    public enum Reason { NONE, HIGHEST_GEAR, LOWEST_FORWARD_GEAR, CLUTCH_REQUIRED, SHIFT_BUSY,
        REVERSE_WHILE_MOVING_FORWARD, FORWARD_WHILE_MOVING_BACKWARD, OVER_REV, UNSAFE_REENGAGEMENT }

    public record Config(double torqueCutSeconds, double ratioSwitchSeconds, double reengageSeconds,
                         double maximumShiftEngagement, double maximumDirectionChangeSpeedMetresPerSecond,
                         double maximumDirectionChangeWheelRadiansPerSecond, double overRevFraction,
                         boolean clutchAssist, boolean revMatchAssist, double revMatchGain, boolean downshiftProtection) {
        public Config(double torqueCutSeconds, double ratioSwitchSeconds, double reengageSeconds,
                      double maximumShiftEngagement, double maximumDirectionChangeSpeedMetresPerSecond,
                      double maximumDirectionChangeWheelRadiansPerSecond, double overRevFraction,
                      boolean clutchAssist, boolean revMatchAssist, double revMatchGain) {
            this(torqueCutSeconds, ratioSwitchSeconds, reengageSeconds, maximumShiftEngagement,
                    maximumDirectionChangeSpeedMetresPerSecond, maximumDirectionChangeWheelRadiansPerSecond,
                    overRevFraction, clutchAssist, revMatchAssist, revMatchGain, true);
        }
        public Config {
            Units.positive(torqueCutSeconds, "torque cut seconds");
            Units.positive(ratioSwitchSeconds, "ratio switch seconds");
            Units.positive(reengageSeconds, "reengage seconds");
            Units.between(maximumShiftEngagement, 0.0, 1.0, "maximum shift engagement");
            Units.nonnegative(maximumDirectionChangeSpeedMetresPerSecond, "direction speed guard");
            Units.nonnegative(maximumDirectionChangeWheelRadiansPerSecond, "direction wheel guard");
            Units.between(overRevFraction, 0.0, 1.0, "over-rev fraction");
            Units.positive(overRevFraction, "over-rev fraction");
            Units.nonnegative(revMatchGain, "rev-match gain");
        }
        public static Config defaults() { return new Config(0.05, 0.04, 0.20, 0.05, 0.35, 0.75, 0.98, true, true, 2.0); }
        public Config withAssistance(boolean assistedClutch, boolean assistedRevMatch) {
            return new Config(torqueCutSeconds, ratioSwitchSeconds, reengageSeconds, maximumShiftEngagement,
                    maximumDirectionChangeSpeedMetresPerSecond, maximumDirectionChangeWheelRadiansPerSecond,
                    overRevFraction, assistedClutch, assistedRevMatch, revMatchGain, downshiftProtection);
        }
        public Config withDownshiftProtection(boolean enabled) {
            return new Config(torqueCutSeconds, ratioSwitchSeconds, reengageSeconds, maximumShiftEngagement,
                    maximumDirectionChangeSpeedMetresPerSecond, maximumDirectionChangeWheelRadiansPerSecond,
                    overRevFraction, clutchAssist, revMatchAssist, revMatchGain, enabled);
        }
    }

    public record State(int currentGear, int targetGear, Phase phase, double phaseRemainingSeconds, Reason reason) {
        public State {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(reason, "reason");
            Units.nonnegative(phaseRemainingSeconds, "phase seconds");
            if (currentGear < -1 || targetGear < -1) throw new IllegalArgumentException("invalid gear");
            if (phase == Phase.DRIVE && (currentGear != targetGear || phaseRemainingSeconds != 0.0)) {
                throw new IllegalArgumentException("DRIVE requires a settled gear");
            }
            if (phase == Phase.TORQUE_CUT && currentGear == targetGear) throw new IllegalArgumentException("cut requires a pending ratio");
            if ((phase == Phase.RATIO_SWITCH || phase == Phase.REENGAGE) && currentGear != targetGear) {
                throw new IllegalArgumentException("ratio must be committed before reengagement");
            }
        }
        public static State drive(int gear) { return new State(gear, gear, Phase.DRIVE, 0.0, Reason.NONE); }
    }

    /** Request is a key-edge command, not a held button. Chassis speed additionally protects locked/slipping wheels. */
    public record Input(double engineRadiansPerSecond, double drivenWheelRadiansPerSecond,
                        double speedMetresPerSecond, double clutchEngagement, Request request) {
        public Input {
            Units.finite(engineRadiansPerSecond, "engine omega");
            Units.finite(drivenWheelRadiansPerSecond, "driven wheel omega");
            Units.finite(speedMetresPerSecond, "speed");
            Units.between(clutchEngagement, 0.0, 1.0, "clutch engagement");
            Objects.requireNonNull(request, "request");
        }
        public Input withoutRequest() { return new Input(engineRadiansPerSecond, drivenWheelRadiansPerSecond, speedMetresPerSecond, clutchEngagement, Request.NONE); }
    }

    /** Rev matching is only a throttle request; the engine's combustion permission still applies. */
    public record Controls(double torqueMultiplier, double clutchEngagementCeiling, double requestedRevMatchThrottle) {
        public Controls {
            Units.between(torqueMultiplier, 0.0, 1.0, "torque multiplier");
            Units.between(clutchEngagementCeiling, 0.0, 1.0, "clutch ceiling");
            Units.between(requestedRevMatchThrottle, 0.0, 1.0, "rev-match throttle");
        }
    }

    private ManualShiftController() { }

    /** Explicit validated range/H-pattern selection shares the same guards and timed actuator. */
    public static State requestGear(Config config, EngineProfile engine, TransmissionProfile transmission,
                                    State state, Input input, int targetGear) {
        validate(config, engine, transmission, state, input);
        transmission.totalRatio(targetGear);
        if (state.phase() != Phase.DRIVE) return new State(state.currentGear(), state.targetGear(),
                state.phase(), state.phaseRemainingSeconds(), Reason.SHIFT_BUSY);
        if (targetGear == state.currentGear()) return settled(state.currentGear(), Reason.NONE);
        Reason rejected = rejection(config, engine, transmission, targetGear, input, true);
        return rejected == Reason.NONE
                ? new State(state.currentGear(), targetGear, Phase.TORQUE_CUT, config.torqueCutSeconds(), Reason.NONE)
                : settled(state.currentGear(), rejected);
    }

    public static double predictedRpm(TransmissionProfile transmission, Input input, int gear) {
        Objects.requireNonNull(transmission, "transmission");
        Objects.requireNonNull(input, "input");
        return Math.abs(Units.radiansPerSecondToRpm(input.drivenWheelRadiansPerSecond() * transmission.totalRatio(gear)));
    }

    public static State advance(Config config, EngineProfile engine, TransmissionProfile transmission,
                                State state, Input input, double seconds) {
        validate(config, engine, transmission, state, input);
        Units.nonnegative(seconds, "elapsed seconds");
        double left = seconds;
        if (state.phase() == Phase.DRIVE && input.request() != Request.NONE) {
            int target = switch (input.request()) {
                case UP -> state.currentGear() < transmission.forwardRatios().size() ? state.currentGear() + 1 : state.currentGear();
                case DOWN -> state.currentGear() > 0 ? state.currentGear() - 1 : state.currentGear();
                case NEUTRAL -> 0;
                case REVERSE -> -1;
                case NONE -> state.currentGear();
            };
            Reason rejected = target == state.currentGear()
                    ? input.request() == Request.UP ? Reason.HIGHEST_GEAR
                    : input.request() == Request.DOWN ? Reason.LOWEST_FORWARD_GEAR : Reason.NONE
                    : rejection(config, engine, transmission, target, input, true);
            if (target == state.currentGear() || rejected != Reason.NONE) return settled(state.currentGear(), rejected);
            state = new State(state.currentGear(), target, Phase.TORQUE_CUT, config.torqueCutSeconds(), Reason.NONE);
        } else if (state.phase() != Phase.DRIVE && input.request() != Request.NONE) {
            state = new State(state.currentGear(), state.targetGear(), state.phase(), state.phaseRemainingSeconds(), Reason.SHIFT_BUSY);
        }
        while (state.phase() != Phase.DRIVE) {
            if (state.phase() == Phase.REENGAGE && rejection(config, engine, transmission, state.currentGear(), input, false) != Reason.NONE) {
                return new State(state.currentGear(), state.currentGear(), Phase.REENGAGE, config.reengageSeconds(), Reason.UNSAFE_REENGAGEMENT);
            }
            double elapsed = Math.min(left, state.phaseRemainingSeconds());
            left -= elapsed;
            double remaining = Math.max(0.0, state.phaseRemainingSeconds() - elapsed);
            if (remaining > 1.0e-12) return new State(state.currentGear(), state.targetGear(), state.phase(), remaining, state.reason());
            state = switch (state.phase()) {
                case TORQUE_CUT -> {
                    Reason rejected = rejection(config, engine, transmission, state.targetGear(), input, false);
                    if (rejected == Reason.NONE && state.targetGear() != 0 && input.clutchEngagement() > config.maximumShiftEngagement()) {
                        rejected = Reason.CLUTCH_REQUIRED;
                    }
                    yield rejected == Reason.NONE
                            ? new State(state.targetGear(), state.targetGear(), Phase.RATIO_SWITCH, config.ratioSwitchSeconds(), state.reason())
                            : new State(state.currentGear(), state.currentGear(), Phase.REENGAGE, config.reengageSeconds(), rejected);
                }
                case RATIO_SWITCH -> new State(state.currentGear(), state.currentGear(), Phase.REENGAGE, config.reengageSeconds(), state.reason());
                case REENGAGE -> settled(state.currentGear(), state.reason());
                case DRIVE -> throw new IllegalStateException("unreachable phase");
            };
        }
        return state;
    }

    public static Controls controls(Config config, EngineProfile engine, TransmissionProfile transmission, State state, Input input) {
        validate(config, engine, transmission, state, input);
        if (state.phase() == Phase.DRIVE) return new Controls(1.0, 1.0, 0.0);
        if (state.phase() == Phase.REENGAGE && rejection(config, engine, transmission, state.currentGear(), input, false) != Reason.NONE) {
            return new Controls(config.clutchAssist() ? 0.0 : 1.0, 0.0, 0.0);
        }
        double progress = state.phase() == Phase.REENGAGE
                ? Units.clamp(1.0 - state.phaseRemainingSeconds() / config.reengageSeconds(), 0.0, 1.0) : 0.0;
        double ceiling = config.clutchAssist() ? progress : 1.0;
        double revMatch = 0.0;
        if (config.revMatchAssist() && state.currentGear() != 0
                && (state.phase() == Phase.RATIO_SWITCH || state.phase() == Phase.REENGAGE)) {
            double target = input.drivenWheelRadiansPerSecond() * transmission.totalRatio(state.currentGear());
            if (target > 0.0) revMatch = Units.clamp((target - input.engineRadiansPerSecond())
                    / Units.rpmToRadiansPerSecond(engine.redlineRpm() - engine.idleRpm()) * config.revMatchGain(), 0.0, 1.0);
        }
        // With a real manual pedal, the open clutch interrupts traction; the driver retains throttle for a blip.
        return new Controls(config.clutchAssist() ? progress : 1.0, ceiling, revMatch);
    }

    public static double secondsToBoundary(Config config, State state) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(state, "state");
        return state.phase() == Phase.DRIVE ? Double.POSITIVE_INFINITY : state.phaseRemainingSeconds();
    }

    private static Reason rejection(Config config, EngineProfile engine, TransmissionProfile transmission,
                                    int target, Input input, boolean checkDriverClutch) {
        if (target == 0) return Reason.NONE;
        if (target == -1 && (input.speedMetresPerSecond() > config.maximumDirectionChangeSpeedMetresPerSecond()
                || input.drivenWheelRadiansPerSecond() > config.maximumDirectionChangeWheelRadiansPerSecond())) {
            return Reason.REVERSE_WHILE_MOVING_FORWARD;
        }
        if (target > 0 && (input.speedMetresPerSecond() < -config.maximumDirectionChangeSpeedMetresPerSecond()
                || input.drivenWheelRadiansPerSecond() < -config.maximumDirectionChangeWheelRadiansPerSecond())) {
            return Reason.FORWARD_WHILE_MOVING_BACKWARD;
        }
        if (config.downshiftProtection() && predictedRpm(transmission, input, target) >= engine.redlineRpm() * config.overRevFraction()) return Reason.OVER_REV;
        if (checkDriverClutch && !config.clutchAssist() && input.clutchEngagement() > config.maximumShiftEngagement()) return Reason.CLUTCH_REQUIRED;
        return Reason.NONE;
    }

    private static State settled(int gear, Reason reason) { return new State(gear, gear, Phase.DRIVE, 0.0, reason); }

    private static void validate(Config config, EngineProfile engine, TransmissionProfile transmission, State state, Input input) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(transmission, "transmission");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(input, "input");
        transmission.totalRatio(state.currentGear());
        transmission.totalRatio(state.targetGear());
        if (engine.redlineRpm() * config.overRevFraction() <= engine.idleRpm()) throw new IllegalArgumentException("protection threshold must exceed idle");
    }
}
