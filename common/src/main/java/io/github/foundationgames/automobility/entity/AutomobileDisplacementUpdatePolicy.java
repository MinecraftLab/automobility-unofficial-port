package io.github.foundationgames.automobility.entity;

import net.minecraft.world.phys.Vec3;

final class AutomobileDisplacementUpdatePolicy {
    private AutomobileDisplacementUpdatePolicy() {
    }

    static boolean shouldUpdate(
            boolean firstTick,
            boolean teleportRefreshPending,
            Vec3 beforeSimulation,
            Vec3 afterSimulation,
            boolean yawChanged
    ) {
        return firstTick
                || teleportRefreshPending
                || afterSimulation.subtract(beforeSimulation).length() > 0
                || yawChanged;
    }
}
