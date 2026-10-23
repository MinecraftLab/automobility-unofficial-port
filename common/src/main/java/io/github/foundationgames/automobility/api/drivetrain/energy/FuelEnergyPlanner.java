package io.github.foundationgames.automobility.api.drivetrain.energy;

import java.util.Objects;

/** Pure fuel economics; driving mode has no input or percentage surcharge. */
public final class FuelEnergyPlanner {
    public record Config(double joulesPerMillilitre, double minimumEfficiency, double peakEfficiency,
                         double optimumRpm, double loadPenalty, double rpmPenalty,
                         double idleWatts, double pumpingJoulesPerRevolution,
                         double starterEfficiency, double starterOverheadWatts) {
        public Config {
            EnergyDemand.positive(joulesPerMillilitre, "fuel energy density");
            EnergyDemand.between(minimumEfficiency, 0.0, 1.0, "minimum efficiency");
            EnergyDemand.positive(minimumEfficiency, "minimum efficiency");
            EnergyDemand.between(peakEfficiency, minimumEfficiency, 1.0, "peak efficiency");
            EnergyDemand.positive(optimumRpm, "efficiency RPM");
            EnergyDemand.nonnegative(loadPenalty, "load penalty");
            EnergyDemand.nonnegative(rpmPenalty, "RPM penalty");
            EnergyDemand.nonnegative(idleWatts, "idle watts");
            EnergyDemand.nonnegative(pumpingJoulesPerRevolution, "pumping cost");
            EnergyDemand.between(starterEfficiency, 0.0, 1.0, "starter efficiency");
            EnergyDemand.positive(starterEfficiency, "starter efficiency");
            EnergyDemand.nonnegative(starterOverheadWatts, "starter overhead");
        }
    }
    private FuelEnergyPlanner() { }

    public static EnergyPlan plan(Config config, EnergyDemand demand, EnergyPlan.OwnerSnapshot owner, EnergyParticipant.Capability capability) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(demand, "demand");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(capability, "capability");
        var unit = EnergyPlan.Unit.MILLILITRE;
        var work = demand.work();
        boolean starter = work.starterWorkJoules() > 0.0 || work.starterTorqueNewtonMetres() > 0.0;
        if (demand.source() != EnergyDemand.Source.FUEL || capability.source() != demand.source() || capability.unit() != unit
                || (starter && !capability.starterSupported())) return EnergyPlan.refused(demand, unit, EnergyPlan.Status.CAPABILITY_MISMATCH, owner);
        double delivered = EnergyDemand.finite(work.combustionWorkJoules() + work.starterWorkJoules(), "delivered work");
        if (!EnergyDemand.sameWork(Math.max(0.0, work.shaftWorkJoules()), delivered)
                || work.electricTorqueNewtonMetres() != 0.0 || work.electricDeliveredMechanicalJoules() != 0.0 || work.regenerationMechanicalJoules() != 0.0
                || ((!demand.flags().combustionRunning() || demand.flags().fuelCut())
                    && (work.combustionWorkJoules() > 0.0 || work.combustionTorqueNewtonMetres() > 0.0))) {
            return EnergyPlan.refused(demand, unit, EnergyPlan.Status.UNSAFE_REQUEST, owner);
        }
        if (((delivered > 0.0 || work.combustionTorqueNewtonMetres() > 0.0 || starter) && !demand.flags().activePowerPermitted())
                || (starter && !demand.flags().starterPermitted())) {
            return EnergyPlan.refused(demand, unit, EnergyPlan.Status.NO_PERMISSION, owner);
        }
        double rpmError = Math.abs(demand.shaftRpm()) / config.optimumRpm() - 1.0;
        double efficiency = Math.max(config.minimumEfficiency(), config.peakEfficiency()
                - config.loadPenalty() * square(1.0 - demand.loadFraction()) - config.rpmPenalty() * square(rpmError));
        double overhead = demand.flags().combustionRunning() && !demand.flags().fuelCut()
                ? config.idleWatts() * demand.seconds() + config.pumpingJoulesPerRevolution() * Math.abs(demand.shaftRpm()) / 60.0 * demand.seconds() : 0.0;
        if (starter) overhead += config.starterOverheadWatts() * demand.seconds();
        double converted = work.combustionWorkJoules() / efficiency + work.starterWorkJoules() / config.starterEfficiency();
        EnergyPlan.Costs costs = new EnergyPlan.Costs(delivered, EnergyDemand.nonnegative(converted - delivered, "conversion loss"),
                overhead, converted + overhead, 0.0, 0.0);
        if (costs.drawJoules() > 0.0 && !demand.flags().activePowerPermitted()) return EnergyPlan.refused(demand, unit, EnergyPlan.Status.NO_PERMISSION, owner);
        return EnergyPlan.settle(demand, unit, owner, config.joulesPerMillilitre(), costs);
    }
    private static double square(double value) { return value * value; }
}
