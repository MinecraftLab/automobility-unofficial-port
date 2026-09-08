package io.github.foundationgames.automobility.entity;

import net.minecraft.world.phys.Vec3;

final class AutomobileDisplacementUpdatePolicy {
    private AutomobileDisplacementUpdatePolicy() {
    }

    static boolean shouldUpdate(
            boolean firstTick,
            boolean packetPositionRefreshPending,
            Vec3 beforeSimulation,
            Vec3 afterSimulation,
            boolean yawChanged
    ) {
        return firstTick
                || packetPositionRefreshPending
                || afterSimulation.subtract(beforeSimulation).length() > 0
                || yawChanged;
    }
}
