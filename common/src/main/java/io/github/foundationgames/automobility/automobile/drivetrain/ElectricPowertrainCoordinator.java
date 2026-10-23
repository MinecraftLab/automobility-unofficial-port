package io.github.foundationgames.automobility.automobile.drivetrain;

import io.github.foundationgames.automobility.api.drivetrain.energy.*;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Immutable EV previews followed by the actual motor-battery owner's exchange; no world or storage writes. */
public final class ElectricPowertrainCoordinator {
    public static final int MAX_REGEN_SEARCH_STEPS = 16;
    public enum Status { PAID, PAID_REGEN_REDUCED, COASTED_RESOURCE_DENIED, COASTED_OWNER_UNCERTAIN }
    public record Result(EvDrivetrainStep.Result mechanics, Status status,
                         Optional<EnergyParticipant.Receipt> receipt, Optional<EnergyPlan.Status> ownerDecision) { }
    private record Candidate(EvDrivetrainStep.Result mechanics, EnergyDemand demand,
                             EnergyParticipant.Preview preview, EnergyPlan plan) { }

    private final EnergyDemand.Identity identity;
    private final EnergyTransactionGuard guard;
    private final Function<EnergyParticipant.Preview, EnergyPlan> ownerPrice;
    private long lastTick = -1;
    private boolean uncertain;

    public ElectricPowertrainCoordinator(EnergyDemand.Identity identity, EnergyTransactionGuard guard,
                                         Function<EnergyParticipant.Preview, EnergyPlan> ownerPrice) {
        this.identity = Objects.requireNonNull(identity); this.guard = Objects.requireNonNull(guard);
        this.ownerPrice = Objects.requireNonNull(ownerPrice);
        if (identity.physicalMotorId().isEmpty()) throw new IllegalArgumentException("electric coordinator needs the actual physical motor identity");
    }
    public boolean ownerReconciliationRequired() { return uncertain; }

    /**
     * Here requested.serviceBrakeAxleTorque is the complete decoded service-brake request BEFORE regeneration
     * blending. Kernel controls constructed below contain only the remaining friction brake. A raw reverse
     * pedal intent must therefore carry serviceBrake=0 and requestedRegen=0 when it means reverse propulsion.
     * Only physical applied braking is blended; automatic/active-only braking cannot manufacture recovery.
     * Coast regeneration gets no friction replacement. A returned powered result may be published only after its receipt.
     */
    public Result advance(EvDrivetrainStep.Parameters parameters, EvDrivetrainStep.State initial,
                          EvDrivetrainStep.Control requested, EvDrivetrainStep.Environment environment,
                          double seconds, long serverTick, UUID requestId) {
        Objects.requireNonNull(parameters); Objects.requireNonNull(initial); Objects.requireNonNull(requested);
        Objects.requireNonNull(environment); Objects.requireNonNull(requestId);
        if (serverTick < 0 || serverTick <= lastTick) throw new IllegalStateException("electric mechanical tick already consumed or reordered");
        lastTick = serverTick;
        if (uncertain) return coast(parameters, initial, requested, environment, seconds, Status.COASTED_OWNER_UNCERTAIN, null);

        try {
            double fullRegen = Math.min(requested.requestedRegenerationMotorTorqueNewtonMetres(), requested.authorizedRegenerationMotorTorqueLimitNewtonMetres());
            if (requested.physicalBrakeIntent() && requested.serviceBrakeAxleTorqueNewtonMetres() > 0.0) {
                fullRegen = Math.min(fullRegen, requested.serviceBrakeAxleTorqueNewtonMetres() / parameters.reductionRatio());
            }
            var initialCandidate = quote(parameters, initial, requested, environment, seconds, serverTick, requestId, fullRegen, false);
            boolean suppressDrive = initialCandidate.mechanics().regenerationEnabledSeconds() > 0.0;
            Candidate selected = initialCandidate;
            boolean reduced = false;
            if (!acceptedExtraction(selected)) {
                // Only regeneration is searched. Future credit never funds a powered interval, and positive traction
                // is not silently scaled after payment; unaffordable drive falls back to a new unpowered preview.
                if (selected.mechanics().recoveredMechanicalJoules() <= 0.0 || selected.mechanics().positiveDriveShaftWorkJoules() > 0.0) {
                    return coast(parameters, initial, requested, environment, seconds, Status.COASTED_RESOURCE_DENIED, selected.plan().status());
                }
                double lower = 0.0, upper = fullRegen;
                for (int i = 0; i < MAX_REGEN_SEARCH_STEPS; i++) {
                    double middle = lower + (upper - lower) * 0.5;
                    if (middle == lower || middle == upper) break;
                    Candidate trial = quote(parameters, initial, requested, environment, seconds, serverTick, requestId, middle, suppressDrive);
                    if (acceptedExtraction(trial)) lower = middle;
                    else upper = middle;
                }
                selected = quote(parameters, initial, requested, environment, seconds, serverTick, requestId, lower, suppressDrive);
                reduced = true;
            }
            if (!acceptedExtraction(selected)) {
                return coast(parameters, initial, requested, environment, seconds, Status.COASTED_RESOURCE_DENIED, selected.plan().status());
            }
            var authorization = guard.authorize(selected.preview(), selected.plan());
            var mechanics = selected.mechanics();
            var settlement = new EnergyParticipant.Settlement(selected.demand(), mechanics.motorShaftWorkJoules(), 0.0, 0.0,
                    mechanics.positiveDriveShaftWorkJoules(), mechanics.motorTorqueRmsNewtonMetres(), true);
            var receipt = guard.commit(authorization, settlement);
            return new Result(mechanics, reduced ? Status.PAID_REGEN_REDUCED : Status.PAID, Optional.of(receipt), Optional.of(selected.plan().status()));
        } catch (RuntimeException | LinkageError failure) {
            // The owner may have received authorize/commit. Never retry an ambiguous callback or use legacy power.
            uncertain = true;
            return coast(parameters, initial, requested, environment, seconds, Status.COASTED_OWNER_UNCERTAIN, null);
        }
    }

    private Candidate quote(EvDrivetrainStep.Parameters parameters, EvDrivetrainStep.State initial,
                            EvDrivetrainStep.Control requested, EvDrivetrainStep.Environment environment,
                            double seconds, long tick, UUID requestId, double regenLimit, boolean suppressDrive) {
        var mechanics = simulate(parameters, initial, requested, environment, seconds, regenLimit, suppressDrive);
        var work = new EnergyDemand.Work(0.0, 0.0, mechanics.motorTorqueRmsNewtonMetres(), 0.0, 0.0,
                mechanics.motorShaftWorkJoules(), mechanics.recoveredMechanicalJoules(), mechanics.positiveDriveShaftWorkJoules());
        var range = switch (mechanics.state().range()) {
            case D -> EnergyDemand.Range.FORWARD; case R -> EnergyDemand.Range.REVERSE;
            case N -> EnergyDemand.Range.NEUTRAL; case P -> EnergyDemand.Range.PARK;
        };
        var demand = new EnergyDemand(identity, tick, requestId, EnergyDemand.Source.ELECTRIC, range,
                mechanics.state().motorRpm(parameters), measuredLoad(parameters.motor(), mechanics.motorTorqueRmsNewtonMetres()), requested.throttle(), seconds, work,
                new EnergyDemand.BrakeIntent(requested.physicalBrakeIntent(), requested.serviceBrakeAxleTorqueNewtonMetres() > 0.0),
                new EnergyDemand.Flags(false, false, environment.grounded(), requested.hazard(), requested.motorPowerPermitted(), false,
                        requested.regenerationPermitted()));
        var preview = guard.preview(demand);
        var plan = Objects.requireNonNull(ownerPrice.apply(preview), "owner electrical price");
        if (!plan.demand().equals(demand) || plan.unit() != EnergyPlan.Unit.ELECTRICAL_UNIT) throw new IllegalStateException("owner priced another electrical demand");
        return new Candidate(mechanics, demand, preview, plan);
    }

    private static boolean acceptedExtraction(Candidate candidate) {
        return candidate.plan().status() == EnergyPlan.Status.READY
                && same(candidate.plan().costs().acceptedRegenerationMechanicalJoules(), candidate.mechanics().recoveredMechanicalJoules());
    }

    private static EvDrivetrainStep.Result simulate(EvDrivetrainStep.Parameters parameters, EvDrivetrainStep.State initial,
                                                    EvDrivetrainStep.Control requested, EvDrivetrainStep.Environment environment,
                                                    double seconds, double regenLimit, boolean suppressDrive) {
        // This eligibility probe has no side effects. It measures the actual available extraction torque, including
        // range, zero-speed, curve and hazard gates; its dynamics are discarded before the real blended preview.
        var probeControl = control(requested, requested.serviceBrakeAxleTorqueNewtonMetres(), regenLimit, suppressDrive, false);
        var probe = EvDrivetrainStep.advance(parameters, initial, new EvDrivetrainStep.Input(probeControl, environment), seconds);
        boolean physicalBlend = requested.physicalBrakeIntent() && requested.serviceBrakeAxleTorqueNewtonMetres() > 0.0;
        if (!physicalBlend || probe.regenerationEnabledSeconds() == 0.0) return probe;
        double actualRegenNm = Math.abs(probe.meanRegenerationMotorTorqueNewtonMetres()) * seconds / probe.regenerationEnabledSeconds();
        double friction = Math.max(0.0, requested.serviceBrakeAxleTorqueNewtonMetres() - parameters.reductionRatio() * actualRegenNm);
        var blendedControl = control(requested, friction, regenLimit, suppressDrive, false);
        var blended = EvDrivetrainStep.advance(parameters, initial, new EvDrivetrainStep.Input(blendedControl, environment), seconds);
        if (!blended.regenerationCutAtZero() || !(blended.regenerationEnabledSeconds() > 0.0)
                || !(blended.regenerationEnabledSeconds() < seconds)) return blended;
        double firstSeconds = blended.regenerationEnabledSeconds(), secondSeconds = seconds - firstSeconds;
        var first = EvDrivetrainStep.advance(parameters, initial, new EvDrivetrainStep.Input(blendedControl, environment), firstSeconds);
        var remainderControl = control(requested.withoutRangeRequest(), requested.serviceBrakeAxleTorqueNewtonMetres(), 0.0, true, true);
        var second = EvDrivetrainStep.advance(parameters, first.state(), new EvDrivetrainStep.Input(remainderControl, environment), secondSeconds);
        return combine(parameters, first, firstSeconds, second, secondSeconds);
    }

    private static EvDrivetrainStep.Control control(EvDrivetrainStep.Control request, double serviceBrake, double regenLimit,
                                                    boolean suppressDrive, boolean denyRegen) {
        return new EvDrivetrainStep.Control(request.requestedRange(), request.throttle(), denyRegen ? 0.0 : regenLimit,
                request.authorizedDriveMotorTorqueLimitNewtonMetres(), request.authorizedRegenerationMotorTorqueLimitNewtonMetres(),
                serviceBrake, request.physicalBrakeFraction(), request.physicalBrakeIntent(), request.motorPowerPermitted() && !suppressDrive,
                request.regenerationPermitted() && !denyRegen, request.coastRegenerationConfigured(), request.hazard());
    }

    private static EvDrivetrainStep.Result combine(EvDrivetrainStep.Parameters parameters, EvDrivetrainStep.Result a, double ta,
                                                   EvDrivetrainStep.Result b, double tb) {
        double seconds = ta + tb;
        double rms = Math.sqrt((a.motorTorqueRmsNewtonMetres() * a.motorTorqueRmsNewtonMetres() * ta
                + b.motorTorqueRmsNewtonMetres() * b.motorTorqueRmsNewtonMetres() * tb) / seconds);
        double regenImpulse = a.regenerationImpulseNewtonMetreSeconds() + b.regenerationImpulseNewtonMetreSeconds();
        return new EvDrivetrainStep.Result(b.state(), a.refusal(), a.initialKineticEnergyJoules(), b.finalKineticEnergyJoules(),
                a.driveShaftWorkJoules() + b.driveShaftWorkJoules(), a.regenerationShaftWorkJoules() + b.regenerationShaftWorkJoules(),
                a.longitudinalWorkJoules() + b.longitudinalWorkJoules(), a.positiveDriveShaftWorkJoules() + b.positiveDriveShaftWorkJoules(),
                a.absorbedDriveShaftWorkJoules() + b.absorbedDriveShaftWorkJoules(),
                a.serviceAndParkingBrakeEnergyJoules() + b.serviceAndParkingBrakeEnergyJoules(), a.tyreSlipEnergyJoules() + b.tyreSlipEnergyJoules(),
                a.rollingResistanceEnergyJoules() + b.rollingResistanceEnergyJoules(), a.passiveMotorDragEnergyJoules() + b.passiveMotorDragEnergyJoules(),
                0.0, -regenImpulse / parameters.reductionRatio() / seconds, rms,
                a.regenerationEnabledSeconds() + b.regenerationEnabledSeconds(), a.regenerationCutAtZero() || b.regenerationCutAtZero(),
                a.serviceBrakeImpulseNewtonMetreSeconds() + b.serviceBrakeImpulseNewtonMetreSeconds(), a.tyreImpulseNewtonSeconds() + b.tyreImpulseNewtonSeconds(),
                a.rollingImpulseNewtonSeconds() + b.rollingImpulseNewtonSeconds(), a.passiveDragImpulseNewtonMetreSeconds() + b.passiveDragImpulseNewtonMetreSeconds(),
                regenImpulse, a.solverSegments() + b.solverSegments(), b.physicalBrakeFraction(), b.physicalBrakeIntent(), b.motorOverspeed());
    }

    private static Result coast(EvDrivetrainStep.Parameters parameters, EvDrivetrainStep.State initial,
                                 EvDrivetrainStep.Control requested, EvDrivetrainStep.Environment environment,
                                 double seconds, Status status, EnergyPlan.Status decision) {
        var denied = control(requested, requested.serviceBrakeAxleTorqueNewtonMetres(), 0.0, true, true);
        var mechanics = EvDrivetrainStep.advance(parameters, initial, new EvDrivetrainStep.Input(denied, environment), seconds);
        if (mechanics.motorTorqueRmsNewtonMetres() != 0.0 || mechanics.motorShaftWorkJoules() != 0.0
                || mechanics.positiveDriveShaftWorkJoules() != 0.0 || mechanics.recoveredMechanicalJoules() != 0.0) {
            throw new IllegalStateException("unpaid EV fallback contains active motor work/current");
        }
        return new Result(mechanics, status, Optional.empty(), Optional.ofNullable(decision));
    }
    private static boolean same(double a, double b) { return Double.isFinite(a) && Double.isFinite(b)
            && Math.abs(a - b) <= 1e-10 * Math.max(1.0, Math.max(Math.abs(a), Math.abs(b))); }
    private static double measuredLoad(EvMotorProfile profile, double rms) {
        double peak = Math.max(profile.motoringNewtonMetres().statistics().torqueNewtonMetres().value(),
                profile.regenerationNewtonMetres().statistics().torqueNewtonMetres().value());
        return peak == 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, rms / peak));
    }
}
