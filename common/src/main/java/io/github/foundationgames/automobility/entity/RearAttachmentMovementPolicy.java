package io.github.foundationgames.automobility.entity;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

final class RearAttachmentMovementPolicy {
    // Movement during a tick is legitimate vehicle physics; only a gap between ticks is discontinuous.
    static final double MAX_BETWEEN_TICK_HORIZONTAL_DISPLACEMENT = 4.0;

    private static final double MAX_BETWEEN_TICK_HORIZONTAL_DISPLACEMENT_SQUARED =
            MAX_BETWEEN_TICK_HORIZONTAL_DISPLACEMENT * MAX_BETWEEN_TICK_HORIZONTAL_DISPLACEMENT;

    private RearAttachmentMovementPolicy() {
    }

    static boolean shouldRealign(
            @Nullable Vec3 previousServerTickEndPosition,
            Vec3 currentServerTickStartPosition,
            boolean dimensionChanged
    ) {
        if (dimensionChanged || !isHorizontallyFinite(currentServerTickStartPosition)) {
            return true;
        }
        if (previousServerTickEndPosition == null) {
            return false;
        }
        if (!isHorizontallyFinite(previousServerTickEndPosition)) {
            return true;
        }

        double deltaX = currentServerTickStartPosition.x - previousServerTickEndPosition.x;
        double deltaZ = currentServerTickStartPosition.z - previousServerTickEndPosition.z;
        return deltaX * deltaX + deltaZ * deltaZ
                > MAX_BETWEEN_TICK_HORIZONTAL_DISPLACEMENT_SQUARED;
    }

    private static boolean isHorizontallyFinite(Vec3 position) {
        return Double.isFinite(position.x)
                && Double.isFinite(position.z);
    }
}
