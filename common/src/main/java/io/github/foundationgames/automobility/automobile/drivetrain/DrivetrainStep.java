package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.Objects;

/** Constant-force event integration with bounded sticking/sliding friction; no game or storage side effects. */
public final class DrivetrainStep {
    public static final double GRAVITY_METRES_PER_SECOND_SQUARED = 9.80665;
    public static final double MAX_STEP_SECONDS = 0.05;
    public static final int MAX_SOLVER_SEGMENTS = CoulombSolver.MAX_SEGMENTS;

    public record Result(DrivetrainState state, double initialKineticEnergyJoules, double finalKineticEnergyJoules,
                         double crankWorkJoules, double longitudinalWorkJoules,
                         double clutchSlipEnergyJoules, double serviceBrakeEnergyJoules,
                         double tyreSlipEnergyJoules, double rollingResistanceEnergyJoules,
                         double clutchImpulseNewtonMetreSeconds, double brakeImpulseNewtonMetreSeconds,
                         double tyreImpulseNewtonSeconds, double rollingImpulseNewtonSeconds,
                         double engineDragEnergyJoules, double engineDragImpulseNewtonMetreSeconds,
                         int solverSegments) {
        public double dissipatedEnergyJoules() {
            return clutchSlipEnergyJoules + serviceBrakeEnergyJoules + tyreSlipEnergyJoules + rollingResistanceEnergyJoules + engineDragEnergyJoules;
        }
        public double energyResidualJoules() {
            return initialKineticEnergyJoules + crankWorkJoules + longitudinalWorkJoules
                    - finalKineticEnergyJoules - dissipatedEnergyJoules();
        }
    }

    private DrivetrainStep() { }

    public static double kineticEnergyJoules(DrivetrainParameters p, DrivetrainState s) {
        return 0.5 * p.engineInertiaKgMetresSquared() * s.engineRadiansPerSecond() * s.engineRadiansPerSecond()
                + 0.5 * p.wheelInertiaKgMetresSquared() * s.wheelRadiansPerSecond() * s.wheelRadiansPerSecond()
                + 0.5 * p.vehicleMassKg() * s.speedMetresPerSecond() * s.speedMetresPerSecond();
    }

    public static Result advance(DrivetrainParameters p, DrivetrainState s, DrivetrainInput input, double dt) {
        Objects.requireNonNull(p, "parameters");
        Objects.requireNonNull(s, "state");
        Objects.requireNonNull(input, "input");
        Units.positive(dt, "step seconds");
        if (dt > MAX_STEP_SECONDS) throw new IllegalArgumentException("subdivide steps longer than 0.05 seconds");
        double ratio = p.transmission().totalRatio(s.gear());
        double radius = p.wheelRadiusMetres();
        double initialEnergy = Units.finite(kineticEnergyJoules(p, s), "initial energy");
        double gradeForce = input.grounded() ? -p.vehicleMassKg() * GRAVITY_METRES_PER_SECOND_SQUARED * input.gradeSine() : 0.0;
        double force = Units.finite(gradeForce + input.externalLongitudinalForceNewtons(), "longitudinal force");
        double[] inverseMass = {1.0 / p.engineInertiaKgMetresSquared(), 1.0 / p.wheelInertiaKgMetresSquared(), 1.0 / p.vehicleMassKg()};
        for (double value : inverseMass) Units.positive(value, "inverse mass/inertia");
        double[][] rows = {{1.0, -ratio, 0.0}, {0.0, 1.0, 0.0}, {0.0, radius, -1.0}, {0.0, 0.0, 1.0}, {1.0, 0.0, 0.0}};
        double[] capacities = {
                ratio == 0.0 ? 0.0 : p.clutchCapacityNewtonMetres() * input.clutchEngagement(),
                input.serviceBrakeTorqueNewtonMetres(),
                input.grounded() ? input.tyreFrictionCoefficient() * input.normalLoadNewtons() : 0.0,
                input.grounded() ? input.rollingResistanceNewtons() : 0.0,
                input.engineDragTorqueNewtonMetres()};
        for (double capacity : capacities) Units.nonnegative(capacity, "constraint capacity");
        CoulombSolver.Solution solution = CoulombSolver.integrate(
                new double[]{s.engineRadiansPerSecond(), s.wheelRadiansPerSecond(), s.speedMetresPerSecond()},
                inverseMass, new double[]{input.appliedCrankTorqueNewtonMetres(), 0.0, force}, rows, capacities, dt);
        double[] velocity = solution.velocity();
        DrivetrainState resultState = new DrivetrainState(velocity[0], velocity[1], velocity[2], s.gear());
        double finalEnergy = Units.finite(kineticEnergyJoules(p, resultState), "final energy");
        double[] losses = solution.dissipation();
        double[] impulse = solution.impulse();
        Result result = new Result(resultState, initialEnergy, finalEnergy, solution.work()[0], solution.work()[2],
                losses[0], losses[1], losses[2], losses[3], impulse[0], impulse[1], impulse[2], impulse[3], losses[4], impulse[4], solution.segments());
        double scale = Math.max(1.0, initialEnergy + Math.abs(result.crankWorkJoules()) + Math.abs(result.longitudinalWorkJoules()));
        if (!Double.isFinite(result.energyResidualJoules()) || Math.abs(result.energyResidualJoules()) > 1.0e-9 * scale) {
            throw new IllegalArgumentException("mechanical energy ledger failed");
        }
        return result;
    }
}
