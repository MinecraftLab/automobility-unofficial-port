package io.github.milkucha.momentum;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.milkucha.momentum.network.ClientPhysicsSession;

public final class MomentumCruiseControl {
    private static final double TO_KMH = 72.0;

    private static boolean active = false;
    private static final CruiseVehicleContext CONTEXT = new CruiseVehicleContext();
    private static final CruiseTargetState TARGET = new CruiseTargetState();
    private static float lastSpeedKmh = 0f;
    private static boolean accelerating = false;
    private static CancellationReason pendingCancellation = CancellationReason.NONE;

    private MomentumCruiseControl() {
    }

    public static ActionResult toggle(AutomobileEntity auto) {
        maintainSession(auto);
        if (cancelForStoppedEngine(auto)) return result(Action.UNAVAILABLE);
        if (!isEnabled()) {
            cancel(CancellationReason.NONE);
            return result(Action.UNAVAILABLE);
        }

        if (active && sameContext(auto)) {
            cancel(CancellationReason.NONE);
            return result(Action.DISENGAGED);
        }

        if (isReversing(auto)) {
            cancel(CancellationReason.NONE);
            return result(Action.UNAVAILABLE);
        }

        MomentumConfig.Cruise cruise = MomentumConfig.gameplay().cruise;
        float speed = currentKmh(auto);
        if (speed < cruise.minActivationKmh) {
            cancel(CancellationReason.NONE);
            return new ActionResult(Action.TOO_SLOW, TARGET.targetKmh(), cruise.minActivationKmh);
        }

        engage(auto, TARGET.set(speed, cruise.minActivationKmh, cruise.maxTargetKmh), speed);
        return result(Action.ENGAGED);
    }

    public static ActionResult resume(AutomobileEntity auto) {
        maintainSession(auto);
        if (cancelForStoppedEngine(auto)) return result(Action.UNAVAILABLE);
        MomentumConfig config = MomentumConfig.gameplay();
        if (!isEnabled() || isReversing(auto)) {
            return result(Action.UNAVAILABLE);
        }
        if (active && sameContext(auto)) {
            return result(Action.ALREADY_ACTIVE);
        }
        if (!TARGET.hasStoredTarget() || !sameContext(auto)) {
            return result(Action.NO_SAVED_TARGET);
        }

        float speed = currentKmh(auto);
        if (speed < config.cruise.minActivationKmh) {
            return new ActionResult(Action.TOO_SLOW, TARGET.targetKmh(), config.cruise.minActivationKmh);
        }

        float target = TARGET.set(TARGET.targetKmh(),
                config.cruise.minActivationKmh, config.cruise.maxTargetKmh);
        engage(auto, target, speed);
        return result(Action.RESUMED);
    }

    public static ActionResult adjustTarget(AutomobileEntity auto, int direction) {
        maintainSession(auto);
        if (cancelForStoppedEngine(auto)) return result(Action.UNAVAILABLE);
        if (!active || !sameContext(auto)) {
            return result(Action.NOT_ACTIVE);
        }
        MomentumConfig config = MomentumConfig.gameplay();
        if (!isEnabled()) {
            cancel(CancellationReason.CONFIG_CHANGED);
            return result(Action.UNAVAILABLE);
        }

        MomentumConfig.Cruise cruise = config.cruise;
        float target = TARGET.adjust(Math.signum(direction) * cruise.targetStepKmh,
                cruise.minActivationKmh, cruise.maxTargetKmh);
        float speed = currentKmh(auto);
        accelerating = speed <= target - cruise.resumeThrottleBelowTargetKmh;
        lastSpeedKmh = speed;
        return result(Action.ADJUSTED);
    }

    public static AdjustedInput adjustInput(AutomobileEntity auto, boolean fwd, boolean back, boolean left, boolean right, boolean space, boolean ctrl) {
        maintainSession(auto);
        if (cancelForStoppedEngine(auto)) return null;
        if (!active) {
            return null;
        }
        if (!isEnabled() || !sameContext(auto)) {
            cancel(CancellationReason.CONFIG_CHANGED);
            return null;
        }

        if (back || MomentumBrakeState.brakeHeld) {
            cancel(CancellationReason.BRAKE);
            return null;
        }
        if (space || MomentumDriftState.driftKeyHeld) {
            cancel(CancellationReason.DRIFT);
            return null;
        }
        if (isReversing(auto)) {
            cancel(CancellationReason.REVERSE);
            return null;
        }

        MomentumConfig.Cruise cruise = MomentumConfig.gameplay().cruise;
        float speed = currentKmh(auto);

        if (lastSpeedKmh >= cruise.impactCancelMinSpeedKmh
                && lastSpeedKmh - speed >= cruise.impactCancelDropKmh) {
            cancel(CancellationReason.IMPACT);
            return null;
        }

        if (fwd) {
            io.github.milkucha.momentum.network.CruiseMotorClient.manualForward();
            TARGET.set(Math.max(TARGET.targetKmh(), speed), cruise.minActivationKmh, cruise.maxTargetKmh);
            accelerating = true;
        } else if (io.github.milkucha.momentum.network.CruiseMotorClient.update(auto, TARGET.targetKmh())) {
            // A grant only caps ordinary paid acceleration. Overspeed still uses the original coast path.
            accelerating = speed <= TARGET.targetKmh() + 0.01f;
        } else if (accelerating) {
            if (speed >= TARGET.targetKmh() - cruise.cutThrottleBelowTargetKmh) {
                accelerating = false;
            }
        } else if (speed <= TARGET.targetKmh() - cruise.resumeThrottleBelowTargetKmh) {
            accelerating = true;
        }

        lastSpeedKmh = speed;
        return new AdjustedInput(accelerating, false, left, right, false, ctrl);
    }

    public static void cancelOnImpact(AutomobileEntity auto, float speedKmh) {
        if (active && auto != null && sameContext(auto)
                && speedKmh >= MomentumConfig.gameplay().cruise.impactCancelMinSpeedKmh) {
            cancel(CancellationReason.IMPACT);
        }
    }

    /** Cancels cruise but keeps the set speed available to Resume. */
    public static void deactivate() {
        cancel(CancellationReason.NONE);
    }

    /** Clears all state when the driver leaves or the client disconnects. */
    public static void clearSession() {
        io.github.milkucha.momentum.network.CruiseMotorClient.cancel();
        active = false;
        CONTEXT.clear();
        TARGET.clear();
        lastSpeedKmh = 0f;
        accelerating = false;
        pendingCancellation = CancellationReason.NONE;
    }

    /** Driver assistance is independent of Original/Extended; remote use still needs the exact handshake. */
    public static boolean isEnabled() {
        return ClientPhysicsSession.physicsAllowed(MomentumConfig.hasServerGameplayConfig())
                && MomentumConfig.gameplay().cruise.enabled;
    }

    /** Accepted mapped pedals cancel assistance even when Extended drift/brake effects are disabled. */
    public static void cancelOnDriverInput(boolean brake, boolean drift) {
        if (!active) return;
        if (brake) cancel(CancellationReason.BRAKE);
        else if (drift) cancel(CancellationReason.DRIFT);
    }

    public static boolean isActive() {
        return isEnabled() && active;
    }

    public static boolean isActiveFor(AutomobileEntity auto) {
        if (!isEnabled() || !sameContext(auto) || !auto.engineRunning()) return false;
        return active && sameContext(auto);
    }

    public static boolean canResumeFor(AutomobileEntity auto) {
        if (!isEnabled() || !sameContext(auto) || !auto.engineRunning()) return false;
        return !active && sameContext(auto) && TARGET.hasStoredTarget();
    }

    public static float getTargetKmh() {
        return TARGET.targetKmh();
    }

    public static boolean isAccelerating() {
        return isActive() && accelerating;
    }

    public static float currentKmh(AutomobileEntity auto) {
        return (float) (auto.getEffectiveSpeed() * TO_KMH);
    }

    private static boolean isReversing(AutomobileEntity auto) {
        return auto instanceof SteeringDebugAccessor accessor && accessor.momentum$getEngineSpeed() < -0.02f;
    }

    public static CancellationReason consumeCancellationReason() {
        CancellationReason reason = pendingCancellation;
        pendingCancellation = CancellationReason.NONE;
        return reason;
    }

    private static boolean sameContext(AutomobileEntity auto) {
        return auto != null && !auto.isRemoved() && auto.isDriving(auto.getFirstPassenger())
                && CONTEXT.matches(auto, auto.level(), auto.getFirstPassenger());
    }

    /** Only actual driver execution may invalidate a session; other-car HUD queries stay read-only. */
    public static void maintainSession(AutomobileEntity auto) {
        if (CONTEXT.differs(auto, auto == null ? null : auto.level(),
                auto == null ? null : auto.getFirstPassenger())) clearSession();
    }

    private static boolean cancelForStoppedEngine(AutomobileEntity auto) {
        if (auto != null && auto.engineRunning()) return false;
        if (auto == null || sameContext(auto)) {
            if (active) cancel(CancellationReason.ENGINE_STOPPED);
        }
        return true;
    }

    private static void engage(AutomobileEntity auto, float target, float currentSpeed) {
        active = true;
        CONTEXT.bind(auto, auto.level(), auto.getFirstPassenger());
        lastSpeedKmh = currentSpeed;
        MomentumConfig.Cruise cruise = MomentumConfig.gameplay().cruise;
        accelerating = currentSpeed <= target - cruise.resumeThrottleBelowTargetKmh;
        pendingCancellation = CancellationReason.NONE;
    }

    private static void cancel(CancellationReason reason) {
        io.github.milkucha.momentum.network.CruiseMotorClient.cancel();
        active = false;
        lastSpeedKmh = 0f;
        accelerating = false;
        pendingCancellation = reason;
    }

    private static ActionResult result(Action action) {
        return new ActionResult(action, TARGET.targetKmh(), MomentumConfig.gameplay().cruise.minActivationKmh);
    }

    public enum Action {
        ENGAGED,
        DISENGAGED,
        RESUMED,
        ADJUSTED,
        ALREADY_ACTIVE,
        TOO_SLOW,
        UNAVAILABLE,
        NOT_ACTIVE,
        NO_SAVED_TARGET
    }

    public enum CancellationReason {
        NONE,
        BRAKE,
        DRIFT,
        REVERSE,
        IMPACT,
        CONFIG_CHANGED,
        ENGINE_STOPPED
    }

    public record ActionResult(Action action, float targetKmh, float minimumKmh) {
    }

    public record AdjustedInput(boolean fwd, boolean back, boolean left, boolean right, boolean space, boolean ctrl) {
    }
}
