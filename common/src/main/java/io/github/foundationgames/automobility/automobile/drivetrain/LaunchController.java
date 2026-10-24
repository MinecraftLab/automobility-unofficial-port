package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.Objects;

/** Automatic clutch launch and optional bounded service-brake hill hold; no speed or energy mutation. */
public final class LaunchController {
    public record Config(double additionalLaunchRpm, double rpmResponsePerSecond,
                         double engagementRiseSeconds, boolean hillHoldEnabled,
                         double hillHoldSeconds, double hillHoldMaximumSpeedMetresPerSecond) {
        public Config {
            Units.nonnegative(additionalLaunchRpm, "additional launch RPM");
            Units.positive(rpmResponsePerSecond, "launch response");
            Units.positive(engagementRiseSeconds, "launch engagement seconds");
            Units.nonnegative(hillHoldSeconds, "hill hold seconds");
            Units.nonnegative(hillHoldMaximumSpeedMetresPerSecond, "hill hold speed");
        }
        public static Config defaults() { return new Config(1000.0, 4.0, 0.25, true, 0.8, 0.2); }
    }
    public record State(double clutchCeiling, double hillHoldRemainingSeconds) {
        public State {
            Units.between(clutchCeiling, 0.0, 1.0, "launch clutch ceiling");
            Units.nonnegative(hillHoldRemainingSeconds, "hill hold remaining");
        }
        public static State stopped() { return new State(0.0, 0.0); }
    }
    public record Input(boolean running, boolean grounded, double engineRadiansPerSecond,
                        double drivenWheelRadiansPerSecond, double speedMetresPerSecond,
                        double throttle, double physicalBrake, double totalRatio,
                        double permittedActiveCrankTorqueNewtonMetres, double engineDragCapacityNewtonMetres,
                        double transmittedCrankTorqueNewtonMetres, double gradeSine,
                        double tyreForceLimitNewtons, double serviceBrakeCapacityNewtonMetres) {
        public Input {
            Units.finite(engineRadiansPerSecond, "engine omega");
            Units.finite(drivenWheelRadiansPerSecond, "wheel omega");
            Units.finite(speedMetresPerSecond, "speed");
            Units.between(throttle, 0.0, 1.0, "throttle");
            Units.between(physicalBrake, 0.0, 1.0, "physical brake");
            Units.finite(totalRatio, "ratio");
            Units.nonnegative(permittedActiveCrankTorqueNewtonMetres, "permitted crank torque");
            Units.nonnegative(engineDragCapacityNewtonMetres, "engine drag");
            Units.finite(transmittedCrankTorqueNewtonMetres, "transmitted crank torque");
            Units.between(gradeSine, -1.0, 1.0, "grade");
            Units.nonnegative(tyreForceLimitNewtons, "tyre force limit");
            Units.nonnegative(serviceBrakeCapacityNewtonMetres, "service brake capacity");
        }
    }
    public record Controls(double clutchEngagementCeiling, double assistedBrakeFraction) { }

    private LaunchController() { }

    public static State advance(Config config, EngineProfile engine, DrivetrainParameters mechanics,
                                State state, Input input, double seconds) {
        Objects.requireNonNull(config, "config"); Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(mechanics, "mechanics"); Objects.requireNonNull(state, "state"); Objects.requireNonNull(input, "input");
        Units.nonnegative(seconds, "elapsed seconds");
        double target = targetEngagement(config, engine, mechanics, input);
        double next = target < state.clutchCeiling() ? target
                : Math.min(target, state.clutchCeiling() + seconds / config.engagementRiseSeconds());
        double hold = Math.max(0.0, state.hillHoldRemainingSeconds() - seconds);
        boolean canHold = config.hillHoldEnabled() && input.grounded()
                && Math.abs(input.speedMetresPerSecond()) <= config.hillHoldMaximumSpeedMetresPerSecond();
        if (!canHold) hold = 0.0;
        else if (input.physicalBrake() > 0.1) hold = config.hillHoldSeconds();
        else if (input.throttle() > 0.01 && readyToReleaseHillHold(mechanics, input)) hold = 0.0;
        return new State(next, hold);
    }

    public static Controls controls(DrivetrainParameters mechanics, State state, Input input) {
        double hold = 0.0;
        if (state.hillHoldRemainingSeconds() > 0.0 && input.grounded() && input.serviceBrakeCapacityNewtonMetres() > 0.0) {
            double needed = mechanics.vehicleMassKg() * DrivetrainStep.GRAVITY_METRES_PER_SECOND_SQUARED
                    * Math.abs(input.gradeSine()) * mechanics.wheelRadiusMetres();
            hold = Units.clamp(1.2 * needed / input.serviceBrakeCapacityNewtonMetres(), 0.0, 1.0);
        }
        return new Controls(state.clutchCeiling(), hold);
    }

    private static double targetEngagement(Config config, EngineProfile engine, DrivetrainParameters p, Input in) {
        if (!in.running() || in.totalRatio() == 0.0 || p.clutchCapacityNewtonMetres() == 0.0
                || in.engineRadiansPerSecond() <= 0.0) return 0.0;
        double coupledOmega = in.drivenWheelRadiansPerSecond() * in.totalRatio();
        double additional = Math.min(config.additionalLaunchRpm(), 0.8 * (engine.redlineRpm() - engine.idleRpm()));
        double targetOmega = Units.rpmToRadiansPerSecond(engine.idleRpm() + additional * in.throttle());
        // Decouple before the idle governor can propel a coasting car indefinitely.
        // Above idle the closed clutch still supplies ordinary engine braking.
        if (in.throttle() <= 0.01 && coupledOmega <= Units.rpmToRadiansPerSecond(engine.idleRpm() * 1.10)) return 0.0;
        if (coupledOmega >= 0.95 * targetOmega) return 1.0;
        // Preserve the existing no-throttle/no-creep default near rest.
        if (in.throttle() <= 0.01) return 0.0;
        double accelerationReserve = Math.max(0.0, targetOmega - in.engineRadiansPerSecond())
                * config.rpmResponsePerSecond() * engine.inertiaKgMetresSquared();
        double transferable = Math.max(0.0, in.permittedActiveCrankTorqueNewtonMetres()
                - in.engineDragCapacityNewtonMetres() - accelerationReserve);
        return Units.clamp(transferable / p.clutchCapacityNewtonMetres(), 0.0, 1.0);
    }

    private static boolean readyToReleaseHillHold(DrivetrainParameters p, Input in) {
        if (!in.running() || in.totalRatio() == 0.0) return false;
        if (in.totalRatio() * in.gradeSine() <= 0.0) return true;
        double available = Math.max(0.0, in.transmittedCrankTorqueNewtonMetres()) * Math.abs(in.totalRatio()) / p.wheelRadiusMetres();
        available = Math.min(available, in.tyreForceLimitNewtons());
        double uphill = p.vehicleMassKg() * DrivetrainStep.GRAVITY_METRES_PER_SECOND_SQUARED * Math.abs(in.gradeSine());
        return available > uphill * 1.05;
    }
}
