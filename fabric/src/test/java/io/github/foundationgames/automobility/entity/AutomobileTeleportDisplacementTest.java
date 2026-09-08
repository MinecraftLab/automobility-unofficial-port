package io.github.foundationgames.automobility.entity;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutomobileTeleportDisplacementTest {
    @Test
    void verticalTeleportKeepsTheBodyAtItsRelativeRideHeight() {
        var displacement = new AutomobileEntity.Displacement();
        var tilt = new Quaternionf().rotationXYZ(0.15f, -0.2f, 0.08f);
        displacement.snapTo(183, tilt);

        assertTrue(displacement.translateVerticalAfterTeleport(183, 200));
        assertEquals(200, displacement.getVertical(0), 1.0E-9);
        assertEquals(200, displacement.getVertical(1), 1.0E-9);

        var actualTilt = new Quaternionf();
        displacement.getAngular(1, actualTilt);
        assertQuaternionEquals(tilt, actualTilt);

        displacement.preTick();
        displacement.postTick();
        assertEquals(200, displacement.getVertical(1), 1.0E-9,
                "the translated target must not pull the model back to its pre-teleport Y");
    }

    @Test
    void forcedRefreshCanDiscardAnAbsoluteTargetFromAStaleSync() {
        var displacement = new AutomobileEntity.Displacement();
        displacement.readSyncData(syncData(183), false);
        assertTrue(displacement.translateVerticalAfterTeleport(183, 200));

        // Simulate a sync overwriting the absolute target between packet handling and entity tick.
        displacement.readSyncData(syncData(183), false);
        assertTrue(displacement.resetVerticalForTeleport(200));
        displacement.preTick();
        displacement.postTick();

        assertEquals(200, displacement.getVertical(0), 1.0E-9);
        assertEquals(200, displacement.getVertical(1), 1.0E-9);
    }

    @Test
    void invalidTeleportCoordinatesCannotPoisonRenderState() {
        var displacement = new AutomobileEntity.Displacement();
        displacement.snapTo(183, new Quaternionf());

        assertFalse(displacement.translateVerticalAfterTeleport(183, Double.NaN));
        assertEquals(183, displacement.getVertical(1), 1.0E-9);

        assertTrue(displacement.translateVerticalAfterTeleport(Double.NaN, 200));
        assertEquals(200, displacement.getVertical(0), 1.0E-9);
        assertEquals(200, displacement.getVertical(1), 1.0E-9);
    }

    private static FriendlyByteBuf syncData(double vertical) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeDouble(vertical);
        buffer.writeFloat(0);
        buffer.writeFloat(0);
        buffer.writeFloat(0);
        buffer.writeFloat(1);
        return buffer;
    }

    private static void assertQuaternionEquals(Quaternionf expected, Quaternionf actual) {
        assertEquals(expected.x(), actual.x(), 1.0E-6f);
        assertEquals(expected.y(), actual.y(), 1.0E-6f);
        assertEquals(expected.z(), actual.z(), 1.0E-6f);
        assertEquals(expected.w(), actual.w(), 1.0E-6f);
    }
}
