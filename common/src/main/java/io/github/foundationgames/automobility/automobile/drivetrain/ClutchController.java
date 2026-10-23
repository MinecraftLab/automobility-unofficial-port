package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.Objects;

/** Pedal depression is 1 = fully pressed/disengaged, 0 = released, for both input paths. */
public final class ClutchController {
    public enum PedalMode { DIGITAL, ANALOG }

    /** Full-travel seconds; analog zero selects direct bounded pedal control. */
    public record Config(double releaseSeconds, double analogSlewSeconds) {
        public Config {
            Units.positive(releaseSeconds, "digital release seconds");
            Units.nonnegative(analogSlewSeconds, "analog slew seconds");
        }
    }

    public record State(double engagement) {
        public State { Units.between(engagement, 0.0, 1.0, "clutch engagement"); }
    }

    /**
     * Assistance is an externally chosen engagement ceiling, not an inference about ignition or RPM.
     * A ceiling of 1 disables that assistance. Analog pedal travel is clamped after finite validation.
     */
    public record Input(PedalMode mode, double pedalDepression, double assistanceEngagementCeiling) {
        public Input {
            Objects.requireNonNull(mode, "pedal mode");
            Units.finite(pedalDepression, "pedal depression");
            Units.between(assistanceEngagementCeiling, 0.0, 1.0, "assistance ceiling");
            if (mode == PedalMode.DIGITAL && pedalDepression != 0.0 && pedalDepression != 1.0) {
                throw new IllegalArgumentException("digital pedal must be explicitly held or released");
            }
            pedalDepression = Units.clamp(pedalDepression, 0.0, 1.0);
        }

        public static Input digital(boolean held) { return digital(held, 1.0); }
        public static Input digital(boolean held, double assistanceCeiling) {
            return new Input(PedalMode.DIGITAL, held ? 1.0 : 0.0, assistanceCeiling);
        }
        public static Input analog(double pedalDepression) { return analog(pedalDepression, 1.0); }
        public static Input analog(double pedalDepression, double assistanceCeiling) {
            return new Input(PedalMode.ANALOG, pedalDepression, assistanceCeiling);
        }
    }

    private ClutchController() { }

    /** Immutable control state only. A zero-duration sample may still apply an immediate pedal hold. */
    public static State advance(Config config, State state, Input input, double seconds) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(input, "input");
        Units.nonnegative(seconds, "elapsed seconds");
        if (input.mode() == PedalMode.DIGITAL && input.pedalDepression() == 1.0) return new State(0.0);

        double target = Math.min(1.0 - input.pedalDepression(), input.assistanceEngagementCeiling());
        double slewSeconds = input.mode() == PedalMode.DIGITAL ? config.releaseSeconds() : config.analogSlewSeconds();
        if (slewSeconds == 0.0) return new State(target);
        double change = seconds / slewSeconds;
        double next = state.engagement() + Units.clamp(target - state.engagement(), -change, change);
        // An assistance intervention may disengage immediately; removing it still follows the selected ramp.
        return new State(Units.clamp(Math.min(next, input.assistanceEngagementCeiling()), 0.0, 1.0));
    }
}
