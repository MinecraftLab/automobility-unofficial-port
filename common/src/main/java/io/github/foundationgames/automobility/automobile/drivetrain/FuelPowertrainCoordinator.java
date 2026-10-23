package io.github.foundationgames.automobility.automobile.drivetrain;

import io.github.foundationgames.automobility.api.drivetrain.energy.*;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Orders immutable ICE mechanics and the real owner's transaction. Returning a result is the sole
 * permission to publish that mechanical preview; this class never owns a tank or changes world state.
 */
public final class FuelPowertrainCoordinator {
    public enum Status { PAID, COASTED_RESOURCE_DENIED, COASTED_OWNER_UNCERTAIN, COASTED_UNSUPPORTED_WORK }
    public record Result(PowertrainSimulation.Result mechanics, Status status,
                         Optional<EnergyParticipant.Receipt> receipt, Optional<EnergyPlan.Status> ownerDecision) { }

    private final EnergyDemand.Identity identity;
    private final EnergyTransactionGuard guard;
    private final Function<EnergyParticipant.Preview, EnergyPlan> ownerPrice;
    private long lastTick = -1;
    private boolean uncertain;

    public FuelPowertrainCoordinator(EnergyDemand.Identity identity, EnergyTransactionGuard guard,
                                     Function<EnergyParticipant.Preview, EnergyPlan> ownerPrice) {
        this.identity = Objects.requireNonNull(identity); this.guard = Objects.requireNonNull(guard);
        this.ownerPrice = Objects.requireNonNull(ownerPrice);
    }
    public boolean ownerReconciliationRequired() { return uncertain; }

    /** Tick and request identity come from the server runtime, never client physics or wall time. */
    public Result advance(PowertrainSimulation.Setup setup, PowertrainSimulation.State initial,
                          PowertrainSimulation.DriverInput driver, PowertrainSimulation.Environment environment,
                          double seconds, long serverTick, UUID requestId, boolean hazard) {
        Objects.requireNonNull(requestId);
        if (serverTick < 0 || serverTick <= lastTick) throw new IllegalStateException("mechanical tick already consumed or reordered");
        lastTick = serverTick;
        if (uncertain) return coast(setup, initial, driver, environment, seconds, Status.COASTED_OWNER_UNCERTAIN, null);

        var powered = PowertrainSimulation.advance(setup, initial, driver, environment, seconds);
        var energy = powered.energy();
        // A reversed crank is a mechanical absorption case, not a positive combustion delivery or credit.
        // Until a combustion absorption model is calibrated, preserve it only in the unpowered preview.
        if (energy.combustionWorkJoules() < 0.0 || energy.starterWorkJoules() < 0.0) {
            return coast(setup, initial, driver, environment, seconds, Status.COASTED_UNSUPPORTED_WORK, null);
        }
        var activity = powered.activity();
        var work = new EnergyDemand.Work(activity.meanCombustionTorqueNewtonMetres(), activity.meanStarterTorqueNewtonMetres(),
                0.0, energy.combustionWorkJoules(), energy.starterWorkJoules(),
                energy.combustionWorkJoules() + energy.starterWorkJoules(), 0.0, 0.0);
        var range = powered.state().parkingLocked() ? EnergyDemand.Range.PARK
                : powered.state().mechanics().gear() == 0 ? EnergyDemand.Range.NEUTRAL
                : powered.state().mechanics().gear() < 0 ? EnergyDemand.Range.REVERSE : EnergyDemand.Range.FORWARD;
        var flags = new EnergyDemand.Flags(activity.runningSeconds() > 0.0,
                activity.meanCombustionTorqueNewtonMetres() == 0.0, environment.grounded(), hazard,
                driver.combustionEnergyPermitted() || driver.starterEnergyPermitted(), driver.starterEnergyPermitted(), false);
        var demand = new EnergyDemand(identity, serverTick, requestId, EnergyDemand.Source.FUEL, range,
                activity.meanEngineRpm(), activity.meanCombustionLoadFraction(), driver.throttle().value(), seconds,
                work, new EnergyDemand.BrakeIntent(driver.controlsPresent() && driver.physicalBrake() > 0.0,
                        driver.controlsPresent() && driver.appliedServiceBrakeFraction() > 0.0), flags);
        final EnergyParticipant.Preview preview;
        final EnergyPlan plan;
        try {
            preview = guard.preview(demand);
            plan = Objects.requireNonNull(ownerPrice.apply(preview), "owner price");
            if (!plan.demand().equals(demand) || plan.unit() != EnergyPlan.Unit.MILLILITRE) throw new IllegalStateException("owner priced another demand");
        } catch (RuntimeException | LinkageError unavailable) {
            // Contract violations/failed callbacks need deliberate owner reconciliation, not a legacy debit.
            uncertain = true;
            return coast(setup, initial, driver, environment, seconds, Status.COASTED_OWNER_UNCERTAIN, null);
        }
        if (plan.status() != EnergyPlan.Status.READY) {
            return coast(setup, initial, driver, environment, seconds, Status.COASTED_RESOURCE_DENIED, plan.status());
        }
        try {
            var authorization = guard.authorize(preview, plan);
            var settlement = new EnergyParticipant.Settlement(demand, work.shaftWorkJoules(),
                    energy.combustionWorkJoules(), energy.starterWorkJoules(), 0.0, 0.0, true);
            var receipt = guard.commit(authorization, settlement);
            return new Result(powered, Status.PAID, Optional.of(receipt), Optional.of(plan.status()));
        } catch (RuntimeException | LinkageError unavailable) {
            uncertain = true;
            return coast(setup, initial, driver, environment, seconds, Status.COASTED_OWNER_UNCERTAIN, null);
        }
    }

    private static Result coast(PowertrainSimulation.Setup setup, PowertrainSimulation.State initial,
                                PowertrainSimulation.DriverInput driver, PowertrainSimulation.Environment environment,
                                double seconds, Status status, EnergyPlan.Status decision) {
        var denied = new PowertrainSimulation.DriverInput(driver.requestedMode(), driver.drivingMode(), driver.throttle(), driver.clutch(),
                driver.shift(), driver.range(), driver.physicalBrake(), driver.appliedServiceBrakeFraction(),
                driver.ignitionOn(), driver.startHeld(), driver.controlsPresent(), false, false);
        var safe = PowertrainSimulation.advance(setup, initial, denied, environment, seconds);
        if (safe.activity().meanCombustionTorqueNewtonMetres() != 0.0 || safe.activity().meanStarterTorqueNewtonMetres() != 0.0
                || safe.energy().combustionWorkJoules() != 0.0 || safe.energy().starterWorkJoules() != 0.0) {
            throw new IllegalStateException("unpaid fallback still contains active engine torque");
        }
        return new Result(safe, status, Optional.empty(), Optional.ofNullable(decision));
    }
}
