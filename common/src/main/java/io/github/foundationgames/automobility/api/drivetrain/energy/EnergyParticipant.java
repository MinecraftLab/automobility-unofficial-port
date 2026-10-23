package io.github.foundationgames.automobility.api.drivetrain.energy;

import java.util.Objects;
import java.util.UUID;

/**
 * Implemented by the actual P&P/Powerworks/other storage owner, not Automobility's mechanical core.
 * preview is read-only; authorize reserves against the current identity/revision and must not deliver power.
 * The owner must recompute/validate density, efficiencies, losses and exchange price using its own trusted
 * calibration; receiving a mechanically feasible Core plan does not authorize its proposed price or storage.
 * commit must atomically validate the reservation, actual work, whole units, fractional debt and durable
 * idempotency receipt in that owner's real storage. The runtime guard cannot provide Minecraft atomicity,
 * crash recovery or persistent deduplication. cancel releases only that owner's reservation.
 * Settlement may describe an immutable mechanics preview: publish/apply actual vehicle state only after a
 * successful owner receipt. An ambiguous callback outcome requires owner reconciliation; never retry it or
 * publish free power. The final game adapter must implement that ordering and its cross-system failure policy.
 */
public interface EnergyParticipant {
    Capability capability();
    Preview preview(EnergyDemand demand);
    Authorization authorize(Preview preview, EnergyPlan plan);
    Receipt commit(Authorization authorization, Settlement settlement);
    void cancel(Authorization authorization);

    record Capability(UUID ownerId, EnergyDemand.Source source, EnergyPlan.Unit unit,
                      boolean starterSupported, boolean regenerationSupported, boolean coastRegenerationSupported) {
        public Capability {
            Objects.requireNonNull(ownerId, "owner id");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(unit, "unit");
            if ((source == EnergyDemand.Source.FUEL) != (unit == EnergyPlan.Unit.MILLILITRE)
                    || (coastRegenerationSupported && !regenerationSupported)
                    || (source != EnergyDemand.Source.ELECTRIC && regenerationSupported)) {
                throw new IllegalArgumentException("inconsistent source capability");
            }
        }
    }
    record Preview(Capability capability, EnergyDemand demand, EnergyPlan.OwnerSnapshot owner) {
        public Preview {
            Objects.requireNonNull(capability, "capability");
            Objects.requireNonNull(demand, "demand");
            Objects.requireNonNull(owner, "owner snapshot");
            if (capability.source() != demand.source()) throw new IllegalArgumentException("wrong source preview");
        }
    }
    record Authorization(UUID authorizationId, Capability capability, EnergyPlan plan, long ownerRevision) {
        public Authorization {
            Objects.requireNonNull(authorizationId, "authorization id");
            Objects.requireNonNull(capability, "capability");
            Objects.requireNonNull(plan, "plan");
            if (ownerRevision < 0 || plan.status() != EnergyPlan.Status.READY) throw new IllegalArgumentException("not an authorizable plan");
        }
    }
    /**
     * Actual immutable mechanics result. A predicted negative work value alone is not a recovery receipt.
     * EV adapters MUST pass measured positive electric work and measured RMS torque to the full constructor.
     * Compatibility constructors derive missing data from net work/planned RMS and cannot prove a changed current history.
     */
    record Settlement(EnergyDemand demand, double actualShaftWorkJoules, double actualCombustionWorkJoules,
                      double actualStarterWorkJoules, double actualElectricDeliveredMechanicalJoules,
                      double actualElectricTorqueRmsNewtonMetres, boolean mechanicalStepSucceeded) {
        public Settlement {
            Objects.requireNonNull(demand, "demand");
            EnergyDemand.finite(actualShaftWorkJoules, "actual shaft work");
            EnergyDemand.nonnegative(actualCombustionWorkJoules, "actual combustion work");
            EnergyDemand.nonnegative(actualStarterWorkJoules, "actual starter work");
            EnergyDemand.nonnegative(actualElectricDeliveredMechanicalJoules, "actual positive electric work");
            EnergyDemand.nonnegative(actualElectricTorqueRmsNewtonMetres, "actual electric RMS torque");
        }
        public Settlement(EnergyDemand demand, double actualShaftWorkJoules, double actualCombustionWorkJoules,
                          double actualStarterWorkJoules, double actualElectricDeliveredMechanicalJoules, boolean mechanicalStepSucceeded) {
            this(demand, actualShaftWorkJoules, actualCombustionWorkJoules, actualStarterWorkJoules,
                    actualElectricDeliveredMechanicalJoules, demand.work().electricTorqueRmsNewtonMetres(), mechanicalStepSucceeded);
        }
        public Settlement(EnergyDemand demand, double actualShaftWorkJoules, double actualCombustionWorkJoules,
                          double actualStarterWorkJoules, boolean mechanicalStepSucceeded) {
            this(demand, actualShaftWorkJoules, actualCombustionWorkJoules, actualStarterWorkJoules,
                    EnergyDemand.legacyElectricDeliveredWork(actualShaftWorkJoules, actualCombustionWorkJoules, actualStarterWorkJoules),
                    demand.work().electricTorqueRmsNewtonMetres(), mechanicalStepSucceeded);
        }
    }
    record Receipt(UUID receiptId, UUID authorizationId, Capability capability, EnergyPlan plan, long ownerRevision) {
        public Receipt {
            Objects.requireNonNull(receiptId, "receipt id");
            Objects.requireNonNull(authorizationId, "authorization id");
            Objects.requireNonNull(capability, "capability");
            Objects.requireNonNull(plan, "plan");
            if (ownerRevision < 0) throw new IllegalArgumentException("negative owner revision");
        }
    }
}
