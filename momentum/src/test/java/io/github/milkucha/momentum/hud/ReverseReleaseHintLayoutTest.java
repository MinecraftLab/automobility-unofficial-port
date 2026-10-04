package io.github.milkucha.momentum.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReverseReleaseHintLayoutTest {
    @Test
    void driverWaitingInEnabledCompactHudSeesHint() {
        assertTrue(ReverseReleaseHintLayout.visible(true, false, true, true, true, true, true, true));
    }

    @Test
    void passengerNeverSeesDriverHint() {
        assertFalse(ReverseReleaseHintLayout.visible(false, false, true, true, true, true, true, true));
    }

    @Test
    void openGuiHidesHint() {
        assertFalse(ReverseReleaseHintLayout.visible(true, true, true, true, true, true, true, true));
    }

    @Test
    void disabledHudHidesHint() {
        assertFalse(ReverseReleaseHintLayout.visible(true, false, false, true, true, true, true, true));
    }

    @Test
    void legacyLayoutHidesHint() {
        assertFalse(ReverseReleaseHintLayout.visible(true, false, true, false, true, true, true, true));
    }

    @Test
    void hintToggleHidesHint() {
        assertFalse(ReverseReleaseHintLayout.visible(true, false, true, true, false, true, true, true));
    }

    @Test
    void releasedGuardHidesHintImmediately() {
        assertFalse(ReverseReleaseHintLayout.visible(true, false, true, true, true, false, true, true));
    }

    @Test
    void unheldBrakeHidesHintEvenWhenGuardWaitsForValidInput() {
        assertFalse(ReverseReleaseHintLayout.visible(true, false, true, true, true, true, false, true));
    }

    @Test
    void movingVehicleHidesReverseInstructionWhileStillBraking() {
        assertFalse(ReverseReleaseHintLayout.visible(true, false, true, true, true, true, true, false));
    }

    @Test
    void signedZeroAndInclusiveHudThresholdAreStationary() {
        assertTrue(ReverseReleaseHintLayout.stationary(0.0));
        assertTrue(ReverseReleaseHintLayout.stationary(-0.0));
        assertTrue(ReverseReleaseHintLayout.stationary(0.001));
        assertTrue(ReverseReleaseHintLayout.stationary(-0.001));
    }

    @Test
    void movingSpeedsOutsideHudThresholdAreNotStationary() {
        assertFalse(ReverseReleaseHintLayout.stationary(0.0011));
        assertFalse(ReverseReleaseHintLayout.stationary(-0.0011));
        assertFalse(ReverseReleaseHintLayout.stationary(Double.MAX_VALUE));
        assertFalse(ReverseReleaseHintLayout.stationary(-Double.MAX_VALUE));
    }

    @Test
    void invalidEffectiveSpeedHidesHintRatherThanSuggestingARelease() {
        for (double speed : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertFalse(ReverseReleaseHintLayout.stationary(speed));
            assertFalse(ReverseReleaseHintLayout.visible(true, false, true, true, true, true,
                    true, ReverseReleaseHintLayout.stationary(speed)));
        }
    }

    @Test
    void everyVisibilityCombinationRequiresAllConditions() {
        for (int mask = 0; mask < 256; mask++) {
            assertEquals(mask == 253, ReverseReleaseHintLayout.visible(
                    (mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0,
                    (mask & 8) != 0, (mask & 16) != 0, (mask & 32) != 0,
                    (mask & 64) != 0, (mask & 128) != 0),
                    "Visibility mask " + mask);
        }
    }

    @Test
    void normalPanelStaysAboveVanillaActionBar() {
        assertEquals(new ReverseReleaseHintLayout.Panel(12, 378, 240, 18),
                ReverseReleaseHintLayout.resolve(854, 480, 12, 451, 240, 10, 1));
    }

    @Test
    void mainPodClearanceTakesPriorityWhenDashboardIsHigher() {
        ReverseReleaseHintLayout.Panel panel = ReverseReleaseHintLayout.resolve(
                854, 480, 12, 120, 240, 10, 1);
        assertEquals(new ReverseReleaseHintLayout.Panel(12, 85, 240, 18), panel);
        assertContained(panel, 854, 480, 120);
    }

    @Test
    void leftAndRightEdgesClampFullPanelInsideViewport() {
        assertEquals(4, ReverseReleaseHintLayout.resolve(320, 240, -100, 220, 200, 10, 1).x());
        assertEquals(116, ReverseReleaseHintLayout.resolve(320, 240, 10_000, 220, 200, 10, 1).x());
    }

    @Test
    void topDockHasNoSpaceAndHidesInsteadOfOverlapping() {
        assertNull(ReverseReleaseHintLayout.resolve(854, 480, 12, 13, 240, 10, 1));
        assertNull(ReverseReleaseHintLayout.resolve(854, 480, 12, 0, 240, 10, 1));
    }

    @Test
    void narrowViewportBoundsRequestedWidth() {
        assertEquals(new ReverseReleaseHintLayout.Panel(4, 98, 192, 18),
                ReverseReleaseHintLayout.resolve(200, 200, 190, 150, 500, 10, 1));
    }

    @Test
    void minimumViewportWidthStillContainsWholePanel() {
        ReverseReleaseHintLayout.Panel panel = ReverseReleaseHintLayout.resolve(
                104, 200, 0, 150, 1, 10, 1);
        assertEquals(new ReverseReleaseHintLayout.Panel(4, 98, 96, 18), panel);
        assertNull(ReverseReleaseHintLayout.resolve(103, 200, 0, 150, 96, 10, 1));
    }

    @Test
    void insufficientVerticalSpaceHidesHint() {
        assertNull(ReverseReleaseHintLayout.resolve(320, 105, 4, 100, 100, 10, 1));
        assertEquals(new ReverseReleaseHintLayout.Panel(4, 4, 100, 18),
                ReverseReleaseHintLayout.resolve(320, 106, 4, 100, 100, 10, 1));
    }

    @Test
    void longTranslationUsesItsFullLineHeightOrHides() {
        for (int lineCount = 1; lineCount <= 8; lineCount++) {
            ReverseReleaseHintLayout.Panel panel = ReverseReleaseHintLayout.resolve(
                    854, 480, 12, 451, 400, 10, lineCount);
            assertNotNull(panel);
            assertEquals(304, panel.width());
            assertEquals(10 * lineCount + 8, panel.height());
            assertContained(panel, 854, 480, 451);
        }
        assertNull(ReverseReleaseHintLayout.resolve(320, 180, 4, 150, 240, 32, 8));
    }

    @Test
    void invalidDimensionsAndTextMetricsFailClosed() {
        int[] invalidDimensions = {Integer.MIN_VALUE, -1, 0};
        for (int value : invalidDimensions) {
            assertNull(ReverseReleaseHintLayout.resolve(value, 480, 12, 451, 240, 10, 1));
            assertNull(ReverseReleaseHintLayout.resolve(854, value, 12, 451, 240, 10, 1));
            assertNull(ReverseReleaseHintLayout.resolve(854, 480, 12, 451, value, 10, 1));
        }
        int[] invalidLineHeights = {Integer.MIN_VALUE, -1, 0, 33, Integer.MAX_VALUE};
        for (int value : invalidLineHeights) {
            assertNull(ReverseReleaseHintLayout.resolve(854, 480, 12, 451, 240, value, 1));
        }
        int[] invalidLineCounts = {Integer.MIN_VALUE, -1, 0, 9, Integer.MAX_VALUE};
        for (int value : invalidLineCounts) {
            assertNull(ReverseReleaseHintLayout.resolve(854, 480, 12, 451, 240, 10, value));
        }
    }

    @Test
    void extremePositionsAndWidthsCannotOverflow() {
        for (int mainX : new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            ReverseReleaseHintLayout.Panel panel = ReverseReleaseHintLayout.resolve(
                    Integer.MAX_VALUE, Integer.MAX_VALUE, mainX, Integer.MAX_VALUE,
                    Integer.MAX_VALUE, 32, 8);
            assertNotNull(panel);
            assertEquals(304, panel.width());
            assertEquals(264, panel.height());
            assertContained(panel, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
        assertNull(ReverseReleaseHintLayout.resolve(854, 480, 12, Integer.MIN_VALUE,
                240, 10, 1));
    }

    @Test
    void sampledCornersAndTextSizesNeverOverlapReservedRegions() {
        for (int screenWidth : new int[] {104, 200, 320, 854, Integer.MAX_VALUE}) {
            for (int screenHeight : new int[] {106, 180, 480, Integer.MAX_VALUE}) {
                for (int mainX : new int[] {Integer.MIN_VALUE, 0, screenWidth, Integer.MAX_VALUE}) {
                    for (int mainY : new int[] {Integer.MIN_VALUE, 0, 35, screenHeight, Integer.MAX_VALUE}) {
                        for (int lines : new int[] {1, 4, 8}) {
                            ReverseReleaseHintLayout.Panel panel = ReverseReleaseHintLayout.resolve(
                                    screenWidth, screenHeight, mainX, mainY, Integer.MAX_VALUE, 32, lines);
                            if (panel != null) assertContained(panel, screenWidth, screenHeight, mainY);
                        }
                    }
                }
            }
        }
    }

    private static void assertContained(ReverseReleaseHintLayout.Panel panel,
                                        int screenWidth, int screenHeight, int mainY) {
        assertTrue(panel.x() >= 4);
        assertTrue(panel.y() >= 4);
        assertTrue(panel.width() >= 96 && panel.width() <= 304);
        assertTrue(panel.height() >= 9 && panel.height() <= 264);
        assertTrue((long) panel.x() + panel.width() <= (long) screenWidth - 4);
        assertTrue((long) panel.y() + panel.height() <= (long) screenHeight - 4);
        assertTrue((long) panel.y() + panel.height() <= (long) mainY - 17);
        assertTrue((long) panel.y() + panel.height() <= (long) screenHeight - 84);
    }
}
