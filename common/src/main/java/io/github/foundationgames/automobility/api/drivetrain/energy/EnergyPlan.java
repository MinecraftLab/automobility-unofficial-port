package io.github.foundationgames.automobility.api.drivetrain.energy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/** A proposed owner-side exchange. READY is not authorization and never mutates storage. */
public record EnergyPlan(EnergyDemand demand, Unit unit, Status status, Costs costs,
                         double requestedDrawUnits, double acceptedDrawUnits, double regenerationCreditUnits,
                         long debitWholeUnits, long creditWholeUnits, BigDecimal nextFractionalDebitUnits,
                         double recommendedWorkScale) {
    public enum Unit { MILLILITRE, ELECTRICAL_UNIT }
    public enum Status { READY, INSUFFICIENT_REPREVIEW_REQUIRED, NO_PERMISSION, CAPABILITY_MISMATCH, UNSAFE_REQUEST }

    /** Fractional debt belongs to the actual owner and must be committed atomically with its whole units. */
    public record OwnerSnapshot(long availableWholeUnits, long capacityWholeUnits,
                                BigDecimal fractionalDebitUnits, long revision, boolean energyPermission) {
        public OwnerSnapshot {
            fractionalDebitUnits = Objects.requireNonNull(fractionalDebitUnits, "fractional debt").stripTrailingZeros();
            if (availableWholeUnits < 0 || capacityWholeUnits < availableWholeUnits || revision < 0
                    || fractionalDebitUnits.signum() < 0 || fractionalDebitUnits.compareTo(BigDecimal.ONE) >= 0
                    || (availableWholeUnits == 0 && fractionalDebitUnits.signum() != 0)) {
                throw new IllegalArgumentException("invalid owner snapshot");
            }
        }
        public OwnerSnapshot(long available, long capacity, double carry, long revision, boolean permitted) {
            this(available, capacity, BigDecimal.valueOf(EnergyDemand.nonnegative(carry, "fractional debt")), revision, permitted);
        }
        public BigDecimal availableExactUnits() { return BigDecimal.valueOf(availableWholeUnits).subtract(fractionalDebitUnits); }
    }

    /** Delivered work + conversion losses + overhead = draw; regen losses remain mechanical input minus stored output. */
    public record Costs(double deliveredMechanicalJoules, double conversionLossJoules, double overheadJoules,
                        double drawJoules, double acceptedRegenerationMechanicalJoules, double storedRegenerationJoules) {
        public Costs {
            for (double value : new double[]{deliveredMechanicalJoules, conversionLossJoules, overheadJoules,
                    drawJoules, acceptedRegenerationMechanicalJoules, storedRegenerationJoules}) EnergyDemand.nonnegative(value, "energy cost");
            if (!EnergyDemand.sameWork(drawJoules, deliveredMechanicalJoules + conversionLossJoules + overheadJoules)
                    || storedRegenerationJoules > acceptedRegenerationMechanicalJoules) {
                throw new IllegalArgumentException("inconsistent conversion energy");
            }
        }
        public double regenerationLossJoules() { return acceptedRegenerationMechanicalJoules - storedRegenerationJoules; }
        public static Costs zero() { return new Costs(0.0, 0.0, 0.0, 0.0, 0.0, 0.0); }
    }

    public EnergyPlan {
        Objects.requireNonNull(demand, "demand");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(costs, "costs");
        nextFractionalDebitUnits = Objects.requireNonNull(nextFractionalDebitUnits, "next fractional debt").stripTrailingZeros();
        EnergyDemand.nonnegative(requestedDrawUnits, "requested units");
        EnergyDemand.nonnegative(acceptedDrawUnits, "accepted units");
        EnergyDemand.nonnegative(regenerationCreditUnits, "regeneration units");
        EnergyDemand.between(recommendedWorkScale, 0.0, 1.0, "scale hint");
        if (debitWholeUnits < 0 || creditWholeUnits < 0 || (debitWholeUnits != 0 && creditWholeUnits != 0)
                || nextFractionalDebitUnits.signum() < 0 || nextFractionalDebitUnits.compareTo(BigDecimal.ONE) >= 0
                || acceptedDrawUnits > requestedDrawUnits
                || (status != Status.READY && (acceptedDrawUnits != 0.0 || regenerationCreditUnits != 0.0
                    || debitWholeUnits != 0 || creditWholeUnits != 0))) {
            throw new IllegalArgumentException("invalid energy plan");
        }
    }

    static EnergyPlan refused(EnergyDemand demand, Unit unit, Status status, OwnerSnapshot owner) {
        if (status == Status.READY) throw new IllegalArgumentException("refusal must not be READY");
        return new EnergyPlan(demand, unit, status, Costs.zero(), 0.0, 0.0, 0.0, 0L, 0L, owner.fractionalDebitUnits(), 0.0);
    }

    static EnergyPlan settle(EnergyDemand demand, Unit unit, OwnerSnapshot owner, double joulesPerUnit, Costs costs) {
        EnergyDemand.positive(joulesPerUnit, "joules per unit");
        double draw = EnergyDemand.nonnegative(costs.drawJoules() / joulesPerUnit, "draw units");
        if (costs.drawJoules() > 0.0 && draw == 0.0) throw new IllegalArgumentException("draw conversion underflow");
        if (!owner.energyPermission()) return refused(demand, unit, Status.NO_PERMISSION, owner);
        BigDecimal drawDecimal = BigDecimal.valueOf(draw);
        BigDecimal available = owner.availableExactUnits();
        // A predicted future regeneration credit cannot fund a currently unaffordable powered interval.
        if (drawDecimal.compareTo(available) > 0) {
            double hint = draw == 0.0 ? 0.0 : available.divide(drawDecimal, MathContext.DECIMAL64).doubleValue();
            return new EnergyPlan(demand, unit, Status.INSUFFICIENT_REPREVIEW_REQUIRED,
                    new Costs(costs.deliveredMechanicalJoules(), costs.conversionLossJoules(), costs.overheadJoules(), costs.drawJoules(), 0.0, 0.0),
                    draw, 0.0, 0.0, 0L, 0L, owner.fractionalDebitUnits(), Math.max(0.0, Math.min(1.0, hint)));
        }
        BigDecimal headroomAfterDraw = BigDecimal.valueOf(owner.capacityWholeUnits()).subtract(available).add(drawDecimal);
        BigDecimal creditDecimal = BigDecimal.valueOf(EnergyDemand.nonnegative(costs.storedRegenerationJoules() / joulesPerUnit, "regen units"))
                .min(headroomAfterDraw);
        double credit = creditDecimal.doubleValue();
        // Canonical public unit amounts must reproduce the exact whole/fraction exchange, without rounding over headroom.
        if (BigDecimal.valueOf(credit).compareTo(creditDecimal) > 0) credit = Math.nextDown(credit);
        creditDecimal = BigDecimal.valueOf(credit);
        double stored = EnergyDemand.finite(credit * joulesPerUnit, "stored regen joules");
        if (credit > 0.0 && stored == 0.0) throw new IllegalArgumentException("regeneration conversion underflow");
        double recovered = costs.storedRegenerationJoules() == 0.0 ? 0.0
                : costs.acceptedRegenerationMechanicalJoules() * (stored / costs.storedRegenerationJoules());
        recovered = Math.max(stored, recovered);
        Costs boundedCosts = new Costs(costs.deliveredMechanicalJoules(), costs.conversionLossJoules(), costs.overheadJoules(), costs.drawJoules(), recovered, stored);
        BigDecimal debt = owner.fractionalDebitUnits().add(drawDecimal).subtract(creditDecimal);
        BigDecimal wholeDebt = debt.setScale(0, RoundingMode.FLOOR);
        BigDecimal carry = debt.subtract(wholeDebt);
        long debit = wholeDebt.max(BigDecimal.ZERO).longValueExact();
        long credited = wholeDebt.negate().max(BigDecimal.ZERO).longValueExact();
        long nextWhole = Math.addExact(Math.subtractExact(owner.availableWholeUnits(), debit), credited);
        if (nextWhole < 0 || nextWhole > owner.capacityWholeUnits()) throw new IllegalArgumentException("exchange exceeds owner bounds");
        return new EnergyPlan(demand, unit, Status.READY, boundedCosts, draw, draw, credit, debit, credited, carry, 1.0);
    }
}
