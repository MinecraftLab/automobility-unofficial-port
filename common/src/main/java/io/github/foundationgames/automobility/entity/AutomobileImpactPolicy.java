package io.github.foundationgames.automobility.entity;

import net.minecraft.world.phys.Vec3;

final class AutomobileImpactPolicy {
    private AutomobileImpactPolicy() {
    }

    static float speedForDamage(Vec3 serverPlannedVelocity) {
        float impactSpeed = (float) serverPlannedVelocity.horizontalDistance();
        return Float.isFinite(impactSpeed) && impactSpeed > 0.2f ? impactSpeed : 0;
    }
}
