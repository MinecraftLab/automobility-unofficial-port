package io.github.foundationgames.automobility.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReverseBrakeGuardTest {
    @Test
    void aFreshBackwardPressAtRestOrWhileAlreadyReversingCanReverse() {
        for (double speed : new double[]{0, -0.1, -0.25}) {
            var guard = new ReverseBrakeGuard();
            guard.acceptBaseInput(false, true, true, speed);
            assertTrue(guard.brakeHeld());
            assertFalse(guard.waitingForRelease());
            assertEquals(-0.25f, guard.speedFloor());
        }
    }

    @Test
    void firstExtraPacketBeforeBasePacketDoesNotInventAnInheritedHeldPedal() {
        for (boolean baseBackward : new boolean[]{false, true}) {
            var guard = new ReverseBrakeGuard();
            // No base packet has established valid input yet. Momentum still keeps its own
            // packet state, which is sampled again after the first accepted base envelope.
            guard.acceptAdditionalBrake(true, false, 0);
            assertFalse(guard.brakeHeld());
            guard.acceptBaseInput(false, baseBackward, true, 0);
            guard.acceptAdditionalBrake(true, true, 0);
            assertTrue(guard.brakeHeld());
            assertFalse(guard.waitingForRelease());
            assertEquals(-0.25f, guard.speedFloor());
        }
    }

    @Test
    void firstBasePacketBeforeExtraPacketHasTheSameStationaryReverseOutcome() {
        for (boolean baseBackward : new boolean[]{false, true}) {
            var guard = new ReverseBrakeGuard();
            guard.acceptBaseInput(false, baseBackward, true, 0);
            guard.acceptAdditionalBrake(true, true, 0);
            assertTrue(guard.brakeHeld());
            assertFalse(guard.waitingForRelease());
            assertEquals(-0.25f, guard.speedFloor());
        }
    }

    @Test
    void anInvalidFirstBaseSampleDoesNotPreventTheFirstValidStationaryReverse() {
        var guard = new ReverseBrakeGuard();
        guard.acceptBaseInput(false, true, false, 0);
        guard.acceptBaseInput(false, true, true, 0);
        assertFalse(guard.waitingForRelease());
        assertEquals(-0.25f, guard.speedFloor());
    }

    @Test
    void backwardHeldDuringForwardTravelStopsAtZeroUntilRelease() {
        var guard = new ReverseBrakeGuard();
        float speed = 0.92f;
        guard.acceptBaseInput(false, true, true, speed);
        for (int tick = 0; tick < 100; tick++) {
            guard.sample(true, speed);
            speed = Math.max(speed - 0.15f, guard.speedFloor());
        }
        assertEquals(0, speed);
        assertTrue(guard.waitingForRelease());
        guard.acceptBaseInput(false, false, true, 0);
        guard.acceptBaseInput(false, true, true, 0);
        speed = Math.max(speed - 0.15f, guard.speedFloor());
        assertTrue(speed < 0);
    }

    @Test
    void emptyFuelMasksAndRefillCannotTurnTheSameHeldBrakeIntoReverse() {
        var guard = new ReverseBrakeGuard();
        guard.acceptBaseInput(false, true, true, 0.4);
        guard.sample(true, 0);
        // An unpowered engine masks the public entity input to false for several ticks.
        // Those movement callbacks are samples, not accepted physical input envelopes.
        for (int unpoweredTick = 0; unpoweredTick < 40; unpoweredTick++) {
            guard.sample(true, 0);
            assertTrue(guard.waitingForRelease());
            assertEquals(0, guard.speedFloor());
        }
        // The tank is refilled; a periodic packet repeats the unchanged held pedal.
        guard.acceptBaseInput(false, true, true, 0);
        assertTrue(guard.waitingForRelease());
        assertEquals(0, Math.max(0 - 0.15f, guard.speedFloor()));
        // Only the actual release and a new press authorize reverse propulsion.
        guard.acceptBaseInput(false, false, true, 0);
        guard.acceptBaseInput(false, true, true, 0);
        assertFalse(guard.waitingForRelease());
        assertEquals(-0.15f, Math.max(0 - 0.15f, guard.speedFloor()));
    }

    @Test
    void releaseAndRepressBetweenMovementSamplesIsPreserved() {
        var guard = new ReverseBrakeGuard();
        guard.acceptBaseInput(false, true, true, 0.5);
        guard.sample(true, 0);
        guard.acceptBaseInput(false, false, true, 0);
        guard.acceptBaseInput(false, true, true, 0);
        guard.sample(true, 0);
        assertFalse(guard.waitingForRelease());
    }

    @Test
    void releasingOnlyOneOfTwoBackwardSourcesDoesNotUnlockReverse() {
        var guard = new ReverseBrakeGuard();
        guard.acceptBaseInput(false, true, true, 0.5);
        guard.acceptAdditionalBrake(true, true, 0.5);
        guard.sample(true, 0);
        guard.acceptBaseInput(false, false, true, 0);
        assertTrue(guard.waitingForRelease());
        guard.acceptAdditionalBrake(false, true, 0);
        assertFalse(guard.waitingForRelease());
        guard.acceptAdditionalBrake(true, true, 0);
        assertFalse(guard.waitingForRelease());
    }

    @Test
    void pressingBothPedalsCannotTurnIntoReverseByReleasingOnlyForward() {
        var guard = new ReverseBrakeGuard();
        guard.acceptBaseInput(true, true, true, 0);
        guard.acceptBaseInput(false, true, true, 0);
        assertTrue(guard.waitingForRelease());
        assertEquals(0, guard.speedFloor());
    }

    @Test
    void contextInterruptionsCannotTurnHeldBrakingIntoReverse() {
        for (String reason : new String[]{"driver changed", "disconnect", "teleport", "tick gap", "input expired"}) {
            var guard = new ReverseBrakeGuard();
            guard.acceptBaseInput(false, true, true, 0.5);
            guard.sample(true, 0);
            guard.interrupt();
            assertFalse(guard.brakeHeld(), reason);
            assertEquals(0, guard.speedFloor(), reason);
            guard.acceptBaseInput(false, true, true, 0);
            assertTrue(guard.waitingForRelease(), reason);
            guard.acceptBaseInput(false, false, true, 0);
            guard.acceptBaseInput(false, true, true, 0);
            assertFalse(guard.waitingForRelease(), reason);
        }
    }

    @Test
    void expiryStopsCachedReversePropulsionAndRequiresRealNeutral() {
        var guard = new ReverseBrakeGuard();
        guard.acceptBaseInput(false, true, true, -0.2);
        assertFalse(guard.waitingForRelease());
        guard.sample(false, -0.2);
        assertFalse(guard.brakeHeld());
        assertTrue(guard.waitingForRelease());
        guard.acceptAdditionalBrake(false, false, 0);
        guard.acceptBaseInput(false, true, true, 0);
        assertTrue(guard.waitingForRelease());
        guard.acceptBaseInput(false, false, true, 0);
        guard.acceptBaseInput(false, true, true, 0);
        assertFalse(guard.waitingForRelease());
    }

    @Test
    void aNeutralSampleWhileInputIsInvalidIsNotARelease() {
        var guard = new ReverseBrakeGuard();
        guard.acceptBaseInput(false, true, true, 0.5);
        guard.acceptBaseInput(false, false, false, 0);
        guard.acceptBaseInput(false, true, true, 0);
        assertTrue(guard.waitingForRelease());
    }

    @Test
    void invalidNeutralCannotUnlockPreviouslyAuthorizedReverse() {
        var guard = new ReverseBrakeGuard();
        guard.acceptBaseInput(false, true, true, -0.25);
        guard.acceptBaseInput(false, false, false, 0);
        guard.acceptBaseInput(false, true, true, 0);
        assertTrue(guard.waitingForRelease());
    }

    @Test
    void invalidExtraNeutralCannotUnlockPreviouslyAuthorizedReverse() {
        var guard = new ReverseBrakeGuard();
        guard.acceptAdditionalBrake(true, true, -0.25);
        guard.acceptAdditionalBrake(false, false, 0);
        guard.acceptAdditionalBrake(true, true, 0);
        assertTrue(guard.waitingForRelease());
    }

    @Test
    void restoredHeldNbtCannotResumeUnpaidReverseIntent() {
        var guard = new ReverseBrakeGuard();
        guard.restoreUntrustedInput(false, true);
        guard.acceptBaseInput(false, true, true, 0);
        assertTrue(guard.waitingForRelease());
        guard.acceptBaseInput(false, false, true, 0);
        guard.acceptBaseInput(false, true, true, 0);
        assertFalse(guard.waitingForRelease());
    }

    @Test
    void invalidSpeedDoesNotAuthorizeReverse() {
        for (double speed : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            var guard = new ReverseBrakeGuard();
            guard.acceptBaseInput(false, true, true, speed);
            guard.sample(true, 0);
            assertTrue(guard.waitingForRelease());
        }
    }

    @Test
    void momentumDecayAlsoStopsAtZeroWithoutChangingItsMagnitude() {
        var guard = new ReverseBrakeGuard();
        float speed = 0.93f;
        guard.acceptAdditionalBrake(true, true, speed);
        for (int tick = 0; tick < 100; tick++) {
            guard.sample(true, speed);
            speed = Math.max(speed - 0.03f, guard.speedFloor());
        }
        assertEquals(0, speed);
        guard.acceptAdditionalBrake(false, true, 0);
        guard.acceptAdditionalBrake(true, true, 0);
        assertEquals(-0.03f, Math.max(speed - 0.03f, guard.speedFloor()));
    }
}
