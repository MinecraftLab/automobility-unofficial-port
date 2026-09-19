package io.github.milkucha.momentum.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.util.AUtils;
import io.github.foundationgames.automobility.util.network.ClientPackets;
import io.github.milkucha.momentum.MomentumBrakeState;
import io.github.milkucha.momentum.MomentumCruiseControl;
import io.github.milkucha.momentum.MomentumDriftState;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.milkucha.momentum.network.ServerKeyState;
import io.github.milkucha.momentum.network.VehicleInputPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AutomobileEntityMixin - corrected movement feel for Automobility.
 *
 * 1. COASTING FIX
 *    Automobility's movementTick() contains:
 *        this.engineSpeed = AUtils.zero(this.engineSpeed, 0.025f);
 *    which stops the car in ~2 ticks from full speed.
 *
 *    We use @Redirect to replace that specific AUtils.zero call with one using
 *    coastDecay from config. This intercepts it *before* hSpeed is computed from
 *    engineSpeed, so the slower decay takes effect on the current tick's movement.
 *
 *    There are two AUtils.zero calls in movementTick - ordinal 0 is for boostSpeed,
 *    ordinal 1 is the engineSpeed coast decay we want to replace.
 *
 * 2. ACCELERATION SCALE
 *    @ModifyArg on the calculateAcceleration() call site scales the speed input,
 *    which indirectly scales the acceleration output since speed is in the denominator.
 *
 * 3. STEERING RAMP RATE
 *    Automobility's steeringTick() uses a hardcoded 0.42f constant for both
 *    ramping toward full lock and returning to centre. This reaches full lock in
 *    ~2.4 ticks - instant and horse-like.
 *    @ModifyConstant replaces every occurrence of 0.42f in steeringTick with the
 *    configurable steeringRampRate. Skipped during a drift so drift steering stays snappy.
 *
 * 4. SPEED-BASED UNDERSTEER
 *    postMovementTick() drives angularSpeed via:
 *        newAngularSpeed = AUtils.shift(newAngularSpeed, 6*traction, steering_target)
 *        this.angularSpeed = grip*newAngularSpeed + (1-grip)*angularSpeed
 *        yawInc = angularSpeed; setYRot(yaw + yawInc);   ← yaw applied here
 *    Scaling angularSpeed at TAIL (after the yaw update) has almost no real effect:
 *    the grip lerp quickly restores it from target, giving a hard floor at grip×target.
 *    Instead, @ModifyArg scales the `to` argument of AUtils.shift (ordinal 1, index 2)
 *    in postMovementTick, so angularSpeed genuinely converges to target/scale, no grip floor.
 *    Skipped during a drift so the drift arc is unaffected.
 */
@Mixin(value = AutomobileEntity.class, remap = false)
public abstract class AutomobileEntityMixin implements SteeringDebugAccessor {

    @Shadow private boolean drifting;
    @Shadow public AutomobileEntity.Input input;
    @Shadow private float engineSpeed;
    @Shadow private float hSpeed;
    @Shadow private float steering;
    @Shadow private float angularSpeed;
    @Shadow private boolean touchingWall;
    @Shadow private int hadVehicleCollision;
    @Shadow private int driftDir;
    @Shadow private int turboCharge;
    @Shadow public abstract boolean automobileOnGround();
    @Shadow private void setDrifting(boolean drifting) {}
    @Shadow private void consumeTurboCharge() {}
    @Shadow public void createDriftParticles() {}
    @Shadow public void boost(float power, int time) {}

    // Per-entity previous drift key state for rising/falling edge detection.
    // Instance field (not static) so client and integrated-server entities
    // each track their own edge independently.
    @Unique private boolean momentum$prevDriftKeyHeld = false;

    // ── Arcade Drift state ────────────────────────────────────────────────
    @Unique private boolean momentum$prevArcadeDriftKeyHeld = false;
    @Unique private boolean momentum$arcadeDriftActive = false;
    @Unique private float   momentum$arcadeDriftOffset = 0f;   // current slip angle (degrees)
    @Unique private int     momentum$arcadeDriftTimer  = 0;    // ticks drift has been active
    @Unique private int     momentum$arcadeDriftDir    = 0;    // ±1
    @Unique private int     momentum$arcadeHeldTimer   = 0;    // ticks drift key held without drift active

    // ── Responsive Drift state ────────────────────────────────────────────
    @Unique private boolean momentum$prevResponsiveDriftKeyHeld = false;
    @Unique private boolean momentum$responsiveDriftActive  = false;
    @Unique private float   momentum$responsiveDriftOffset  = 0f;
    @Unique private int     momentum$responsiveDriftTimer   = 0;   // ticks drift has been active
    @Unique private int     momentum$responsiveDriftDir     = 0;
    @Unique private int     momentum$responsiveHeldTimer    = 0;   // ticks drift key held without drift active
    @Unique private float   momentum$responsiveSteerAccum   = 0f;  // 0..1 steering time accumulator
    @Unique private int     momentum$lastDriftDirection     = 0;
    @Unique private boolean momentum$remoteBrakeHeld        = false;
    @Unique private boolean momentum$remoteDriftHeld        = false;
    @Unique private UUID    momentum$lastSyncedRider        = null;
    @Unique private boolean momentum$lastSyncedBrake        = false;
    @Unique private boolean momentum$lastSyncedDrift        = false;
    @Unique private int     momentum$remoteInputSyncTicks   = 0;

    // ── Key-state helpers (client vs. dedicated server) ───────────────────────
    //
    // On the client logical side we read the volatile statics set by KeyBinding polling.
    // On a dedicated server those statics are always false; instead we look up the
    // ServerKeyState map populated by the C2S KeyStatePacket.

    @Unique
    private UUID momentum$getRiderUuid() {
        Entity rider = ((Entity)(Object)this).getFirstPassenger();
        return rider != null ? rider.getUUID() : null;
    }

    @Unique private boolean momentum$brake() {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide()) {
            if (self.getFirstPassenger() instanceof Player player && player.isLocalPlayer()) {
                return MomentumBrakeState.brakeHeld;
            }
            return momentum$remoteBrakeHeld;
        }
        UUID id = momentum$getRiderUuid(); return id != null && ServerKeyState.getBrake(id);
    }

    /** Raw drift key state - true if the Handbrake (drift) key is currently held. */
    @Unique private boolean momentum$driftKey() {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide()) {
            if (self.getFirstPassenger() instanceof Player player && player.isLocalPlayer()) {
                return MomentumDriftState.driftKeyHeld;
            }
            return momentum$remoteDriftHeld;
        }
        UUID id = momentum$getRiderUuid(); return id != null && ServerKeyState.getDrift(id);
    }

    /** Drift key held AND active profile is Vanilla Drift. */
    @Unique private boolean momentum$vanillaDriftKey() {
        return momentum$driftKey() && MomentumConfig.gameplay().oDrift.profile == MomentumConfig.ODrift.Profile.VANILLA;
    }

    /** Drift key held AND active profile is Arcade Drift. */
    @Unique private boolean momentum$arcadeDriftKey() {
        return momentum$driftKey() && MomentumConfig.gameplay().oDrift.profile == MomentumConfig.ODrift.Profile.ARCADE;
    }

    /** Drift key held AND active profile is Responsive Drift. */
    @Unique private boolean momentum$responsiveDriftKey() {
        return momentum$driftKey() && MomentumConfig.gameplay().oDrift.profile == MomentumConfig.ODrift.Profile.RESPONSIVE;
    }

    @Unique
    private int momentum$driftDirForSteering(float steeringValue, int fallback) {
        if (steeringValue > 0f) {
            momentum$lastDriftDirection = -1;
            return -1;
        }
        if (steeringValue < 0f) {
            momentum$lastDriftDirection = 1;
            return 1;
        }
        if (fallback != 0) {
            momentum$lastDriftDirection = fallback;
        }
        return fallback;
    }

    @Unique
    private int momentum$stableAutoDriftDirection() {
        if (momentum$lastDriftDirection != 0) {
            return momentum$lastDriftDirection;
        }
        long bits = ((Entity) (Object) this).getUUID().getLeastSignificantBits();
        return (bits & 1L) == 0 ? 1 : -1;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void momentum$syncRemoteInputs(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }

        var automobile = (AutomobileEntity) (Object) this;
        Entity rider = self.getFirstPassenger();
        boolean hasDriver = rider != null && automobile.isDriving(rider);
        UUID riderId = hasDriver ? rider.getUUID() : null;
        boolean brake = hasDriver && ServerKeyState.getBrake(riderId);
        boolean drift = hasDriver && ServerKeyState.getDrift(riderId);
        boolean changed = !java.util.Objects.equals(riderId, momentum$lastSyncedRider)
                || brake != momentum$lastSyncedBrake
                || drift != momentum$lastSyncedDrift;

        if (momentum$remoteInputSyncTicks > 0) {
            momentum$remoteInputSyncTicks--;
        }
        if (changed || (hasDriver && momentum$remoteInputSyncTicks <= 0)) {
            VehicleInputPacket.sendToTracking(automobile, brake, drift);
            momentum$lastSyncedRider = riderId;
            momentum$lastSyncedBrake = brake;
            momentum$lastSyncedDrift = drift;
            momentum$remoteInputSyncTicks = 4;
        }
    }

    // ── Coasting fix ─────────────────────────────────────────────────────────

    @Redirect(
        method = "movementTick",
        at = @At(
            value = "INVOKE",
            target = "Lio/github/foundationgames/automobility/util/AUtils;zero(FF)F",
            ordinal = 1
        )
    )
    private float momentum$replaceCoastDecay(float value, float rate) {
        if (!MomentumConfig.gameplay().enabled) return AUtils.zero(value, 0.025f);
        if (!MomentumConfig.gameplay().movement.enabled) return AUtils.zero(value, 0.025f);
        if (momentum$brake()) return value;  // brake inject handles decel this tick
        return AUtils.zero(value, MomentumConfig.gameplay().movement.coastDecay);
    }

    // ── Acceleration scale ────────────────────────────────────────────────────

    /**
     * Bypasses Automobility's steering acceleration gate.
     *
     * movementTick() contains a ternary that suppresses normal acceleration when:
     *   (!drifting && steering != 0 && hSpeed > 0.5)
     * capping engineSpeed increment to 0.001 while cornering above ~36 km/h.
     *
     * Replacing the 0.5f threshold with Float.MAX_VALUE makes hSpeed > Float.MAX_VALUE
     * permanently false, so calculateAcceleration() is always called while steering.
     * The drift branch of the same ternary (drifting && haveSameSign) uses no float
     * constant and is unaffected.
     *
     * Momentum's understeer system already handles speed-based corner resistance,
     * so this gate is redundant and undesirable.
     */
    @ModifyConstant(
        method = "movementTick",
        constant = @Constant(doubleValue = 0.5)
    )
    private double momentum$removeSteeringAccelGate(double original) {
        if (!MomentumConfig.gameplay().enabled) return original;
        if (!MomentumConfig.gameplay().movement.enabled) return original;
        return Double.MAX_VALUE;
    }

    @ModifyArg(
        method = "movementTick",
        at = @At(
            value = "INVOKE",
            target = "Lio/github/foundationgames/automobility/entity/AutomobileEntity;calculateAcceleration(FLio/github/foundationgames/automobility/automobile/AutomobileStats;)F"
        ),
        index = 0
    )
    private float momentum$scaleAcceleration(float speed) {
        if (!MomentumConfig.gameplay().enabled) return speed;
        if (!MomentumConfig.gameplay().movement.enabled) return speed;
        return speed / MomentumConfig.gameplay().movement.accelerationScale;
    }

    // ── Steering ramp rate ────────────────────────────────────────────────────

    /**
     * Replaces the 0.42f constant in steeringTick() with steering.rampRate from config.
     * This constant controls both how fast steering builds toward full lock and how
     * fast it returns to centre. Skipped during a drift to keep drift steering responsive.
     */
    @ModifyConstant(
        method = "steeringTick",
        constant = @Constant(floatValue = 0.42f)
    )
    private float momentum$steeringRampRate(float original) {
        if (!MomentumConfig.gameplay().enabled) return original;
        if (!MomentumConfig.gameplay().steering.enabled) return original;
        if (drifting) return original;
        if (input.steering != 0) return MomentumConfig.gameplay().steering.rampRate;
        return MomentumConfig.gameplay().steering.centerRate;
    }

    // ── Speed-based understeer ────────────────────────────────────────────────

    /**
     * Scales the TARGET argument of the AUtils.shift() call that drives angularSpeed
     * in postMovementTick (ordinal 1 - the normal driving branch, not burnout or stopped).
     *
     * Automobility computes:
     *   newAngularSpeed = AUtils.shift(newAngularSpeed, 6 * traction, steering_target)
     * then blends with grip and applies the result to yaw - all before any TAIL inject
     * could act. Scaling the stored angularSpeed at TAIL has almost no effect because
     * the lerp (grip ≈ 0.6) resets it mostly from scratch each tick; no matter how
     * large the understeer value, the yaw rotation can never drop below grip × target.
     *
     * By scaling the target here instead, angularSpeed genuinely converges to
     *   target / (1 + steeringUndersteer * |hSpeed|^steeringUndersteerCurve)
     * giving true proportional understeer with no grip floor.
     * Skipped during a drift so the drift arc is unaffected.
     */
    @ModifyArg(
        method = "postMovementTick",
        at = @At(
            value = "INVOKE",
            target = "Lio/github/foundationgames/automobility/util/AUtils;shift(FFF)F",
            ordinal = 1
        ),
        index = 2
    )
    private float momentum$applyUndersteer(float target) {
        if (!MomentumConfig.gameplay().enabled) return target;
        if (!MomentumConfig.gameplay().steering.enabled) return target;
        if (drifting || momentum$arcadeDriftActive || momentum$responsiveDriftActive) return target;
        MomentumConfig.Steering s = MomentumConfig.gameplay().steering;
        float speedCurved = (float) Math.pow(Math.abs(hSpeed), s.understeerCurve);
        return target / (1f + s.understeer * speedCurved);
    }

    @Inject(method = "postMovementTick", at = @At("HEAD"))
    private void momentum$cancelCruiseOnImpact(CallbackInfo ci) {
        if (!MomentumConfig.gameplay().enabled || !MomentumConfig.gameplay().cruise.enabled) return;
        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide()) return;
        if (!(self.getControllingPassenger() instanceof Player player) || !player.isLocalPlayer()) return;
        if (touchingWall || hadVehicleCollision > 0) {
            MomentumCruiseControl.cancelOnImpact((AutomobileEntity) (Object) this, Math.abs(hSpeed) * 72f);
        }
    }

    // ── Vanilla Drift (transplanted from Automobility) ────────────────────────

    /**
     * Complete replacement of Automobility's driftingTick() using the Drift key
     * when profile == VANILLA.
     *
     * This is a direct transplant of the Automobility source logic with
     * holdingDrift/prevHoldDrift replaced by the Drift keybinding state.
     * Automobility's own driftingTick is cancelled so this is the sole
     * drift implementation.
     *
     * By writing directly to the shadowed drifting/driftDir/turboCharge fields,
     * all other Momentum mixins (understeer bypass, steering ramp bypass, brake
     * skip) continue to work correctly without modification.
     */
    @Inject(method = "driftingTick", at = @At("HEAD"), cancellable = true)
    private void momentum$vanillaDriftTick(CallbackInfo ci) {
        if (!MomentumConfig.gameplay().enabled) return;
        ci.cancel();

        boolean driftHeld = momentum$vanillaDriftKey();
        boolean prevDrift = momentum$prevDriftKeyHeld;

        boolean mcOnGnd = automobileOnGround();

        // Rising edge: drift key just pressed this tick
        if (!prevDrift && driftHeld) {
            boolean canDrift = steering != 0 && !drifting && hSpeed > 0.4f && mcOnGnd;
            if (canDrift) {
                setDrifting(true);
                driftDir = steering > 0 ? 1 : -1;
                engineSpeed -= 0.028f * engineSpeed;
            }
        }

        if (drifting) {
            if (mcOnGnd) createDriftParticles();

            if (prevDrift && !driftHeld) {
                // Falling edge: drift key released → end drift and grant turbo boost
                setDrifting(false);
                consumeTurboCharge();
            } else if (hSpeed < 0.33f) {
                // Too slow: drift cancelled, no boost
                setDrifting(false);
                turboCharge = 0;
            }

            if (mcOnGnd) {
                turboCharge += ((input.steering < 0 && driftDir < 0) || (input.steering > 0 && driftDir > 0)) ? 2 : 1;
            }
        }

        momentum$prevDriftKeyHeld = driftHeld;
    }

    // ── Arcade Drift ──────────────────────────────────────────────────────────

    /**
     * Arcade Drift state machine - runs at HEAD of movementTick each tick.
     * Active when profile == ARCADE.
     *
     * Independent of Automobility's drifting/holdingDrift/turboCharge pipeline.
     * Reads the Drift keybinding state (polled from SDL/KeyMapping in START_CLIENT_TICK).
     *
     * Rising edge + conditions → set arcadeDriftActive, snap arcadeDriftOffset to initial slip.
     * While drift held → converge offset to slipAngle; cancel if speed drops.
     * Drift released → fade offset to 0; grant boost if drift was sustained.
     *
     * The actual movement direction offset is applied in momentum$applyArcadeDriftSlip (RETURN inject).
     * Understeer is suppressed during Arcade Drift via the @ModifyArg above.
     */
    @Inject(method = "movementTick", at = @At("HEAD"))
    private void momentum$arcadeDriftStateMachine(CallbackInfo ci) {
        if (!MomentumConfig.gameplay().enabled) return;
        boolean kHeld = momentum$arcadeDriftKey();
        MomentumConfig.ArcadeDrift cfg = MomentumConfig.gameplay().arcadeDrift;

        boolean kMcOnGnd = automobileOnGround();

        if (!momentum$arcadeDriftActive) {
            if (kHeld) {
                momentum$arcadeHeldTimer++;
                // Steering-based trigger: steering exceeds threshold, hold long enough, speed OK
                if (!drifting && Math.abs(steering) > cfg.steerThreshold
                        && momentum$arcadeHeldTimer >= cfg.minHoldTicks
                        && hSpeed > cfg.minSpeedKmh / 72f && kMcOnGnd) {
                    momentum$arcadeDriftActive = true;
                    momentum$arcadeDriftDir    = momentum$driftDirForSteering(steering, 0);
                    momentum$arcadeDriftTimer  = 0;
                    momentum$arcadeDriftOffset = momentum$arcadeDriftDir * cfg.slipAngle;
                }
                // Auto-trigger: use a stable direction on both logical sides.
                else if (cfg.autoTriggerTicks > 0 && momentum$arcadeHeldTimer >= cfg.autoTriggerTicks
                        && !drifting && hSpeed > cfg.minSpeedKmh / 72f && kMcOnGnd) {
                    momentum$arcadeDriftActive = true;
                    momentum$arcadeDriftDir    = momentum$stableAutoDriftDirection();
                    momentum$arcadeDriftTimer  = 0;
                    momentum$arcadeDriftOffset = momentum$arcadeDriftDir * cfg.slipAngle;
                }
            } else {
                momentum$arcadeHeldTimer = 0;
            }
        } else {
            // Drift is active - emit smoke particles every tick
            if (kMcOnGnd) createDriftParticles();

            if (kHeld) {
                // Maintain slip angle while drift held.
                // Use current steering direction so slip angle can be redirected mid-drift.
                momentum$arcadeDriftTimer++;
                if (cfg.boostEnabled) {
                    int t = momentum$arcadeDriftTimer, min = cfg.minTicks;
                    if      (t >= min + 40) turboCharge = AutomobileEntity.LARGE_TURBO_TIME + 1;
                    else if (t >= min + 20) turboCharge = AutomobileEntity.MEDIUM_TURBO_TIME + 1;
                    else if (t >= min)      turboCharge = AutomobileEntity.SMALL_TURBO_TIME + 1;
                    else                    turboCharge = 0;
                }
                int currentDir = momentum$driftDirForSteering(steering, momentum$arcadeDriftDir);
                float target = currentDir * cfg.slipAngle;
                momentum$arcadeDriftOffset = AUtils.shift(momentum$arcadeDriftOffset, cfg.slipConvergeRate, target);
                // Cancel drift if speed drops too low
                if (hSpeed < 0.3f) {
                    momentum$arcadeDriftActive = false;
                    momentum$arcadeDriftTimer  = 0;
                    momentum$arcadeDriftOffset = 0f;
                    momentum$arcadeHeldTimer   = 0;
                    turboCharge = 0;
                }
            } else {
                // Drift released: fade slip angle back to zero (speed-adjusted)
                MomentumConfig.ArcadeDrift arcadeCfg = MomentumConfig.gameplay().arcadeDrift;
                float kDecay = arcadeCfg.slipDecaySpeedRef > 0
                    ? arcadeCfg.slipDecay * arcadeCfg.slipDecaySpeedRef / Math.max(0.1f, Math.abs(hSpeed))
                    : arcadeCfg.slipDecay;
                momentum$arcadeDriftOffset = AUtils.zero(momentum$arcadeDriftOffset, kDecay);
                if (Math.abs(momentum$arcadeDriftOffset) < 0.5f) {
                    if (arcadeCfg.boostEnabled && momentum$arcadeDriftTimer >= arcadeCfg.minTicks) {
                        engineSpeed += arcadeCfg.boost;
                        boost(0.23f, arcadeCfg.boostDuration);
                    }
                    turboCharge = 0;
                    momentum$arcadeDriftActive = false;
                    momentum$arcadeDriftTimer  = 0;
                    momentum$arcadeDriftOffset = 0f;
                }
            }
        }
        momentum$prevArcadeDriftKeyHeld = kHeld;
    }

    /**
     * Applies the Arcade Drift slip angle by rotating the entity's current velocity vector.
     * Runs at RETURN of movementTick, after setDeltaMovement has been called.
     */
    @Inject(method = "movementTick", at = @At("RETURN"))
    private void momentum$applyArcadeDriftSlip(CallbackInfo ci) {
        if (!MomentumConfig.gameplay().enabled) return;
        if (!momentum$arcadeDriftActive || Math.abs(momentum$arcadeDriftOffset) < 0.01f) return;
        Entity self = (Entity)(Object)this;
        Vec3 mov = self.getDeltaMovement();
        double rad = Math.toRadians(momentum$arcadeDriftOffset);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        self.setDeltaMovement(
            mov.x * cos - mov.z * sin,
            mov.y,
            mov.x * sin + mov.z * cos
        );
    }

    // ── Responsive Drift ──────────────────────────────────────────────────────

    /**
     * Responsive Drift state machine - runs at HEAD of movementTick.
     * Active when profile == RESPONSIVE.
     *
     * Drift held + steering≠0 → slip-angle arcade drift with steering-driven modulation.
     * Drift held + steering=0 → braking is applied in momentum$applyResponsiveBrake (RETURN inject).
     */
    @Inject(method = "movementTick", at = @At("HEAD"))
    private void momentum$responsiveDriftStateMachine(CallbackInfo ci) {
        if (!MomentumConfig.gameplay().enabled) return;
        boolean mHeld = momentum$responsiveDriftKey();
        MomentumConfig.ResponsiveDrift cfg = MomentumConfig.gameplay().responsiveDrift;
        boolean mMcOnGnd = automobileOnGround();

        if (!momentum$responsiveDriftActive) {
            if (mHeld) {
                momentum$responsiveHeldTimer++;
                float mMinSpd = cfg.minSpeedKmh / 72f;
                if (!drifting && Math.abs(steering) > cfg.steerThreshold && hSpeed > mMinSpd && mMcOnGnd
                        && momentum$responsiveHeldTimer >= cfg.minHoldTicks) {
                    momentum$responsiveDriftActive = true;
                    momentum$responsiveDriftDir    = momentum$driftDirForSteering(steering, 0);
                    momentum$responsiveDriftTimer  = 0;
                    momentum$responsiveDriftOffset = 0f;
                    momentum$responsiveHeldTimer   = 0;
                } else {
                    int threshold = cfg.autoTriggerTicks;
                    if (threshold > 0 && momentum$responsiveHeldTimer >= threshold
                            && !drifting && hSpeed > mMinSpd && mMcOnGnd) {
                        momentum$responsiveDriftActive = true;
                        momentum$responsiveDriftDir    = momentum$stableAutoDriftDirection();
                        momentum$responsiveDriftTimer  = 0;
                        momentum$responsiveDriftOffset = 0f;
                        momentum$responsiveHeldTimer   = 0;
                    }
                }
            } else {
                momentum$responsiveHeldTimer = 0;
            }
        } else {
            if (mMcOnGnd) createDriftParticles();

            if (mHeld) {
                momentum$responsiveDriftTimer++;
                if (cfg.boostEnabled) {
                    int t = momentum$responsiveDriftTimer, min = cfg.minTicks;
                    if      (t >= min + 40) turboCharge = AutomobileEntity.LARGE_TURBO_TIME + 1;
                    else if (t >= min + 20) turboCharge = AutomobileEntity.MEDIUM_TURBO_TIME + 1;
                    else if (t >= min)      turboCharge = AutomobileEntity.SMALL_TURBO_TIME + 1;
                    else                    turboCharge = 0;
                }
                if (Math.abs(steering) > 0.05f) {
                    momentum$responsiveSteerAccum = Math.min(1.0f, momentum$responsiveSteerAccum + cfg.steerBuildRate);
                } else {
                    momentum$responsiveSteerAccum = Math.max(0.0f, momentum$responsiveSteerAccum - cfg.steerDecayRate);
                }
                int currentDir = momentum$driftDirForSteering(steering, momentum$responsiveDriftDir);
                float steerFactor = cfg.constantAngle ? 1.0f
                    : (float) Math.pow(momentum$responsiveSteerAccum, cfg.steerSensitivity);
                float target = currentDir * cfg.slipAngle * steerFactor;
                momentum$responsiveDriftOffset += (target - momentum$responsiveDriftOffset) * cfg.slipConvergeRate;
                if (hSpeed < 0.3f) {
                    turboCharge = 0;
                    momentum$responsiveDriftActive = false;
                    momentum$responsiveDriftTimer  = 0;
                    momentum$responsiveDriftOffset = 0f;
                    momentum$responsiveSteerAccum  = 0f;
                }
            } else {
                float mDecay = cfg.slipDecaySpeedRef > 0
                    ? cfg.slipDecay * cfg.slipDecaySpeedRef / Math.max(0.1f, Math.abs(hSpeed))
                    : cfg.slipDecay;
                momentum$responsiveDriftOffset = AUtils.zero(momentum$responsiveDriftOffset, mDecay);
                if (Math.abs(momentum$responsiveDriftOffset) < 0.5f) {
                    if (cfg.boostEnabled && momentum$responsiveDriftTimer >= cfg.minTicks) {
                        engineSpeed += cfg.boost;
                        boost(0.23f, cfg.boostDuration);
                    }
                    turboCharge = 0;
                    momentum$responsiveDriftActive = false;
                    momentum$responsiveDriftTimer  = 0;
                    momentum$responsiveDriftOffset = 0f;
                    momentum$responsiveSteerAccum  = 0f;
                }
            }
        }
        momentum$prevResponsiveDriftKeyHeld = mHeld;
    }

    /**
     * Applies the Responsive Drift slip angle by rotating the velocity vector.
     */
    @Inject(method = "movementTick", at = @At("RETURN"))
    private void momentum$applyResponsiveDriftSlip(CallbackInfo ci) {
        if (!MomentumConfig.gameplay().enabled) return;
        if (!momentum$responsiveDriftActive || Math.abs(momentum$responsiveDriftOffset) < 0.01f) return;
        Entity self = (Entity)(Object)this;
        Vec3 mov = self.getDeltaMovement();
        double rad = Math.toRadians(momentum$responsiveDriftOffset);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        self.setDeltaMovement(
            mov.x * cos - mov.z * sin,
            mov.y,
            mov.x * sin + mov.z * cos
        );
    }

    /**
     * Applies braking when Responsive Drift is held and no drift is active.
     */
    @Inject(method = "movementTick", at = @At("RETURN"))
    private void momentum$applyResponsiveBrake(CallbackInfo ci) {
        if (!MomentumConfig.gameplay().enabled) return;
        if (!MomentumConfig.gameplay().movement.enabled) return;
        if (!MomentumConfig.gameplay().responsiveDrift.brakeEnabled) return;
        if (!momentum$responsiveDriftKey()) return;
        if (momentum$responsiveDriftActive) return;
        if (drifting) return;
        float decay = MomentumConfig.gameplay().movement.brakeDecay;
        engineSpeed = Math.max(engineSpeed - decay, 0f);
    }

    /**
     * Applies braking when Arcade Drift is held and no drift is active.
     */
    @Inject(method = "movementTick", at = @At("RETURN"))
    private void momentum$applyArcadeBrake(CallbackInfo ci) {
        if (!MomentumConfig.gameplay().enabled) return;
        if (!MomentumConfig.gameplay().movement.enabled) return;
        if (!MomentumConfig.gameplay().arcadeDrift.brakeEnabled) return;
        if (!momentum$arcadeDriftKey()) return;
        if (momentum$arcadeDriftActive) return;
        if (momentum$responsiveDriftActive) return;
        if (drifting) return;
        float decay = MomentumConfig.gameplay().movement.brakeDecay;
        engineSpeed = Math.max(engineSpeed - decay, 0f);
    }

    // ── Brake ────────────────────────────────────────────────────────────────

    /**
     * Intercepts the `back` parameter of provideClientInput and forces it to false
     * when Momentum is enabled.
     */
    @Inject(method = "provideClientInput", at = @At("HEAD"), cancellable = true)
    private void momentum$applyCruiseControlInput(boolean fwd, boolean back, boolean left, boolean right, boolean space, boolean ctrl, CallbackInfo ci) {
        MomentumCruiseControl.AdjustedInput adjusted = MomentumCruiseControl.adjustInput(
                (AutomobileEntity) (Object) this,
                fwd, back, left, right, space, ctrl
        );
        if (adjusted == null) return;

        if (this.input.setDigitalInputs(
                adjusted.fwd(),
                adjusted.back(),
                adjusted.left(),
                adjusted.right(),
                adjusted.space(),
                adjusted.ctrl()
        )) {
            ClientPackets.sendServerboundAutomobileSyncPacket((AutomobileEntity) (Object) this);
        }
        ci.cancel();
    }

    @ModifyVariable(
        method = "provideClientInput",
        at = @At("HEAD"),
        index = 2,
        remap = false
    )
    private boolean momentum$suppressVanillaBackInput(boolean back) {
        if (!MomentumConfig.gameplay().enabled) return back;
        if (!MomentumConfig.gameplay().movement.enabled) return back;
        return false;
    }

    /**
     * Suppresses Automobility's own braking decay while Momentum is enabled.
     */
    @Redirect(
        method = "movementTick",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/Math;max(FF)F",
            ordinal = 3
        )
    )
    private float momentum$suppressVanillaBrakeDecay(float a, float b) {
        if (!MomentumConfig.gameplay().enabled) return Math.max(a, b);
        if (!MomentumConfig.gameplay().movement.enabled) return Math.max(a, b);
        return engineSpeed;  // no-op: Momentum's @Inject at RETURN owns braking
    }

    /**
     * Applies linear braking directly to engineSpeed at the end of movementTick,
     * using MomentumBrakeState.brakeHeld as the source of truth.
     */
    @Inject(method = "movementTick", at = @At("RETURN"))
    private void momentum$applyBrake(CallbackInfo ci) {
        if (!MomentumConfig.gameplay().enabled) return;
        if (!MomentumConfig.gameplay().movement.enabled) return;
        if (!momentum$brake()) return;
        if (drifting) return;  // braking reduces hSpeed which would cancel the drift
        float decay = MomentumConfig.gameplay().movement.brakeDecay;
        engineSpeed = Math.max(engineSpeed - decay, -0.25f);
    }

    // ── Debug accessors (SteeringDebugAccessor) ───────────────────────────────

    @Unique @Override public float   momentum$getSteering()                { return steering; }
    @Unique @Override public float   momentum$getHSpeed()                  { return hSpeed; }
    @Unique @Override public float   momentum$getAngularSpeed()            { return angularSpeed; }
    @Unique @Override public boolean momentum$isDrifting()                 { return drifting; }
    @Unique @Override public boolean momentum$isOnGround()                 { return automobileOnGround(); }
    @Unique @Override public boolean momentum$isArcadeDriftActive()        { return momentum$arcadeDriftActive; }
    @Unique @Override public float   momentum$getArcadeDriftOffset()       { return momentum$arcadeDriftOffset; }
    @Unique @Override public boolean momentum$isResponsiveDriftActive()    { return momentum$responsiveDriftActive; }
    @Unique @Override public float   momentum$getResponsiveDriftOffset()   { return momentum$responsiveDriftOffset; }
    @Unique @Override public float   momentum$getEngineSpeed()             { return engineSpeed; }
    @Unique @Override public void momentum$setRemoteInputState(boolean brake, boolean drift) {
        Entity self = (Entity) (Object) this;
        if (!(self.getFirstPassenger() instanceof Player player) || !player.isLocalPlayer()) {
            momentum$remoteBrakeHeld = brake;
            momentum$remoteDriftHeld = drift;
        }
    }
}
