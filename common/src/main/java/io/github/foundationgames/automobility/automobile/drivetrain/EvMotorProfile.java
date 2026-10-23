package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.Objects;

/** Physical motor data; reduction belongs to the vehicle, and electrical storage belongs to its actual owner. */
public record EvMotorProfile(TorqueCurve motoringNewtonMetres, TorqueCurve regenerationNewtonMetres,
                             double rotorInertiaKgMetresSquared, double maximumRpm) {
    public EvMotorProfile {
        Objects.requireNonNull(motoringNewtonMetres, "motoring curve");
        Objects.requireNonNull(regenerationNewtonMetres, "regeneration curve");
        Units.positive(rotorInertiaKgMetresSquared, "rotor inertia");
        Units.positive(maximumRpm, "maximum motor RPM");
        for (TorqueCurve curve : new TorqueCurve[]{motoringNewtonMetres, regenerationNewtonMetres}) {
            if (curve.points().getFirst().rpm() != 0.0 || curve.points().getLast().rpm() > maximumRpm) {
                throw new IllegalArgumentException("EV curves must cover zero RPM and remain within the declared limit");
            }
        }
        var last = motoringNewtonMetres.points().getLast();
        if (last.rpm() == maximumRpm && last.torqueNewtonMetres() > 0.0) {
            throw new IllegalArgumentException("motoring peaks must be reachable below the inverter cutoff");
        }
    }
    public double maximumMotoringTorque(double signedMotorRpm) {
        Units.finite(signedMotorRpm, "motor RPM");
        return Math.abs(signedMotorRpm) >= maximumRpm ? 0.0 : motoringNewtonMetres.torqueAtRpm(Math.abs(signedMotorRpm));
    }
    public double maximumRegenerationTorque(double signedMotorRpm) {
        Units.finite(signedMotorRpm, "motor RPM");
        return regenerationNewtonMetres.torqueAtRpm(Math.abs(signedMotorRpm));
    }
    /** Garage output is motor shaft output from the same motoring curve, not wheel force. */
    public TorqueCurve.Statistics statistics() { return motoringNewtonMetres.statistics(); }
}
