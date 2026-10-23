package io.github.milkucha.momentum.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import io.github.foundationgames.automobility.automobile.AutomobileStats;
import io.github.foundationgames.automobility.util.AUtils;
import io.github.foundationgames.automobility.util.network.ClientPackets;
import io.github.milkucha.momentum.MomentumBrakeState;
import io.github.milkucha.momentum.CoreDriftReleaseHandoff;
import io.github.milkucha.momentum.PedalBrakePolicy;
import io.github.milkucha.momentum.MomentumCruiseControl;
import io.github.milkucha.momentum.MomentumDriftState;
import io.github.milkucha.momentum.MomentumVehicleDrivingModes;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import io.github.milkucha.momentum.accessor.ExternalEngineDriveAccess;
import io.github.milkucha.momentum.accessor.VehicleDrivingStatsAccess;
import io.github.milkucha.momentum.api.ExternalEngineDriveApi;
import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.milkucha.momentum.network.ServerKeyState;
import io.github.milkucha.momentum.network.VehicleInputPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.player.Player;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
public abstract class AutomobileEntityMixin implements SteeringDebugAccessor, ExternalEngineDriveAccess {

    @Shadow @Final private static EntityDataAccessor<Holder<AutomobileEngine>> ENGINE_TYPE;
    @Shadow @Final private static EntityDataAccessor<Holder<AutomobileFrame>> FRAME_TYPE;
    @Shadow @Final private AutomobileStats stats;

    @Shadow private boolean drifting;
    @Shadow private boolean prevHoldDrift;
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
    @Unique private final CoreDriftReleaseHandoff momentum$coreReleaseHandoff = new CoreDriftReleaseHandoff();

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

    @Inject(method = "<init>", at = @At("RETURN"))
    private void momentum$bindDrivingStats(CallbackInfo ci) {
        ((VehicleDrivingStatsAccess) (Object) stats).momentum$bindDrivingOwner((AutomobileEntity) (Object) this);
    }

    @Unique
    private MomentumConfig momentum$drivingConfig() {
        return MomentumVehicleDrivingModes.baselineFor((AutomobileEntity) (Object) this);
    }

    @Unique
    private MomentumVehicleDrivingModes.Settings momentum$drivingSettings() {
        return MomentumVehicleDrivingModes.settingsFor((AutomobileEntity) (Object) this);
    }

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
                return MomentumBrakeState.rawBrakeHeld;
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

    @Unique private UUID momentum$driftDriverContext;

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lio/github/foundationgames/automobility/entity/AutomobileEntity;expireStaleClientInput()V",
            shift = At.Shift.BEFORE), require = 1)
    private void momentum$clearUnavailableAddonState(CallbackInfo ci) {
        var car = (AutomobileEntity) (Object) this;
        Entity driver = car.getFirstPassenger();
        UUID context = driver != null && car.isDriving(driver) ? driver.getUUID() : null;
        if (!java.util.Objects.equals(context, momentum$driftDriverContext)
                || !MomentumConfig.gameplay().enabled) {
            momentum$resetAddonDriftState();
        }
        momentum$driftDriverContext = context;
    }

    @Inject(method = "removePassenger", at = @At("HEAD"))
    private void momentum$clearDepartingDriverAddonState(Entity passenger, CallbackInfo ci) {
        // RIA's seat table may already be empty. Invalidate only the captured
        // actual driver's Core input history, before losing that reference.
        if (momentum$coreReleaseHandoff.isBoundDriver(passenger)) {
            momentum$resetCoreDriftInputHistory();
            momentum$coreReleaseHandoff.bindDriver(null);
        }
        // RIA can clear its seat lookup before the actual driver removal reaches HEAD.
        if (((AutomobileEntity) (Object) this).isDriving(passenger)
                || (passenger != null && passenger.getUUID().equals(momentum$driftDriverContext))) {
            momentum$resetAddonDriftState();
            momentum$driftDriverContext = null;
        }
    }

    @Unique
    private void momentum$bindCoreReleaseDriver() {
        var car = (AutomobileEntity) (Object) this;
        Entity driver = car.getFirstPassenger();
        // This runs after ordinary admission cleanup. A masked/ineligible controller
        // cannot retain release ownership, even if the actual passenger UUID matches.
        Object context = driver != null && car.isDriving(driver)
                && car.getControllingPassenger() == driver
                && !(car.level().isClientSide() && driver instanceof Player player
                    && player.isLocalPlayer() && MomentumConfig.clientDrivingBlocked())
                ? driver : null;
        boolean driverChanged = momentum$coreReleaseHandoff.bindDriver(context);
        if (driverChanged) {
            momentum$resetAddonDriftState();
        }
        // A first denied tick can still have held raw input, so Core may latch
        // it again. Every unavailable entry must discard that non-driver edge.
        if (driverChanged || context == null) {
            momentum$resetCoreDriftInputHistory();
        }
    }

    @Unique
    private void momentum$resetCoreDriftInputHistory() {
        // A departed/replaced driver's held key is not a genuine Core release.
        // Preserve the actual drift, charge, boost and motion; only invalidate
        // its old input edge before a disabled-mode Core body can observe it.
        prevHoldDrift = false;
    }

    @Unique
    private void momentum$resetAddonDriftState() {
        momentum$prevDriftKeyHeld = false;
        momentum$prevArcadeDriftKeyHeld = false;
        momentum$arcadeDriftActive = false;
        momentum$arcadeDriftOffset = 0f;
        momentum$arcadeDriftTimer = 0;
        momentum$arcadeHeldTimer = 0;
        momentum$prevResponsiveDriftKeyHeld = false;
        momentum$responsiveDriftActive = false;
        momentum$responsiveDriftOffset = 0f;
        momentum$responsiveDriftTimer = 0;
        momentum$responsiveHeldTimer = 0;
        momentum$responsiveSteerAccum = 0f;
        momentum$remoteBrakeHeld = false;
        momentum$remoteDriftHeld = false;
        // Core drift, boost, position and speed are deliberately not reset.
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
        if (momentum$coreMechanicalDrive()) return value;
        if (momentum$externalDrive()) return AUtils.zero(value, rate);
        var config = momentum$drivingConfig();
        if (!config.enabled || !config.movement.enabled)
            return AUtils.zero(value, momentum$passiveCombustionCoastRate(value, rate));
        // Existing drift decay remains separate from normal pedal ownership.
        if (drifting ? momentum$brake() : ((AutomobileEntity) (Object) this).rawBrakePedalHeld()) return value;
        return AUtils.zero(value, momentum$passiveCombustionCoastRate(value, momentum$drivingSettings().coastDecay()));
    }

    /** Only neutral forward roll of stock combustion engines changes. External/EV ownership,
     * raw pedals, reverse and all drift/brake handoffs retain their existing paths.
     */
    @Unique
    private float momentum$passiveCombustionCoastRate(float value, float rate) {
        var automobile = (AutomobileEntity) (Object) this;
        if (value <= 0 || hSpeed < 0 || input.accelerating || input.braking || automobile.rawBrakePedalHeld()
                || drifting || input.holdingDrift || momentum$driftKey() || automobile.burningOut()
                || momentum$arcadeDriftActive || momentum$responsiveDriftActive
                || Math.abs(momentum$arcadeDriftOffset) >= .01f || Math.abs(momentum$responsiveDriftOffset) >= .01f)
            return rate;
        var engine = automobile.getEntityData().get(ENGINE_TYPE).unwrapKey().orElse(null);
        if (engine == null || !"automobility".equals(engine.identifier().getNamespace())) return rate;
        return rate * 0.5f;
    }

    /** MC-5 gets a stronger forward launch, not new speed limits, reverse/braking or EV torque. */
    @Inject(method = "calculateAcceleration(FLio/github/foundationgames/automobility/automobile/AutomobileStats;)F",
            at = @At("RETURN"), cancellable = true, require = 1)
    private void momentum$mc5ForwardAcceleration(float speed, AutomobileStats stats, CallbackInfoReturnable<Float> cir) {
        var automobile = (AutomobileEntity) (Object) this;
        if (momentum$externalDrive() || momentum$coreMechanicalDrive() || engineSpeed < 0 || hSpeed < 0
                || engineSpeed >= stats.getComfortableSpeed() || !input.accelerating || input.braking
                || automobile.rawBrakePedalHeld() || drifting || input.holdingDrift || momentum$driftKey()
                || automobile.burningOut() || momentum$arcadeDriftActive || momentum$responsiveDriftActive)
            return;
        var engine = automobile.getEntityData().get(ENGINE_TYPE).unwrapKey().orElse(null);
        var frame = automobile.getEntityData().get(FRAME_TYPE).unwrapKey().orElse(null);
        if (engine == null || !"automobility".equals(engine.identifier().getNamespace()) || frame == null
                || !"automobility".equals(frame.identifier().getNamespace()) || !"mx5_na".equals(frame.identifier().getPath()))
            return;
        float acceleration = cir.getReturnValue();
        if (Float.isFinite(acceleration) && acceleration > 0) cir.setReturnValue(acceleration * 1.25f);
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
        var config = momentum$drivingConfig();
        if (!config.enabled) return original;
        if (!config.movement.enabled) return original;
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
        if (momentum$coreMechanicalDrive()) return speed;
        var config = momentum$drivingConfig();
        if (!config.enabled) return speed;
        if (!config.movement.enabled) return speed;
        return speed / momentum$drivingSettings().accelerationScale();
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
        var config = momentum$drivingConfig();
        if (!config.enabled) return original;
        if (!config.steering.enabled) return original;
        if (drifting) return original;
        var settings = momentum$drivingSettings();
        if (input.steering != 0) return settings.rampRate();
        return settings.centerRate();
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
        var config = momentum$drivingConfig();
        if (!config.enabled) return target;
        if (!config.steering.enabled) return target;
        if (drifting || momentum$arcadeDriftActive || momentum$responsiveDriftActive) return target;
        var settings = momentum$drivingSettings();
        float speedCurved = (float) Math.pow(Math.abs(hSpeed), settings.understeerCurve());
        return target / (1f + settings.understeer() * speedCurved);
    }

    @Inject(method = "postMovementTick", at = @At("HEAD"))
    private void momentum$cancelCruiseOnImpact(CallbackInfo ci) {
        Entity impactSelf = (Entity) (Object) this;
        if (!impactSelf.level().isClientSide() && (touchingWall || hadVehicleCollision > 0))
            io.github.milkucha.momentum.network.CruiseMotorServer.cancelOnImpact(
                    (AutomobileEntity) (Object) this, Math.abs(hSpeed) * 72f);
        if (!MomentumCruiseControl.isEnabled()) return;
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
        momentum$bindCoreReleaseDriver();
        boolean enabled = MomentumConfig.gameplay().enabled;
        boolean coreRelease = momentum$coreReleaseHandoff.beforeDriftTick(
                enabled, drifting, prevHoldDrift, input.holdingDrift);
        if (!enabled) return;
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
                if (!momentum$externalDrive() && !momentum$coreMechanicalDrive()) engineSpeed -= 0.028f * engineSpeed;
            }
        }

        if (drifting) {
            if (mcOnGnd) createDriftParticles();

            if (momentum$coreReleaseHandoff.coreOwnsRelease() ? coreRelease : prevDrift && !driftHeld) {
                // An old Core drift waits for its own real release, not a mapped-key edge.
                setDrifting(false);
                if (momentum$externalDrive() && !momentum$coreMechanicalDrive()) turboCharge = 0;
                else consumeTurboCharge();
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
        // Core must see its own held/released edge when its original body resumes.
        // Never alias this latch to Momentum's independently configurable key.
        prevHoldDrift = input.holdingDrift;
        momentum$coreReleaseHandoff.afterDriftTick(drifting);
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
                    if ((!momentum$externalDrive() || momentum$coreMechanicalDrive()) && ((AutomobileEntity) (Object) this).engineRunning()
                            && arcadeCfg.boostEnabled && momentum$arcadeDriftTimer >= arcadeCfg.minTicks) {
                        if (!momentum$coreMechanicalDrive()) engineSpeed += arcadeCfg.boost;
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
                    if ((!momentum$externalDrive() || momentum$coreMechanicalDrive()) && ((AutomobileEntity) (Object) this).engineRunning()
                            && cfg.boostEnabled && momentum$responsiveDriftTimer >= cfg.minTicks) {
                        if (!momentum$coreMechanicalDrive()) engineSpeed += cfg.boost;
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
        if (momentum$externalDrive() || momentum$coreMechanicalDrive()) return;
        var config = momentum$drivingConfig();
        if (!config.enabled) return;
        if (!config.movement.enabled) return;
        if (!config.responsiveDrift.brakeEnabled) return;
        if (!momentum$responsiveDriftKey()) return;
        if (momentum$responsiveDriftActive) return;
        if (drifting) return;
        float decay = momentum$drivingSettings().brakeDecay();
        engineSpeed = Math.max(engineSpeed - decay, 0f);
    }

    /**
     * Applies braking when Arcade Drift is held and no drift is active.
     */
    @Inject(method = "movementTick", at = @At("RETURN"))
    private void momentum$applyArcadeBrake(CallbackInfo ci) {
        if (momentum$externalDrive() || momentum$coreMechanicalDrive()) return;
        var config = momentum$drivingConfig();
        if (!config.enabled) return;
        if (!config.movement.enabled) return;
        if (!config.arcadeDrift.brakeEnabled) return;
        if (!momentum$arcadeDriftKey()) return;
        if (momentum$arcadeDriftActive) return;
        if (momentum$responsiveDriftActive) return;
        if (drifting) return;
        float decay = momentum$drivingSettings().brakeDecay();
        engineSpeed = Math.max(engineSpeed - decay, 0f);
    }

    // ── Brake ────────────────────────────────────────────────────────────────

    /** Adjust cruise input without discarding the player's raw brake/reverse pedal. */
    @Inject(method = "provideClientInput", at = @At("HEAD"), cancellable = true)
    private void momentum$applyCruiseControlInput(boolean fwd, boolean back, boolean left, boolean right, boolean space, boolean ctrl, CallbackInfo ci) {
        MomentumCruiseControl.AdjustedInput adjusted = MomentumCruiseControl.adjustInput(
                (AutomobileEntity) (Object) this,
                fwd, back, left, right, space, ctrl
        );
        if (adjusted == null) return;

        boolean changed = this.input.setDigitalInputs(
                adjusted.fwd(),
                adjusted.back(),
                adjusted.left(),
                adjusted.right(),
                adjusted.space(),
                adjusted.ctrl()
        );
        boolean republish = io.github.milkucha.momentum.network.CruiseMotorClient.consumeInputResync(
                (AutomobileEntity) (Object) this);
        if (changed || republish) {
            ClientPackets.sendServerboundAutomobileSyncPacket((AutomobileEntity) (Object) this);
        }
        ci.cancel();
    }

    @Inject(method = "provideClientInput", at = @At("RETURN"))
    private void momentum$republishAfterCruiseWithdrawal(boolean fwd, boolean back, boolean left,
            boolean right, boolean space, boolean ctrl, CallbackInfo ci) {
        if (io.github.milkucha.momentum.network.CruiseMotorClient.consumeInputResync(
                (AutomobileEntity) (Object) this))
            ClientPackets.sendServerboundAutomobileSyncPacket((AutomobileEntity) (Object) this);
    }

    /**
     * The base entity owns the reverse-release latch for all engines. Supply only the
     * separate brake binding before it samples raw intent; handbrake/drift never
     * count as a brake-pedal release or a request for reverse.
     */
    @Inject(method = "prepareReverseBrakeIntent", at = @At("HEAD"))
    private void momentum$provideAdditionalBrakeIntent(CallbackInfo ci) {
        var config = momentum$drivingConfig();
        ((AutomobileEntity) (Object) this).setAdditionalBrakeInput(
                momentum$brake(), config.enabled && config.movement.enabled);
    }

    /**
     * Selects ordinary Original braking; Extended continues to own its configured decay.
     */
    @Redirect(
        method = "movementTick",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/Math;max(FF)F",
            ordinal = 3
        ),
        require = 1
    )
    private float momentum$suppressVanillaBrakeDecay(float a, float b) {
        if (momentum$coreMechanicalDrive()) return engineSpeed;
        float proposed = momentum$limitOrdinaryReverse((AutomobileEntity) (Object) this, Math.max(a, b), b);
        if (momentum$externalDrive()) return proposed;
        var config = momentum$drivingConfig();
        if (!config.enabled) {
            var automobile = (AutomobileEntity) (Object) this;
            boolean unsafe = drifting || input.holdingDrift || momentum$driftKey()
                    || momentum$arcadeDriftActive || momentum$responsiveDriftActive
                    || Math.abs(momentum$arcadeDriftOffset) >= 0.01f
                    || Math.abs(momentum$responsiveDriftOffset) >= 0.01f;
            return io.github.milkucha.momentum.OriginalBrakePolicy.runningResult(
                    proposed, engineSpeed, b, automobile.burningOut(),
                    input.accelerating, input.braking, automobile.rawBrakePedalHeld(), unsafe);
        }
        if (!config.movement.enabled) return proposed;
        return engineSpeed;  // no-op: Momentum's @Inject at RETURN owns braking
    }

    // The adjacent engine-off branch also belongs to Momentum. Suppressing only
    // ordinal 3 leaves a vanilla .15 decay followed by Momentum's configured decay.
    @Redirect(method = "movementTick",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F", ordinal = 4),
            require = 1)
    private float momentum$suppressVanillaStoppedEngineBrakeDecay(float a, float b) {
        if (drifting || !momentum$drivingConfig().enabled) return Math.max(a, b);
        return momentum$suppressVanillaBrakeDecay(a, b);
    }

    /**
     * Applies the configured decay to the core's raw pedal intent and braking floor.
     * Holding the brake through a forward stop therefore cannot create reverse
     * propulsion; a distinct pedal release is required before the floor is negative.
     */
    @Inject(method = "movementTick", at = @At("RETURN"))
    private void momentum$applyBrake(CallbackInfo ci) {
        if (momentum$externalDrive() || momentum$coreMechanicalDrive()) return;
        var config = momentum$drivingConfig();
        if (!config.enabled) return;
        if (!config.movement.enabled) return;
        var automobile = (AutomobileEntity) (Object) this;
        if (!automobile.rawBrakePedalHeld()) return;
        if (drifting) return;  // braking reduces hSpeed which would cancel the drift
        var frameId = automobile.getEntityData().get(FRAME_TYPE).unwrapKey().map(key -> key.identifier()).orElse(null);
        float decay = PedalBrakePolicy.decay(engineSpeed, momentum$drivingSettings().brakeDecay(),
                config.movement.forwardPedalScale, config.movement.vehiclePedalBalance,
                frameId == null ? null : frameId.getNamespace(), frameId == null ? null : frameId.getPath());
        float floor = automobile.brakingSpeedFloor();
        engineSpeed = momentum$limitOrdinaryReverse(automobile, Math.max(engineSpeed - decay, floor), floor);
    }

    @Unique
    private float momentum$limitOrdinaryReverse(AutomobileEntity automobile, float proposed, float floor) {
        if (engineSpeed > 0 || proposed >= engineSpeed || floor >= 0) return proposed;
        boolean unsafe = drifting || input.holdingDrift || momentum$driftKey()
                || momentum$arcadeDriftActive || momentum$responsiveDriftActive
                || Math.abs(momentum$arcadeDriftOffset) >= .01f || Math.abs(momentum$responsiveDriftOffset) >= .01f;
        boolean ordinary = automobile.engineRunning() && automobile.rawBrakePedalHeld()
                && !input.accelerating && !unsafe && !automobile.burningOut();
        return io.github.milkucha.momentum.ReverseAccelerationPolicy.constrain(engineSpeed, proposed, floor,
                io.github.milkucha.momentum.network.ReverseDriveCapability.negotiated(automobile), ordinary);
    }

    /** Normal backward pedal is not a deliberate W+S burnout in either driving model.
     * Keep active burnout cleanup and every drift/handbrake/external path in Core.
     */
    @Inject(method = "burnoutTick", at = @At("HEAD"), cancellable = true, require = 1)
    private void momentum$keepNormalPedalOutOfBurnout(CallbackInfo ci) {
        var automobile = (AutomobileEntity) (Object) this;
        boolean unsafe = drifting || input.holdingDrift || momentum$driftKey()
                || momentum$arcadeDriftActive || momentum$responsiveDriftActive
                || Math.abs(momentum$arcadeDriftOffset) >= 0.01f
                || Math.abs(momentum$responsiveDriftOffset) >= 0.01f;
        if (io.github.milkucha.momentum.BurnoutBrakePolicy.suppressStart(
                true, momentum$externalDrive(),
                automobile.burningOut(), input.accelerating, input.braking,
                automobile.rawBrakePedalHeld(), hSpeed, unsafe)) {
            ci.cancel();
        }
    }

    @Unique
    private boolean momentum$coreMechanicalDrive() {
        return io.github.foundationgames.automobility.api.drivetrain.VehicleMechanicalAccess.active(this);
    }

    @Unique
    private boolean momentum$externalDrive() {
        return ExternalEngineDriveApi.ownsDrive((AutomobileEntity) (Object) this);
    }

    @Unique @Override public Holder<AutomobileEngine> momentum$driveEngine() {
        return ((AutomobileEntity) (Object) this).getEntityData().get(ENGINE_TYPE);
    }

    @Unique @Override public ExternalEngineDriveApi.DriveState momentum$externalDriveState() {
        var self = (AutomobileEntity) (Object) this;
        var passenger = self.getFirstPassenger();
        var attachment = self.getFrontAttachment();
        boolean driver = self.isDriving(passenger)
                || (attachment != null && attachment.isProvidingAlternativeInputs(self, passenger));
        var config = momentum$drivingConfig();
        boolean enabled = config.enabled && config.movement.enabled;
        boolean driftKey = driver && momentum$driftKey();
        boolean handbrake = enabled && driftKey && !drifting
                && ((config.oDrift.profile == MomentumConfig.ODrift.Profile.RESPONSIVE
                        && config.responsiveDrift.brakeEnabled && !momentum$responsiveDriftActive)
                    || (config.oDrift.profile == MomentumConfig.ODrift.Profile.ARCADE
                        && config.arcadeDrift.brakeEnabled && !momentum$arcadeDriftActive
                        && !momentum$responsiveDriftActive));
        boolean unsafe = driftKey || drifting
                || momentum$arcadeDriftActive || momentum$responsiveDriftActive
                || Math.abs(momentum$arcadeDriftOffset) >= 0.01f
                || Math.abs(momentum$responsiveDriftOffset) >= 0.01f;
        var settings = momentum$drivingSettings();
        return new ExternalEngineDriveApi.DriveState(enabled,
                settings.coastDecay(), settings.brakeDecay(), driver && momentum$brake(), handbrake, unsafe);
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
