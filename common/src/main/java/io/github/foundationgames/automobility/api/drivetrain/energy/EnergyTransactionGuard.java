package io.github.foundationgames.automobility.api.drivetrain.energy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Ephemeral identity/idempotency bookkeeping only. It owns no fuel, battery or fractional energy account. */
public final class EnergyTransactionGuard {
    private record Slot(UUID vehicle, EnergyDemand.Source source) { }

    /** Keep one ledger for the actual world runtime; reuse bindings rather than constructing one per tick. */
    public static final class RuntimeLedger {
        private final UUID worldSessionId;
        private final UUID runtimeId;
        private final Map<Slot, EnergyTransactionGuard> bindings = new HashMap<>();
        private boolean callbackInFlight;

        public RuntimeLedger(UUID worldSessionId, UUID runtimeId) {
            this.worldSessionId = Objects.requireNonNull(worldSessionId, "world session");
            this.runtimeId = Objects.requireNonNull(runtimeId, "runtime");
        }
        public synchronized EnergyTransactionGuard bind(EnergyDemand.Identity identity, EnergyParticipant participant) {
            checkIdentity(identity);
            if (callbackInFlight) throw new IllegalStateException("reentrant owner callback");
            Objects.requireNonNull(participant, "participant");
            EnergyParticipant.Capability capability = Objects.requireNonNull(participant.capability(), "capability");
            Slot slot = new Slot(identity.vehicleId(), capability.source());
            if (bindings.containsKey(slot)) throw new IllegalStateException("vehicle/source already has an owner binding");
            var binding = new EnergyTransactionGuard(this, identity, participant, capability, -1L);
            bindings.put(slot, binding);
            return binding;
        }
        /** Swap invalidates old physical-motor requests and preserves tick high-water; it never moves energy. */
        public synchronized EnergyTransactionGuard replaceMotor(EnergyDemand.Identity identity, EnergyParticipant participant) {
            checkIdentity(identity);
            if (callbackInFlight) throw new IllegalStateException("reentrant owner callback");
            Objects.requireNonNull(participant, "participant");
            EnergyParticipant.Capability capability = Objects.requireNonNull(participant.capability(), "capability");
            Slot slot = new Slot(identity.vehicleId(), capability.source());
            EnergyTransactionGuard old = bindings.get(slot);
            if (old == null) throw new IllegalStateException("no binding to replace");
            if (!old.capability.equals(capability)) throw new IllegalStateException("replacement cannot change energy owner/capability");
            if (old.outstanding != null || old.poisoned) throw new IllegalStateException("unsettled owner transaction");
            if (old.identity.physicalMotorId().equals(identity.physicalMotorId())) throw new IllegalArgumentException("physical motor did not change");
            old.invalidated = true;
            var replacement = new EnergyTransactionGuard(this, identity, participant, capability, Math.max(old.lastClosedTick, old.highestTick));
            bindings.put(slot, replacement);
            return replacement;
        }
        private void checkIdentity(EnergyDemand.Identity identity) {
            Objects.requireNonNull(identity, "identity");
            if (!worldSessionId.equals(identity.worldSessionId()) || !runtimeId.equals(identity.runtimeId())) {
                throw new IllegalArgumentException("identity belongs to another world runtime");
            }
        }
    }

    private final RuntimeLedger ledger;
    private final EnergyDemand.Identity identity;
    private final EnergyParticipant participant;
    private final EnergyParticipant.Capability capability;
    private long lastClosedTick;
    private long highestTick;
    private EnergyParticipant.Preview latestPreview;
    private EnergyParticipant.Authorization outstanding;
    private boolean invalidated;
    private boolean poisoned;

    private EnergyTransactionGuard(RuntimeLedger ledger, EnergyDemand.Identity identity, EnergyParticipant participant,
                                   EnergyParticipant.Capability capability, long highWater) {
        this.ledger = ledger;
        this.identity = identity;
        this.participant = participant;
        this.capability = capability;
        this.lastClosedTick = highWater;
        this.highestTick = highWater;
        if (capability.source() == EnergyDemand.Source.ELECTRIC && identity.physicalMotorId().isEmpty()) {
            throw new IllegalArgumentException("electric owner requires a physical motor");
        }
    }

    public EnergyParticipant.Preview preview(EnergyDemand demand) {
        synchronized (ledger) {
            checkDemand(demand);
            if (outstanding != null) throw new IllegalStateException("authorization already outstanding");
            highestTick = Math.max(highestTick, demand.tick());
            ledger.callbackInFlight = true;
            try {
                var preview = Objects.requireNonNull(participant.preview(demand), "owner preview");
                if (!capability.equals(preview.capability()) || !demand.equals(preview.demand())) throw new IllegalStateException("owner returned a different preview");
                latestPreview = preview;
                return preview;
            } finally { ledger.callbackInFlight = false; }
        }
    }

    public EnergyParticipant.Authorization authorize(EnergyParticipant.Preview preview, EnergyPlan plan) {
        synchronized (ledger) {
            Objects.requireNonNull(preview, "preview");
            Objects.requireNonNull(plan, "plan");
            checkDemand(plan.demand());
            if (outstanding != null || latestPreview == null || !latestPreview.equals(preview)
                    || !preview.demand().equals(plan.demand()) || plan.unit() != capability.unit()) {
                throw new IllegalStateException("stale or mismatched preview");
            }
            verifyBudget(preview.owner(), plan);
            verifySafety(plan);
            ledger.callbackInFlight = true;
            poisoned = true; // Any ambiguous callback outcome must be reconciled by the owner, never retried here.
            try {
                var authorization = Objects.requireNonNull(participant.authorize(preview, plan), "owner authorization");
                if (!capability.equals(authorization.capability()) || !plan.equals(authorization.plan())
                        || authorization.ownerRevision() != preview.owner().revision()) throw new IllegalStateException("owner returned a different authorization");
                outstanding = authorization;
                latestPreview = null;
                poisoned = false;
                return authorization;
            } finally { ledger.callbackInFlight = false; }
        }
    }

    public EnergyParticipant.Receipt commit(EnergyParticipant.Authorization authorization, EnergyParticipant.Settlement settlement) {
        synchronized (ledger) {
            checkAuthorization(authorization);
            Objects.requireNonNull(settlement, "settlement");
            EnergyDemand demand = authorization.plan().demand();
            if (!settlement.mechanicalStepSucceeded() || !demand.equals(settlement.demand())
                    || !EnergyDemand.sameWork(demand.work().shaftWorkJoules(), settlement.actualShaftWorkJoules())
                    || !EnergyDemand.sameWork(demand.work().combustionWorkJoules(), settlement.actualCombustionWorkJoules())
                    || !EnergyDemand.sameWork(demand.work().starterWorkJoules(), settlement.actualStarterWorkJoules())
                    || !EnergyDemand.sameWork(demand.work().electricDeliveredMechanicalJoules(), settlement.actualElectricDeliveredMechanicalJoules())
                    || !EnergyDemand.sameWork(demand.work().electricTorqueRmsNewtonMetres(), settlement.actualElectricTorqueRmsNewtonMetres())
                    || authorization.plan().costs().acceptedRegenerationMechanicalJoules() > Math.max(0.0, -settlement.actualShaftWorkJoules())) {
                throw new IllegalStateException("actual mechanics requires a new preview, not this exchange");
            }
            closeTick(demand.tick());
            ledger.callbackInFlight = true;
            poisoned = true;
            try {
                var receipt = Objects.requireNonNull(participant.commit(authorization, settlement), "owner receipt");
                if (!authorization.authorizationId().equals(receipt.authorizationId()) || !capability.equals(receipt.capability())
                        || !authorization.plan().equals(receipt.plan()) || receipt.ownerRevision() <= authorization.ownerRevision()) {
                    throw new IllegalStateException("owner returned a different or stale receipt");
                }
                poisoned = false;
                return receipt;
            } finally { ledger.callbackInFlight = false; }
        }
    }

    public void cancel(EnergyParticipant.Authorization authorization) {
        synchronized (ledger) {
            checkAuthorization(authorization);
            closeTick(authorization.plan().demand().tick());
            ledger.callbackInFlight = true;
            poisoned = true;
            try { participant.cancel(authorization); poisoned = false; }
            finally { ledger.callbackInFlight = false; }
        }
    }

    private void closeTick(long tick) { lastClosedTick = tick; latestPreview = null; outstanding = null; }
    private void checkAuthorization(EnergyParticipant.Authorization authorization) {
        Objects.requireNonNull(authorization, "authorization");
        checkDemand(authorization.plan().demand());
        if (outstanding == null || !outstanding.equals(authorization)) throw new IllegalStateException("unknown or already consumed authorization");
    }
    private void checkDemand(EnergyDemand demand) {
        Objects.requireNonNull(demand, "demand");
        if (invalidated || poisoned || ledger.callbackInFlight) throw new IllegalStateException("binding unavailable");
        if (!identity.equals(demand.identity()) || capability.source() != demand.source()) throw new IllegalArgumentException("different vehicle/motor/runtime/source");
        if (demand.tick() <= lastClosedTick || demand.tick() < highestTick) throw new IllegalStateException("replayed or stale tick");
    }

    private static void verifyBudget(EnergyPlan.OwnerSnapshot owner, EnergyPlan plan) {
        if (plan.status() != EnergyPlan.Status.READY || !owner.energyPermission() || plan.acceptedDrawUnits() != plan.requestedDrawUnits()) {
            throw new IllegalStateException("plan is not fully affordable and permitted");
        }
        BigDecimal draw = BigDecimal.valueOf(plan.acceptedDrawUnits());
        BigDecimal credit = BigDecimal.valueOf(plan.regenerationCreditUnits());
        if (draw.compareTo(owner.availableExactUnits()) > 0
                || credit.compareTo(BigDecimal.valueOf(owner.capacityWholeUnits()).subtract(owner.availableExactUnits()).add(draw)) > 0) {
            throw new IllegalStateException("plan exceeds current owner budget");
        }
        BigDecimal debt = owner.fractionalDebitUnits().add(draw).subtract(credit);
        BigDecimal whole = debt.setScale(0, RoundingMode.FLOOR);
        if (plan.debitWholeUnits() != whole.max(BigDecimal.ZERO).longValueExact()
                || plan.creditWholeUnits() != whole.negate().max(BigDecimal.ZERO).longValueExact()
                || plan.nextFractionalDebitUnits().compareTo(debt.subtract(whole)) != 0) {
            throw new IllegalStateException("unit/fraction exchange was altered");
        }
    }

    private void verifySafety(EnergyPlan plan) {
        var demand = plan.demand();
        boolean starter = demand.work().starterTorqueNewtonMetres() > 0.0 || demand.work().starterWorkJoules() > 0.0;
        boolean motoringTorque = demand.work().combustionTorqueNewtonMetres() > 0.0 || starter
                || demand.work().electricDeliveredMechanicalJoules() > 0.0
                || (demand.work().electricTorqueNewtonMetres() != 0.0 && demand.work().shaftWorkJoules() >= 0.0);
        double delivered = demand.source() == EnergyDemand.Source.ELECTRIC ? demand.work().electricDeliveredMechanicalJoules()
                : demand.work().combustionWorkJoules() + demand.work().starterWorkJoules();
        if (((plan.costs().drawJoules() > 0.0 || motoringTorque) && !demand.flags().activePowerPermitted())
                || (starter && (!capability.starterSupported() || !demand.flags().starterPermitted()))
                || !EnergyDemand.sameWork(plan.costs().deliveredMechanicalJoules(), delivered)) {
            throw new IllegalStateException("unsafe active energy request");
        }
        // The demand describes actual extraction in an immutable mechanics preview, not merely an available capability.
        // A bounded-price feedback plan cannot authorize old braking that needs an unmodelled electrical dump load.
        if (!EnergyDemand.sameWork(plan.costs().acceptedRegenerationMechanicalJoules(), demand.work().regenerationMechanicalJoules())) {
            throw new IllegalStateException("regeneration requires a matching mechanical re-preview");
        }
        if (plan.regenerationCreditUnits() > 0.0 && (!capability.regenerationSupported() || !demand.flags().regenerationPermitted()
                || demand.work().shaftWorkJoules() >= 0.0 || demand.work().regenerationMechanicalJoules() == 0.0
                || plan.costs().storedRegenerationJoules() == 0.0 || plan.costs().acceptedRegenerationMechanicalJoules() == 0.0
                || !demand.flags().grounded() || demand.flags().hazard()
                || demand.range() == EnergyDemand.Range.NEUTRAL || demand.range() == EnergyDemand.Range.PARK
                || !(demand.brake().physicalBrake() || (capability.coastRegenerationSupported() && demand.throttleFraction() == 0.0))
                || plan.costs().acceptedRegenerationMechanicalJoules() > demand.work().regenerationMechanicalJoules())) {
            throw new IllegalStateException("unsafe regeneration credit");
        }
    }
}
