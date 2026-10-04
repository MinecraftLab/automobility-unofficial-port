package io.github.foundationgames.automobility.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Geometry contracts only: actual glyph metrics, shaping and tooltip rendering require native UI checks. */
class GarageTextTooltipTest {
    @Test
    void clippingRequiresStrictlyMoreThanTheAvailableWidth() {
        for (int available : new int[] {36, 40, 42, 45, 47, 48, 118, 126, 160, 226, 232}) {
            assertFalse(GarageTextTooltip.isClipped(0, available));
            assertFalse(GarageTextTooltip.isClipped(available - 1, available));
            assertFalse(GarageTextTooltip.isClipped(available, available));
            assertTrue(GarageTextTooltip.isClipped(available + 1, available));
        }
    }

    @Test
    void theVisibleLabelBoxIncludesItsTopAndLeftButExcludesBottomAndRight() {
        assertTrue(hoverInput(64, 9, 125));
        assertTrue(hoverInput(64, 56, 125));
        assertTrue(hoverInput(64, 9, 133));
        assertTrue(hoverInput(64, 56, 133));
        assertFalse(hoverInput(64, 8, 125));
        assertFalse(hoverInput(64, 57, 125));
        assertFalse(hoverInput(64, 9, 124));
        assertFalse(hoverInput(64, 9, 134));
    }

    @Test
    void fittingTextNeverRequestsAClippingTooltipEvenWhileHovered() {
        for (int measured : new int[] {0, 1, 47, 48}) {
            for (int x = 9; x < 57; x++) {
                for (int y = 125; y < 134; y++) {
                    assertFalse(hoverInput(measured, x, y));
                }
            }
        }
    }

    @Test
    void hitTestingUsesTheClippedBoxRatherThanTheHiddenFullTextWidth() {
        assertTrue(hoverInput(500, 56, 128));
        assertFalse(hoverInput(500, 57, 128));
        assertFalse(hoverInput(500, 508, 128));
        assertTrue(GarageTextTooltip.isHoveredClipped(80, 193, 125, 47, 9, 239, 133));
        assertFalse(GarageTextTooltip.isHoveredClipped(80, 193, 125, 47, 9, 240, 133));
    }

    @Test
    void callerSuppliedFontHeightAndEmptyBoxesAreRespected() {
        assertTrue(GarageTextTooltip.isHoveredClipped(50, 20, 46, 36, 12, 20, 57));
        assertFalse(GarageTextTooltip.isHoveredClipped(50, 20, 46, 36, 12, 20, 58));
        assertFalse(GarageTextTooltip.isHoveredClipped(50, 20, 46, 0, 9, 20, 46));
        assertFalse(GarageTextTooltip.isHoveredClipped(50, 20, 46, 36, 0, 20, 46));
    }

    private static boolean hoverInput(int measuredWidth, int mouseX, int mouseY) {
        return GarageTextTooltip.isHoveredClipped(measuredWidth, 9, 125, 48, 9, mouseX, mouseY);
    }
}
