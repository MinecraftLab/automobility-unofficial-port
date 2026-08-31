package io.github.milkucha.momentum;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import io.github.milkucha.momentum.config.MomentumConfig;

public final class MomentumCruiseControl {
    private static final double TO_KMH = 72.0;

    private static boolean active = false;
    private static int entityId = -1;
    private static final CruiseTargetState TARGET = new CruiseTargetState();
    private static float lastSpeedKmh = 0f;
    private static boolean accelerating = false;
    private static CancellationReason pendingCancellation = CancellationReason.NONE;

    private MomentumCruiseControl() {
    }

    public static ActionResult toggle(AutomobileEntity auto) {
        if (!MomentumConfig.gameplay().enabled || !MomentumConfig.gameplay().cruise.enabled) {
            cancel(CancellationReason.NONE);
            return result(Action.UNAVAILABLE);
        }

        if (active && entityId == auto.getId()) {
            cancel(CancellationReason.NONE);
            return result(Action.DISENGAGED);
        }

        if (entityId != -1 && entityId != auto.getId()) {
            clearSession();
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
        MomentumConfig config = MomentumConfig.gameplay();
        if (!config.enabled || !config.cruise.enabled || isReversing(auto)) {
            return result(Action.UNAVAILABLE);
        }
        if (active && entityId == auto.getId()) {
            return result(Action.ALREADY_ACTIVE);
        }
        if (!TARGET.hasStoredTarget() || entityId != auto.getId()) {
            if (entityId != -1 && entityId != auto.getId()) {
                clearSession();
            }
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
        if (!active || entityId != auto.getId()) {
            return result(Action.NOT_ACTIVE);
        }
        MomentumConfig config = MomentumConfig.gameplay();
        if (!config.enabled || !config.cruise.enabled) {
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
        if (!active) {
            return null;
        }
        if (!MomentumConfig.gameplay().enabled || !MomentumConfig.gameplay().cruise.enabled || entityId != auto.getId()) {
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
            TARGET.set(Math.max(TARGET.targetKmh(), speed), cruise.minActivationKmh, cruise.maxTargetKmh);
            accelerating = true;
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
        if (active && auto != null && entityId == auto.getId()
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
        active = false;
        entityId = -1;
        TARGET.clear();
        lastSpeedKmh = 0f;
        accelerating = false;
        pendingCancellation = CancellationReason.NONE;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isActiveFor(AutomobileEntity auto) {
        return active && auto != null && entityId == auto.getId();
    }

    public static float getTargetKmh() {
        return TARGET.targetKmh();
    }

    public static boolean isAccelerating() {
        return active && accelerating;
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

    private static void engage(AutomobileEntity auto, float target, float currentSpeed) {
        active = true;
        entityId = auto.getId();
        lastSpeedKmh = currentSpeed;
        MomentumConfig.Cruise cruise = MomentumConfig.gameplay().cruise;
        accelerating = currentSpeed <= target - cruise.resumeThrottleBelowTargetKmh;
        pendingCancellation = CancellationReason.NONE;
    }

    private static void cancel(CancellationReason reason) {
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
        CONFIG_CHANGED
    }

    public record ActionResult(Action action, float targetKmh, float minimumKmh) {
    }

    public record AdjustedInput(boolean fwd, boolean back, boolean left, boolean right, boolean space, boolean ctrl) {
    }
}
