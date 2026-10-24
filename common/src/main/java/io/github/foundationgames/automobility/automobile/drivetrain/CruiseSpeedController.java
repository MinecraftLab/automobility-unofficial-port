package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.Objects;

/** Traction demand only. Mechanical integration and energy receipts remain with their existing owners. */
public final class CruiseSpeedController {
    public record Config(double proportionalGain, double integralGain, double resetTargetDeltaMetresPerSecond) {
        public Config {
            Units.positive(proportionalGain, "cruise proportional gain");
            Units.positive(integralGain, "cruise integral gain");
            Units.positive(resetTargetDeltaMetresPerSecond, "cruise reset target delta");
        }
        public static Config defaults() { return new Config(.65, .45, 2); }
    }
    public record State(double integralThrottle, double targetMetresPerSecond, boolean active) {
        public State {
            Units.between(integralThrottle, 0, 1, "cruise integral");
            Units.nonnegative(targetMetresPerSecond, "cruise state target");
            if (!active && (integralThrottle != 0 || targetMetresPerSecond != 0))
                throw new IllegalArgumentException("inactive cruise retains no demand");
        }
        public static State idle() { return new State(0, 0, false); }
    }
    public record Command(State state, double throttle) {
        public Command { Objects.requireNonNull(state); Units.between(throttle, 0, 1, "cruise throttle"); }
    }
    private CruiseSpeedController() { }

    /** Disabled/braking/unpowered samples must be supplied as disabled, clearing any remembered demand. */
    public static Command advance(Config config, State state, boolean enabled, double targetMetresPerSecond,
                                  double measuredMetresPerSecond, double seconds) {
        Objects.requireNonNull(config); Objects.requireNonNull(state);
        Units.nonnegative(targetMetresPerSecond, "cruise target");
        Units.finite(measuredMetresPerSecond, "cruise measured speed");
        Units.between(seconds, 0, 1, "cruise control interval");
        if (!enabled || targetMetresPerSecond == 0) return new Command(State.idle(), 0);
        double integral = !state.active() || Math.abs(targetMetresPerSecond - state.targetMetresPerSecond())
                > config.resetTargetDeltaMetresPerSecond() ? 0 : state.integralThrottle();
        double error = Units.clamp(targetMetresPerSecond - measuredMetresPerSecond, -20, 20);
        double requested = config.proportionalGain() * error + integral;
        // Do not accumulate demand while the actuator is already at its limit in that direction.
        if ((requested < 1 || error < 0) && (requested > 0 || error > 0))
            integral = Units.clamp(integral + config.integralGain() * error * seconds, 0, 1);
        double throttle = Units.clamp(config.proportionalGain() * error + integral, 0, 1);
        return new Command(new State(integral, targetMetresPerSecond, true), throttle);
    }
}
