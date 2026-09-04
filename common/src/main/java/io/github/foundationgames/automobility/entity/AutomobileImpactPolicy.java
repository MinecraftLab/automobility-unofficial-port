package io.github.foundationgames.automobility.entity;

import net.minecraft.world.phys.Vec3;

final class AutomobileImpactPolicy {
    private static final double OBSERVED_MOVEMENT_TOLERANCE = 0.1;

    private AutomobileImpactPolicy() {
    }

    static Vec3 boundedVelocityForDamage(Vec3 serverPlannedVelocity, Vec3 serverObservedMovement) {
        double plannedSpeed = serverPlannedVelocity.horizontalDistance();
        double observedSpeed = serverObservedMovement.horizontalDistance();
        if (!Double.isFinite(plannedSpeed) || !Double.isFinite(observedSpeed)
                || plannedSpeed <= 0.2
                || observedSpeed > RearAttachmentMovementPolicy.MAX_BETWEEN_TICK_HORIZONTAL_DISPLACEMENT) {
            return Vec3.ZERO;
        }

        double impactSpeed = Math.min(plannedSpeed, observedSpeed + OBSERVED_MOVEMENT_TOLERANCE);
        if (!Double.isFinite(impactSpeed) || impactSpeed <= 0.2 || observedSpeed == 0) {
            return Vec3.ZERO;
        }

        return new Vec3(
                serverObservedMovement.x * impactSpeed / observedSpeed,
                0,
                serverObservedMovement.z * impactSpeed / observedSpeed
        );
    }

    static float speedForDamage(Vec3 serverPlannedVelocity) {
        float impactSpeed = (float) serverPlannedVelocity.horizontalDistance();
        return Float.isFinite(impactSpeed) && impactSpeed > 0.2f ? impactSpeed : 0;
    }
}
