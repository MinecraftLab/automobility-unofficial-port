package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.Objects;

/** ICE calibration data. Idle/start/anti-stall controllers are separate from this curve. */
public record EngineProfile(TorqueCurve torqueCurve, double inertiaKgMetresSquared,
                            double idleRpm, double redlineRpm) {
    public EngineProfile {
        Objects.requireNonNull(torqueCurve, "torqueCurve");
        Units.positive(inertiaKgMetresSquared, "engine inertia");
        Units.positive(idleRpm, "idle RPM");
        Units.positive(redlineRpm, "redline RPM");
        if (idleRpm >= redlineRpm) throw new IllegalArgumentException("idle must be below redline");
        TorqueCurve.Point first = torqueCurve.points().getFirst();
        TorqueCurve.Point last = torqueCurve.points().getLast();
        if (first.rpm() <= 0.0 || first.rpm() > idleRpm || torqueCurve.torqueAtRpm(idleRpm) <= 0.0) {
            throw new IllegalArgumentException("ICE curve must cover idle with positive torque and start above zero RPM");
        }
        if (last.rpm() > redlineRpm || (last.rpm() == redlineRpm && last.torqueNewtonMetres() > 0.0)) {
            throw new IllegalArgumentException("positive curve peaks must be reachable below the rev limiter");
        }
    }

    /** Garage and physics share these reachable shaft-output peaks; not wheel power or acceleration. */
    public TorqueCurve.Statistics statistics() { return torqueCurve.statistics(); }

    /** Requested positive combustion torque, before fuel-owner authorization and parasitic losses. */
    public double requestedTorque(double engineRadiansPerSecond, double throttle, boolean ignitionRunning) {
        Units.finite(engineRadiansPerSecond, "engine omega");
        Units.between(throttle, 0.0, 1.0, "throttle");
        double rpm = Units.radiansPerSecondToRpm(engineRadiansPerSecond);
        if (!ignitionRunning || rpm <= 0.0 || rpm >= redlineRpm) return 0.0;
        return throttle * torqueCurve.torqueAtRpm(rpm);
    }
}
