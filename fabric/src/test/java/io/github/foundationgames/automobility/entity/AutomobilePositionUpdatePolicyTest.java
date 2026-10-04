package io.github.foundationgames.automobility.entity;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutomobilePositionUpdatePolicyTest {
    @Test
    void identicalPositionAndRotationOnlyUpdatesAreIgnored() {
        var position = new Vec3(4.5, 70, -2.25);
        assertFalse(AutomobilePositionUpdatePolicy.positionChanged(position, position));
        assertFalse(AutomobilePositionUpdatePolicy.positionChanged(position, new Vec3(4.5, 70, -2.25)));
    }

    @Test
    void horizontalAndVerticalPositionChangesAreReconciled() {
        var position = new Vec3(4.5, 70, -2.25);
        assertTrue(AutomobilePositionUpdatePolicy.positionChanged(position, position.add(0.25, 0, 0)));
        assertTrue(AutomobilePositionUpdatePolicy.positionChanged(position, position.add(0, 17, 0)));
    }

    @Test
    void invalidCoordinatesCannotRequestAReconciliation() {
        var position = new Vec3(4.5, 70, -2.25);
        assertFalse(AutomobilePositionUpdatePolicy.positionChanged(null, position));
        assertFalse(AutomobilePositionUpdatePolicy.positionChanged(position, new Vec3(Double.NaN, 70, 0)));
        assertFalse(AutomobilePositionUpdatePolicy.positionChanged(
                new Vec3(4.5, Double.POSITIVE_INFINITY, -2.25), position));
    }

    @Test
    void onlyDeltasOutsideVanillasRelativeRangeRequireAHardCorrection() {
        assertFalse(AutomobilePositionUpdatePolicy.exceedsRelativeEncoding(Short.MIN_VALUE, 0, Short.MAX_VALUE));
        assertTrue(AutomobilePositionUpdatePolicy.exceedsRelativeEncoding((long) Short.MIN_VALUE - 1, 0, 0));
        assertTrue(AutomobilePositionUpdatePolicy.exceedsRelativeEncoding(0, (long) Short.MAX_VALUE + 1, 0));
    }
}
