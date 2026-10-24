package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.Objects;

/** Sound requests from the same shaft and load as the solver; no road-speed or artificial shift input. */
public final class DrivetrainSoundModel {
    public enum Voice { DEFAULT, PETROL, DIESEL, ELECTRIC }
    public record Tone(float pitch, float gain, boolean audible) { }
    private DrivetrainSoundModel() { }

    public static Tone sample(double shaftRpm, double maximumRpm, double load,
                              EngineController.Phase phase, boolean electric) {
        return sample(shaftRpm, maximumRpm, load, phase, electric ? Voice.ELECTRIC : Voice.DEFAULT);
    }

    public static Tone sample(double shaftRpm, double maximumRpm, double load,
                              EngineController.Phase phase, Voice voice) {
        Objects.requireNonNull(voice, "engine voice");
        boolean electric = voice == Voice.ELECTRIC;
        Units.finite(shaftRpm, "shaft RPM");
        Units.positive(maximumRpm, "sound profile maximum RPM");
        Units.between(load, 0.0, 1.0, "sound load");
        Objects.requireNonNull(phase, "engine phase");
        boolean active = phase == EngineController.Phase.RUNNING || phase == EngineController.Phase.STARTING;
        double speed = Math.clamp(Math.abs(shaftRpm) / maximumRpm, 0.0, 1.15);
        double pitch = electric ? 0.65 + 1.25 * Math.sqrt(speed) : 0.60 + 1.35 * Math.pow(speed, 0.75);
        double gain = electric ? 0.025 + 0.40 * load + 0.18 * speed : 0.24 + 0.65 * load + 0.10 * speed;
        // Distinct RPM envelopes and load response accompany the selected existing engine loop.
        if (voice == Voice.PETROL) { pitch = 0.64 + 1.40 * Math.pow(speed, 0.85); gain = 0.22 + 0.66 * load + 0.11 * speed; }
        else if (voice == Voice.DIESEL) { pitch = 0.52 + 1.20 * Math.pow(speed, 0.60); gain = 0.30 + 0.55 * load + 0.12 * speed; }
        if (phase == EngineController.Phase.STARTING) gain *= 0.50;
        // A switched-off crank still takes time to stop. Keep its mechanical run-down
        // audible from measured shaft speed, without implying combustion or energy use.
        if (!active) gain = electric ? 0.0 : (voice == Voice.DIESEL ? 0.28 : voice == Voice.PETROL ? 0.22 : 0.24) * Math.clamp(Math.abs(shaftRpm) / 800.0, 0.0, 1.0);
        return new Tone((float)Math.clamp(pitch, 0.5, 2.0), (float)Math.clamp(gain, 0.0, 1.0), gain > 0.001);
    }
}
