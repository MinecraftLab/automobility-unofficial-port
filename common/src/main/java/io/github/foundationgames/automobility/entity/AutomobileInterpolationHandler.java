package io.github.foundationgames.automobility.entity;

import net.minecraft.core.PositionAndRotation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Bridges 26.3's packet API to Automobility's existing position-tracking tick phase.
 * Vanilla's commonTick must not advance this state before AutomobileEntity captures prevPos.
 */
final class AutomobileInterpolationHandler implements InterpolationHandler {
    private final Supplier<PositionAndRotation> currentPose;
    private final BooleanSupplier localAuthority;
    private final BooleanSupplier authoritativePacket;
    private final Consumer<PositionAndRotation> applyPose;
    private final int interpolationSteps;
    private @Nullable PositionAndRotation pendingPose;
    private int remainingSteps;

    AutomobileInterpolationHandler(Supplier<PositionAndRotation> currentPose,
                                   BooleanSupplier localAuthority,
                                   BooleanSupplier authoritativePacket,
                                   Consumer<PositionAndRotation> applyPose,
                                   int interpolationSteps) {
        this.currentPose = currentPose;
        this.localAuthority = localAuthority;
        this.authoritativePacket = authoritativePacket;
        this.applyPose = applyPose;
        this.interpolationSteps = Math.max(1, interpolationSteps);
    }

    @Override
    public @Nullable PositionAndRotation target() {
        return this.hasActiveInterpolation() ? this.pendingPose : null;
    }

    @Override
    public boolean interpolateTo(@Nullable PositionPath path, float yaw, float pitch, boolean hasRotation) {
        if (this.authoritativePacket.getAsBoolean() || this.localAuthority.getAsBoolean()) {
            // Hard teleports calculate their relative base from target() before reaching here.
            // Returning false lets vanilla apply that already-resolved correction immediately.
            this.cancel();
            return false;
        }

        PositionAndRotation previousTarget = this.target();
        PositionAndRotation base = previousTarget != null ? previousTarget : this.currentPose.get();
        Vec3 position = path != null ? path.endPosition() : base.position();
        float targetYaw = hasRotation ? yaw : base.yRot();
        float targetPitch = hasRotation ? pitch : base.xRot();
        if (previousTarget != null && previousTarget.is(position, targetYaw, targetPitch)) {
            return true;
        }
        this.pendingPose = PositionAndRotation.of(position, targetYaw, targetPitch);
        this.remainingSteps = this.interpolationSteps;
        return true;
    }

    @Override
    public void interpolate() {
        // Deliberately deferred to AutomobileEntity.positionTrackingTick(), exactly once per tick.
    }

    void tick() {
        if (!this.hasActiveInterpolation()) return;
        PositionAndRotation current = this.currentPose.get();
        double progress = 1.0 / this.remainingSteps;
        this.applyPose.accept(PositionAndRotation.of(
                current.position().lerp(this.pendingPose.position(), progress),
                current.yRot() + Mth.wrapDegrees(this.pendingPose.yRot() - current.yRot()) / this.remainingSteps,
                current.xRot() + Mth.wrapDegrees(this.pendingPose.xRot() - current.xRot()) / this.remainingSteps
        ));
        if (--this.remainingSteps == 0) this.cancel();
    }

    @Override
    public void applyPredictedMovement(Vec3 delta) {
        if (this.hasActiveInterpolation()
                && Double.isFinite(delta.x) && Double.isFinite(delta.y) && Double.isFinite(delta.z)) {
            Vec3 translated = this.pendingPose.position().add(delta);
            if (!Double.isFinite(translated.x) || !Double.isFinite(translated.y)
                    || !Double.isFinite(translated.z)) return;
            this.pendingPose = PositionAndRotation.of(translated,
                    this.pendingPose.yRot(), this.pendingPose.xRot());
        }
    }

    @Override
    public boolean hasActiveInterpolation() {
        // A newly mounted local driver must not expose an old remote endpoint, even before tick().
        if (this.localAuthority.getAsBoolean()) this.cancel();
        return this.remainingSteps > 0 && this.pendingPose != null;
    }

    @Override
    public void cancel() {
        this.pendingPose = null;
        this.remainingSteps = 0;
    }
}
