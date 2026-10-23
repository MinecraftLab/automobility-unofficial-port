package io.github.foundationgames.automobility.api.drivetrain.energy;

import java.util.Objects;

/**
 * Pure electrical request conversion. Physical motor storage remains entirely owned by its participant.
 * Efficiencies cover other conversion losses; explicit current-squared copper loss is charged separately.
 */
public final class EvEnergyPlanner {
    public record Config(double joulesPerUnit, double driveEfficiency, double regenerationEfficiency,
                         double accessoryWatts, double torqueLossWattsPerNewtonMetreSquared,
                         double maximumRegenerationWatts, boolean coastRegeneration, boolean reverseRegeneration) {
        public Config {
            EnergyDemand.positive(joulesPerUnit, "joules per electrical unit");
            EnergyDemand.between(driveEfficiency, 0.0, 1.0, "drive efficiency");
            EnergyDemand.positive(driveEfficiency, "drive efficiency");
            EnergyDemand.between(regenerationEfficiency, 0.0, 1.0, "regeneration efficiency");
            EnergyDemand.positive(regenerationEfficiency, "regeneration efficiency");
            EnergyDemand.nonnegative(accessoryWatts, "accessory watts");
            EnergyDemand.nonnegative(torqueLossWattsPerNewtonMetreSquared, "torque loss coefficient");
            EnergyDemand.nonnegative(maximumRegenerationWatts, "regeneration power");
        }
    }
    private EvEnergyPlanner() { }

    public static EnergyPlan plan(Config config, EnergyDemand demand, EnergyPlan.OwnerSnapshot owner, EnergyParticipant.Capability capability) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(demand, "demand");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(capability, "capability");
        var unit = EnergyPlan.Unit.ELECTRICAL_UNIT;
        boolean starterSource = demand.source() == EnergyDemand.Source.STARTER;
        if ((!starterSource && demand.source() != EnergyDemand.Source.ELECTRIC) || capability.source() != demand.source()
                || capability.unit() != unit || (starterSource && !capability.starterSupported())) {
            return EnergyPlan.refused(demand, unit, EnergyPlan.Status.CAPABILITY_MISMATCH, owner);
        }
        var work = demand.work();
        boolean driveRange = demand.range() == EnergyDemand.Range.FORWARD || demand.range() == EnergyDemand.Range.REVERSE;
        if (work.combustionWorkJoules() != 0.0 || work.combustionTorqueNewtonMetres() != 0.0
                || (!starterSource && (work.starterWorkJoules() != 0.0 || work.starterTorqueNewtonMetres() != 0.0))
                || (starterSource && (!EnergyDemand.sameWork(work.shaftWorkJoules(), work.starterWorkJoules())
                    || work.electricTorqueNewtonMetres() != 0.0 || work.regenerationMechanicalJoules() != 0.0
                    || work.electricDeliveredMechanicalJoules() != 0.0))
                || (!starterSource && !driveRange && (work.shaftWorkJoules() > 0.0 || work.electricTorqueNewtonMetres() != 0.0
                    || work.electricDeliveredMechanicalJoules() != 0.0))) {
            return EnergyPlan.refused(demand, unit, EnergyPlan.Status.UNSAFE_REQUEST, owner);
        }
        double delivered = starterSource ? work.starterWorkJoules() : work.electricDeliveredMechanicalJoules();
        double torque = starterSource ? work.starterTorqueNewtonMetres() : work.electricTorqueRmsNewtonMetres();
        if (((delivered > 0.0 || (torque != 0.0 && work.shaftWorkJoules() >= 0.0)) && !demand.flags().activePowerPermitted())
                || (starterSource && !demand.flags().starterPermitted())) return EnergyPlan.refused(demand, unit, EnergyPlan.Status.NO_PERMISSION, owner);
        double overhead = config.accessoryWatts() * demand.seconds();
        // RMS must cover the whole interval, including partial regen/cut events; signed mean torque undercharges copper loss.
        overhead += config.torqueLossWattsPerNewtonMetreSquared() * torque * torque * demand.seconds();
        double converted = delivered / config.driveEfficiency();
        double recovered = 0.0;
        boolean brakeIntent = demand.brake().physicalBrake() || (config.coastRegeneration() && capability.coastRegenerationSupported() && demand.throttleFraction() == 0.0);
        if (!starterSource && driveRange && (demand.range() != EnergyDemand.Range.REVERSE || config.reverseRegeneration())
                && capability.regenerationSupported() && demand.flags().grounded() && !demand.flags().hazard()
                && demand.flags().regenerationPermitted() && brakeIntent) recovered = work.regenerationMechanicalJoules();
        double stored = Math.min(recovered * config.regenerationEfficiency(), config.maximumRegenerationWatts() * demand.seconds());
        recovered = stored / config.regenerationEfficiency();
        var costs = new EnergyPlan.Costs(delivered, converted - delivered, overhead, converted + overhead, recovered, stored);
        if (costs.drawJoules() > 0.0 && !demand.flags().activePowerPermitted()) return EnergyPlan.refused(demand, unit, EnergyPlan.Status.NO_PERMISSION, owner);
        return EnergyPlan.settle(demand, unit, owner, config.joulesPerUnit(), costs);
    }
}
