package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.Objects;

/** Explicit ICE lifecycle and torque requests. Mechanical spin alone never starts combustion. */
public final class EngineController {
    public enum Phase { OFF, STARTING, RUNNING, STALLED }
    public enum Reason { NONE, IGNITION_OFF, START_ENERGY_UNAVAILABLE, STARTER_RELEASED,
        STARTER_TIMEOUT, LOW_RPM, STOPPED_OR_REVERSED, UNSAFE_CRANK_ROTATION }

    /** Motor RPM, inertia and torque come exclusively from EngineProfile; these are control calibrations. */
    public record Config(double starterTorqueFractionOfPeak, double maximumCrankSeconds,
                         double catchIdleFraction, double starterCutoffIdleFraction,
                         double stallIdleFraction, double stallDelaySeconds, boolean stallEnabled,
                         boolean antiStallEnabled, double antiStallFullOpenIdleFraction,
                         double idleGainPerSecond, double maximumIdleThrottle,
                         double dragBaseFractionOfPeak, double dragAtRedlineFractionOfPeak,
                         double limiterReleaseFraction) {
        public Config {
            Units.positive(starterTorqueFractionOfPeak, "starter torque fraction");
            Units.positive(maximumCrankSeconds, "maximum crank seconds");
            Units.between(catchIdleFraction, 0.0, 1.0, "catch idle fraction");
            Units.positive(catchIdleFraction, "catch idle fraction");
            Units.positive(starterCutoffIdleFraction, "starter cutoff fraction");
            if (starterCutoffIdleFraction <= 1.0) throw new IllegalArgumentException("starter cutoff must exceed idle");
            Units.between(stallIdleFraction, 0.0, catchIdleFraction, "stall idle fraction");
            Units.positive(stallIdleFraction, "stall idle fraction");
            Units.positive(stallDelaySeconds, "stall delay seconds");
            Units.between(antiStallFullOpenIdleFraction, 0.0, 1.0, "anti-stall full-open fraction");
            if (antiStallFullOpenIdleFraction == 1.0) throw new IllegalArgumentException("anti-stall band needs positive width");
            Units.positive(idleGainPerSecond, "idle gain");
            Units.between(maximumIdleThrottle, 0.0, 1.0, "maximum idle throttle");
            Units.nonnegative(dragBaseFractionOfPeak, "base drag fraction");
            Units.nonnegative(dragAtRedlineFractionOfPeak, "speed drag fraction");
            Units.between(limiterReleaseFraction, 0.0, 1.0, "limiter release fraction");
            if (limiterReleaseFraction == 0.0 || limiterReleaseFraction == 1.0) {
                throw new IllegalArgumentException("limiter release needs positive hysteresis");
            }
        }

        /** Reference calibration only; each adapter may supply its own assistance policy. */
        public static Config defaults() {
            return new Config(0.25, 2.0, 0.65, 1.15, 0.45, 0.25, true, true,
                    0.60, 10.0, 0.40, 0.03, 0.12, 0.97);
        }

        public Config withStallBehavior(boolean allowStall, boolean antiStall) {
            return new Config(starterTorqueFractionOfPeak, maximumCrankSeconds, catchIdleFraction,
                    starterCutoffIdleFraction, stallIdleFraction, stallDelaySeconds, allowStall,
                    antiStall, antiStallFullOpenIdleFraction, idleGainPerSecond, maximumIdleThrottle,
                    dragBaseFractionOfPeak, dragAtRedlineFractionOfPeak, limiterReleaseFraction);
        }
    }

    public record State(Phase phase, double crankSeconds, double lowRpmSeconds, boolean startHeld,
                        boolean revLimiterLatched, Reason reason) {
        public State {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(reason, "reason");
            Units.nonnegative(crankSeconds, "crank seconds");
            Units.nonnegative(lowRpmSeconds, "low-RPM seconds");
            if (phase != Phase.STARTING && crankSeconds != 0.0) throw new IllegalArgumentException("crank timer belongs to STARTING");
            if (phase != Phase.RUNNING && (lowRpmSeconds != 0.0 || revLimiterLatched)) {
                throw new IllegalArgumentException("running timers and limiter belong to RUNNING");
            }
        }
        public static State off() { return new State(Phase.OFF, 0.0, 0.0, false, false, Reason.NONE); }
        /** Explicit initial condition for an already running engine, never an inference from RPM. */
        public static State running() { return new State(Phase.RUNNING, 0.0, 0.0, false, false, Reason.NONE); }
        public static State stalled() { return new State(Phase.STALLED, 0.0, 0.0, false, false, Reason.NONE); }
    }

    /** Permissions are supplied by the external energy owner; a true flag is not an energy reservation. */
    public record Input(double engineRadiansPerSecond, boolean ignitionOn, boolean startHeld,
                        double throttle, boolean starterEnergyPermitted, boolean combustionEnergyPermitted) {
        public Input {
            Units.finite(engineRadiansPerSecond, "engine omega");
            Units.between(throttle, 0.0, 1.0, "throttle");
        }
    }

    /** Requested and permitted positive torques stay separate; idle assistance also requires permission. */
    public record Controls(double requestedCombustionTorqueNewtonMetres, double permittedCombustionTorqueNewtonMetres,
                           double requestedStarterTorqueNewtonMetres, double permittedStarterTorqueNewtonMetres,
                           double passiveDragCapacityNewtonMetres, double clutchEngagementCeiling,
                           boolean idleAssistActive, boolean revLimited) {
        public Controls {
            for (double value : new double[]{requestedCombustionTorqueNewtonMetres, permittedCombustionTorqueNewtonMetres,
                    requestedStarterTorqueNewtonMetres, permittedStarterTorqueNewtonMetres, passiveDragCapacityNewtonMetres}) {
                Units.nonnegative(value, "torque");
            }
            if (permittedCombustionTorqueNewtonMetres > requestedCombustionTorqueNewtonMetres
                    || permittedStarterTorqueNewtonMetres > requestedStarterTorqueNewtonMetres) {
                throw new IllegalArgumentException("permitted torque exceeds its request");
            }
            Units.between(clutchEngagementCeiling, 0.0, 1.0, "clutch ceiling");
        }
        /** Feed drag separately to its Coulomb row; never subtract it as a frozen signed external torque. */
        public double appliedActiveCrankTorqueNewtonMetres() {
            return Units.finite(permittedCombustionTorqueNewtonMetres + permittedStarterTorqueNewtonMetres, "active crank torque");
        }
    }

    private EngineController() { }

    /**
     * Input is a held snapshot. Sample advance(..., 0) before mechanics, then split at clock boundaries.
     * A start button must be released before a fresh attempt; holding it cannot bypass the finite timer.
     */
    public static State advance(Config config, EngineProfile engine, State state, Input input, double seconds) {
        validate(config, engine, state, input);
        Units.nonnegative(seconds, "elapsed seconds");
        boolean startEdge = input.startHeld() && !state.startHeld();
        if (!input.ignitionOn()) return state(Phase.OFF, 0.0, 0.0, input, false, Reason.IGNITION_OFF);
        double rpm = Units.radiansPerSecondToRpm(input.engineRadiansPerSecond());

        if (state.phase() == Phase.OFF || state.phase() == Phase.STALLED) {
            if (!startEdge) return state(state.phase(), 0.0, 0.0, input, false, state.reason());
            if (input.engineRadiansPerSecond() < 0.0) {
                return state(Phase.STALLED, 0.0, 0.0, input, false, Reason.UNSAFE_CRANK_ROTATION);
            }
            if (!input.starterEnergyPermitted()) {
                return state(state.phase(), 0.0, 0.0, input, false, Reason.START_ENERGY_UNAVAILABLE);
            }
            state = state(Phase.STARTING, 0.0, 0.0, input, false, Reason.NONE);
        }
        if (state.phase() == Phase.STARTING) {
            if (input.engineRadiansPerSecond() < 0.0) {
                return state(Phase.STALLED, 0.0, 0.0, input, false, Reason.UNSAFE_CRANK_ROTATION);
            }
            if (rpm >= catchRpm(config, engine) && input.combustionEnergyPermitted()) {
                state = state(Phase.RUNNING, 0.0, 0.0, input, false, Reason.NONE);
            } else {
                if (!input.startHeld()) return state(Phase.STALLED, 0.0, 0.0, input, false, Reason.STARTER_RELEASED);
                if (!input.starterEnergyPermitted()) return state(Phase.STALLED, 0.0, 0.0, input, false, Reason.START_ENERGY_UNAVAILABLE);
                double elapsed = Units.finite(state.crankSeconds() + seconds, "crank seconds");
                if (elapsed >= config.maximumCrankSeconds()) return state(Phase.STALLED, 0.0, 0.0, input, false, Reason.STARTER_TIMEOUT);
                return state(Phase.STARTING, elapsed, 0.0, input, false, Reason.NONE);
            }
        }

        // Stall-disabled assistance prevents low-RPM stalls, but cannot make a stopped/reversed ICE fire.
        if (rpm <= 0.0) return state(Phase.STALLED, 0.0, 0.0, input, false, Reason.STOPPED_OR_REVERSED);
        double low = config.stallEnabled() && rpm < engine.idleRpm() * config.stallIdleFraction()
                ? Units.finite(state.lowRpmSeconds() + seconds, "low-RPM seconds") : 0.0;
        if (low >= config.stallDelaySeconds()) return state(Phase.STALLED, 0.0, 0.0, input, false, Reason.LOW_RPM);
        boolean limiter = state.revLimiterLatched()
                ? rpm > engine.redlineRpm() * config.limiterReleaseFraction() : rpm >= engine.redlineRpm();
        return state(Phase.RUNNING, 0.0, low, input, limiter, Reason.NONE);
    }

    public static Controls controls(Config config, EngineProfile engine, State state, Input input) {
        validate(config, engine, state, input);
        double rpm = Units.radiansPerSecondToRpm(input.engineRadiansPerSecond());
        double peak = engine.statistics().torqueNewtonMetres().value();
        double drag = Units.finite(peak * (config.dragBaseFractionOfPeak()
                + config.dragAtRedlineFractionOfPeak() * Math.abs(rpm) / engine.redlineRpm()), "passive drag");
        boolean limited = state.revLimiterLatched() || rpm >= engine.redlineRpm();
        double combustion = 0.0;
        boolean idle = false;
        if (input.ignitionOn() && state.phase() == Phase.RUNNING && rpm > 0.0 && !limited) {
            // No invented below-curve torque: idle recovery requires explicit low-RPM calibration samples.
            double available = engine.torqueCurve().torqueAtRpm(rpm);
            if (available > 0.0) {
                double idleDemand = Math.max(0.0, drag + engine.inertiaKgMetresSquared() * config.idleGainPerSecond()
                        * (Units.rpmToRadiansPerSecond(engine.idleRpm()) - input.engineRadiansPerSecond()));
                double idleThrottle = Units.clamp(idleDemand / available, 0.0, config.maximumIdleThrottle());
                idle = idleThrottle > input.throttle();
                combustion = available * Math.max(input.throttle(), idleThrottle);
            }
        }
        double starter = input.ignitionOn() && state.phase() == Phase.STARTING && input.startHeld()
                && input.engineRadiansPerSecond() >= 0.0
                && rpm < engine.idleRpm() * config.starterCutoffIdleFraction()
                ? Units.finite(peak * config.starterTorqueFractionOfPeak(), "starter torque") : 0.0;
        boolean assist = config.antiStallEnabled() || !config.stallEnabled();
        double ceiling = 1.0;
        if (assist && state.phase() == Phase.STARTING) ceiling = 0.0;
        else if (assist && state.phase() == Phase.RUNNING) {
            double fullOpen = engine.idleRpm() * config.antiStallFullOpenIdleFraction();
            ceiling = Units.clamp((rpm - fullOpen) / (engine.idleRpm() - fullOpen), 0.0, 1.0);
        }
        return new Controls(combustion, input.combustionEnergyPermitted() ? combustion : 0.0,
                starter, input.starterEnergyPermitted() ? starter : 0.0, drag, ceiling, idle, limited);
    }

    /** Clock events only; RPM/catch/limiter changes require a fresh measured input snapshot. */
    public static double secondsToBoundary(Config config, EngineProfile engine, State state, Input input) {
        validate(config, engine, state, input);
        if (state.phase() == Phase.STARTING) return Math.max(0.0, config.maximumCrankSeconds() - state.crankSeconds());
        if (state.phase() == Phase.RUNNING && config.stallEnabled()
                && Units.radiansPerSecondToRpm(input.engineRadiansPerSecond()) < engine.idleRpm() * config.stallIdleFraction()) {
            return Math.max(0.0, config.stallDelaySeconds() - state.lowRpmSeconds());
        }
        return Double.POSITIVE_INFINITY;
    }

    public static double catchRpm(Config config, EngineProfile engine) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(engine, "engine");
        return Math.max(engine.torqueCurve().points().getFirst().rpm(), engine.idleRpm() * config.catchIdleFraction());
    }

    private static State state(Phase phase, double crank, double low, Input input, boolean limiter, Reason reason) {
        return new State(phase, crank, low, input.startHeld(), limiter, reason);
    }

    private static void validate(Config config, EngineProfile engine, State state, Input input) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(input, "input");
        if (engine.idleRpm() * config.starterCutoffIdleFraction() >= engine.redlineRpm()) {
            throw new IllegalArgumentException("starter cutoff must fit below the engine limiter");
        }
        if (engine.redlineRpm() * config.limiterReleaseFraction() <= engine.idleRpm()) {
            throw new IllegalArgumentException("limiter release must remain above idle");
        }
    }
}
