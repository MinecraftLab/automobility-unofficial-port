package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.Objects;

/** One demand path for keyboard and analog throttle; it never authorizes engine energy. */
public final class ThrottleController {
    public record Config(double riseSeconds, double fallSeconds) {
        public Config {
            Units.positive(riseSeconds, "throttle rise seconds");
            Units.positive(fallSeconds, "throttle fall seconds");
        }
        public static Config defaults() { return new Config(0.12, 0.08); }
    }
    public record State(double demand) {
        public State { Units.between(demand, 0.0, 1.0, "throttle demand"); }
    }
    public record Input(boolean analog, double value) {
        public Input {
            Units.finite(value, "throttle input");
            if (!analog && value != 0.0 && value != 1.0) throw new IllegalArgumentException("digital throttle must be held or released");
            value = Units.clamp(value, 0.0, 1.0);
        }
        public static Input digital(boolean held) { return new Input(false, held ? 1.0 : 0.0); }
        public static Input analog(double travel) { return new Input(true, travel); }
    }

    private ThrottleController() { }

    public static State advance(Config config, State state, Input input, double seconds) {
        Objects.requireNonNull(config, "config"); Objects.requireNonNull(state, "state"); Objects.requireNonNull(input, "input");
        Units.nonnegative(seconds, "elapsed seconds");
        if (input.analog()) return new State(input.value());
        double fullTravel = input.value() > state.demand() ? config.riseSeconds() : config.fallSeconds();
        double change = seconds / fullTravel;
        return new State(Units.clamp(state.demand() + Units.clamp(input.value() - state.demand(), -change, change), 0.0, 1.0));
    }
}
