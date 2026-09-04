package io.github.foundationgames.automobility.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutomobileClientInputPolicyTest {
    @Test
    void periodicClientInputRemainsFreshThroughTheTimeoutBoundary() {
        assertTrue(AutomobileClientInputPolicy.isFresh(100, 120, 20));
    }

    @Test
    void missingOrStaleClientInputIsRejected() {
        assertFalse(AutomobileClientInputPolicy.isFresh(Long.MIN_VALUE, 100, 20));
        assertFalse(AutomobileClientInputPolicy.isFresh(100, 121, 20));
    }

    @Test
    void backwardsGameTimeRequiresANewInputPacket() {
        assertFalse(AutomobileClientInputPolicy.isFresh(100, 99, 20));
    }
}
