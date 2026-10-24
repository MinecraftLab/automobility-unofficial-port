package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.Objects;

/** Sound requests from the same shaft and load as the solver; no road-speed or artificial shift input. */
public final class DrivetrainSoundModel {
    public record Tone(float pitch, float gain, boolean audible) { }
    private DrivetrainSoundModel() { }

    public static Tone sample(double shaftRpm, double maximumRpm, double load,
                              EngineController.Phase phase, boolean electric) {
        Units.finite(shaftRpm, "shaft RPM");
        Units.positive(maximumRpm, "sound profile maximum RPM");
        Units.between(load, 0.0, 1.0, "sound load");
        Objects.requireNonNull(phase, "engine phase");
        boolean active = phase == EngineController.Phase.RUNNING || phase == EngineController.Phase.STARTING;
        double speed = Math.clamp(Math.abs(shaftRpm) / maximumRpm, 0.0, 1.15);
        double pitch = electric ? 0.65 + 1.25 * Math.sqrt(speed) : 0.60 + 1.35 * Math.pow(speed, 0.75);
        double gain = electric ? 0.025 + 0.40 * load + 0.18 * speed : 0.24 + 0.65 * load + 0.10 * speed;
        if (phase == EngineController.Phase.STARTING) gain *= 0.50;
        // A switched-off crank still takes time to stop. Keep its mechanical run-down
        // audible from measured shaft speed, without implying combustion or energy use.
        if (!active) gain = electric ? 0.0 : 0.24 * Math.clamp(Math.abs(shaftRpm) / 800.0, 0.0, 1.0);
        return new Tone((float)Math.clamp(pitch, 0.5, 2.0), (float)Math.clamp(gain, 0.0, 1.0), gain > 0.001);
    }
}
