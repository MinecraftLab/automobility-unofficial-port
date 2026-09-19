package io.github.foundationgames.automobility.entity;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DeferredDriverRotationTest {
    @Test
    void passengerSnapshotPrecedesRotationAndRetainsCameraInterpolation() {
        var queue = new DeferredDriverRotation();
        Object driver = new Object();
        var yaw = new YawState(20);

        for (int tick = 1; tick <= 3; tick++) {
            float previous = yaw.current;
            queue.queue(driver, tick, 6, false); // automobile tick
            yaw.commonTick(); // vanilla passenger.commonTick
            var rotation = queue.consume(driver, tick, true); // rideTick HEAD
            assertNotNull(rotation);
            yaw.rotate(rotation.yawDelta());
            assertEquals(previous, yaw.view(0), 0.0001f);
            assertEquals(previous + 3, yaw.view(0.5f), 0.0001f);
            assertEquals(previous + 6, yaw.view(1), 0.0001f);
        }
    }

    @Test
    void mouseLookKeepsItsImmediateDeltaAtEveryPartialTick() {
        var queue = new DeferredDriverRotation();
        Object driver = new Object();
        var yaw = new YawState(20);
        queue.queue(driver, 1, 6, false);
        yaw.commonTick();
        yaw.rotate(queue.consume(driver, 1, true).yawDelta());
        float start = yaw.view(0);
        float middle = yaw.view(0.5f);
        float end = yaw.view(1);

        yaw.mouseTurn(10); // Entity.turn adds mouse delta to both current and old yaw.
        assertEquals(start + 10, yaw.view(0), 0.0001f);
        assertEquals(middle + 10, yaw.view(0.5f), 0.0001f);
        assertEquals(end + 10, yaw.view(1), 0.0001f);
    }

    @Test
    void wrapBoundaryUsesTheShortTwoDegreeTurn() {
        var queue = new DeferredDriverRotation();
        Object driver = new Object();
        var yaw = new YawState(179);
        queue.queue(driver, 1, 2, false);
        yaw.commonTick();
        yaw.rotate(queue.consume(driver, 1, true).yawDelta());
        assertEquals(-179, yaw.current, 0.0001f);
        assertEquals(180, yaw.view(0.5f), 0.0001f);
        assertEquals(2, Mth.wrapDegrees(yaw.view(1) - yaw.view(0)), 0.0001f);
    }

    @Test
    void queuedTurnIsConsumedExactlyOnce() {
        var queue = new DeferredDriverRotation();
        Object driver = new Object();
        queue.queue(driver, 12, 4, false);
        var rotation = queue.consume(driver, 12, true);
        assertNotNull(rotation);
        assertEquals(4, rotation.yawDelta());
        assertFalse(rotation.lockedView());
        assertNull(queue.consume(driver, 12, true));
    }

    @Test
    void oldDriverOrSeatCannotTransferPendingRotationToAnotherRider() {
        var queue = new DeferredDriverRotation();
        Object driver = new Object();
        queue.queue(driver, 12, 4, false);
        assertNull(queue.consume(new Object(), 12, true));
        assertNull(queue.consume(driver, 12, true));

        queue.queue(driver, 13, 4, false);
        assertNull(queue.consume(driver, 13, false)); // no longer driver/alive/on this automobile
        assertNull(queue.consume(driver, 13, true));
    }

    @Test
    void missingRideTickCannotReplayThePreviousTicksTurn() {
        var queue = new DeferredDriverRotation();
        Object driver = new Object();
        queue.queue(driver, 12, 4, false);
        assertNull(queue.consume(driver, 13, true));
        assertNull(queue.consume(driver, 12, true));
    }

    @Test
    void dismountClearPreventsReplayEvenWhenRemountingDuringTheSameTick() {
        var queue = new DeferredDriverRotation();
        Object driver = new Object();
        queue.queue(driver, 12, 4, false);
        queue.clear();
        assertNull(queue.consume(driver, 12, true));
    }

    @Test
    void lockedViewIsHandedOffWithoutConvertingItsSmoothingToAnAdditiveTurn() {
        var queue = new DeferredDriverRotation();
        Object driver = new Object();
        queue.queue(driver, 12, 4, true);
        assertTrue(queue.consume(driver, 12, true).lockedView());
        queue.queue(driver, 13, -3, false);
        var next = queue.consume(driver, 13, true);
        assertEquals(-3, next.yawDelta());
        assertFalse(next.lockedView());
    }

    /** Replay only vanilla's yaw-history operations; integration tests verify the production hooks. */
    private static final class YawState {
        float old;
        float current;

        YawState(float yaw) { this.old = this.current = yaw; }
        void commonTick() { this.old = this.current; }
        void rotate(float delta) { this.current = Mth.wrapDegrees(this.current + delta); }
        void mouseTurn(float delta) { this.current += delta; this.old += delta; }
        float view(float partial) { return Mth.rotLerp(partial, this.old, this.current); }
    }
}
