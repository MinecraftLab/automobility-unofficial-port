package io.github.foundationgames.automobility.entity;

import net.minecraft.world.phys.Vec3;

public final class AutomobilePositionUpdatePolicy {
    private AutomobilePositionUpdatePolicy() {
    }

    static boolean positionChanged(Vec3 previousPosition, Vec3 currentPosition) {
        return isFinite(previousPosition)
                && isFinite(currentPosition)
                && !previousPosition.equals(currentPosition);
    }

    public static boolean exceedsRelativeEncoding(long encodedX, long encodedY, long encodedZ) {
        return outsideSignedShort(encodedX)
                || outsideSignedShort(encodedY)
                || outsideSignedShort(encodedZ);
    }

    private static boolean outsideSignedShort(long encodedDelta) {
        return encodedDelta < Short.MIN_VALUE || encodedDelta > Short.MAX_VALUE;
    }

    private static boolean isFinite(Vec3 position) {
        return position != null
                && Double.isFinite(position.x)
                && Double.isFinite(position.y)
                && Double.isFinite(position.z);
    }
}
