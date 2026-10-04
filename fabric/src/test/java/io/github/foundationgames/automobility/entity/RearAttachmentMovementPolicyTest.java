package io.github.foundationgames.automobility.entity;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RearAttachmentMovementPolicyTest {
    @Test
    void firstServerTickKeepsPullPhysics() {
        assertFalse(RearAttachmentMovementPolicy.shouldRealign(
                null,
                new Vec3(12, 64, -8),
                false
        ));
    }

    @Test
    void dimensionChangeAlwaysRealigns() {
        assertTrue(RearAttachmentMovementPolicy.shouldRealign(Vec3.ZERO, Vec3.ZERO, true));
        assertTrue(RearAttachmentMovementPolicy.shouldRealign(null, Vec3.ZERO, true));
    }

    @Test
    void exactlyFourHorizontalBlocksKeepsPullPhysics() {
        double threshold = RearAttachmentMovementPolicy.MAX_BETWEEN_TICK_HORIZONTAL_DISPLACEMENT;

        assertFalse(shouldRealign(Vec3.ZERO, new Vec3(threshold, 20, 0)));
    }

    @Test
    void smallestRepresentableDistanceOverFourBlocksRealigns() {
        double threshold = RearAttachmentMovementPolicy.MAX_BETWEEN_TICK_HORIZONTAL_DISPLACEMENT;

        assertTrue(shouldRealign(Vec3.ZERO, new Vec3(Math.nextUp(threshold), 0, 0)));
    }

    @Test
    void obviousBetweenTickTeleportsRealign() {
        assertTrue(shouldRealign(Vec3.ZERO, new Vec3(5, 0, 0)));
        assertTrue(shouldRealign(Vec3.ZERO, new Vec3(20, 0, 0)));
    }

    @Test
    void verticalGapBetweenTicksDoesNotAffectYawPolicy() {
        assertFalse(shouldRealign(Vec3.ZERO, new Vec3(0, 200, 0)));
        assertFalse(shouldRealign(
                new Vec3(4, Double.NEGATIVE_INFINITY, -2),
                new Vec3(4, Double.POSITIVE_INFINITY, -2)
        ));
    }

    @Test
    void nonFiniteHorizontalPositionsRealignFailSafe() {
        assertTrue(shouldRealign(new Vec3(Double.NaN, 0, 0), Vec3.ZERO));
        assertTrue(shouldRealign(Vec3.ZERO, new Vec3(0, 0, Double.POSITIVE_INFINITY)));
        assertTrue(RearAttachmentMovementPolicy.shouldRealign(
                null,
                new Vec3(Double.NEGATIVE_INFINITY, 0, 0),
                false
        ));
    }

    @Test
    void movementLaterInsideTheCurrentTickIsNotPartOfThePolicy() {
        var previousTickEnd = new Vec3(8, 64, -3);
        var currentTickStart = previousTickEnd;
        var simulatedCurrentTickEnd = currentTickStart.add(20, 40, -12);
        double threshold = RearAttachmentMovementPolicy.MAX_BETWEEN_TICK_HORIZONTAL_DISPLACEMENT;

        assertTrue(simulatedCurrentTickEnd.subtract(previousTickEnd).horizontalDistanceSqr()
                > threshold * threshold);
        assertFalse(shouldRealign(previousTickEnd, currentTickStart));
    }

    private static boolean shouldRealign(Vec3 previousServerTickEndPosition, Vec3 currentServerTickStartPosition) {
        return RearAttachmentMovementPolicy.shouldRealign(
                previousServerTickEndPosition,
                currentServerTickStartPosition,
                false
        );
    }
}
