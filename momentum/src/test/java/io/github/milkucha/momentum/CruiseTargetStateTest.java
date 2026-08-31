package io.github.milkucha.momentum;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CruiseTargetStateTest {
    @Test
    void storesAndAdjustsTargetInConfiguredSteps() {
        CruiseTargetState target = new CruiseTargetState();

        assertEquals(64f, target.set(64f, 8f, 150f));
        assertEquals(69f, target.adjust(5f, 8f, 150f));
        assertEquals(64f, target.adjust(-5f, 8f, 150f));
        assertTrue(target.hasStoredTarget());
    }

    @Test
    void clampsTargetAtBothLimits() {
        CruiseTargetState target = new CruiseTargetState();

        assertEquals(8f, target.set(2f, 8f, 150f));
        assertEquals(150f, target.adjust(500f, 8f, 150f));
        assertEquals(8f, target.adjust(-500f, 8f, 150f));
    }

    @Test
    void clearRemovesResumeTarget() {
        CruiseTargetState target = new CruiseTargetState();
        target.set(80f, 8f, 150f);

        target.clear();

        assertFalse(target.hasStoredTarget());
        assertEquals(0f, target.targetKmh());
        assertEquals(0f, target.adjust(5f, 8f, 150f));
    }
}
