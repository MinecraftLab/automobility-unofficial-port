package io.github.milkucha.momentum.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DashboardHudLayoutTest {
    @Test
    void liveAndOffscreenOriginsAreClampedExactlyLikeTheFuelBridge() {
        assertEquals(new DashboardHudLayout.Origin(337, 436),
                DashboardHudLayout.resolveOrigin(854, 480, 90, 15,
                        -1, -1, 0.5f, 29));
        assertEquals(new DashboardHudLayout.Origin(0, 436),
                DashboardHudLayout.resolveOrigin(854, 480, 90, 15,
                        -1, -1, 1.0f, 29));
        assertEquals(new DashboardHudLayout.Origin(764, 465),
                DashboardHudLayout.resolveOrigin(854, 480, 90, 15,
                        10_000, 10_000, 0.5f, 29));
    }

    @Test
    void panelSizeUsesTheSameViewportBoundsAsTheFuelBridge() {
        assertEquals(new DashboardHudLayout.PanelSize(90, 15),
                DashboardHudLayout.resolvePanelSize(854, 90, 15));
        assertEquals(new DashboardHudLayout.PanelSize(312, 48),
                DashboardHudLayout.resolvePanelSize(320, 4096, 4096));
        assertEquals(new DashboardHudLayout.PanelSize(24, 1),
                DashboardHudLayout.resolvePanelSize(16, 1, 1));
    }

    @Test
    void deployedPresetKeepsEveryElementInsideDeclaredPanel() {
        DashboardHudLayout.Layout layout = DashboardHudLayout.resolve(90, 15, false);

        assertTrue(layout.renderable());
        assertEquals(8, layout.segmentCount());
        assertTrue(layout.iconX() >= 0 && layout.iconX() + 8 <= layout.width());
        assertTrue(layout.numberX() >= 0
                && layout.numberX() + layout.numberWidth() <= layout.width());
        assertTrue(layout.barY() >= 0
                && layout.barY() + layout.segmentHeight() <= layout.height());
        assertTrue(layout.segmentX(layout.segmentCount() - 1)
                + DashboardHudLayout.SEGMENT_WIDTH <= layout.width());
    }

    @Test
    void cruiseLayoutReservesPersistentTargetWithoutLeavingPanel() {
        DashboardHudLayout.Layout layout = DashboardHudLayout.resolve(90, 15, true);

        assertTrue(layout.renderable());
        assertEquals(5, layout.segmentCount());
        assertTrue(layout.auxiliaryX() + layout.auxiliaryWidth() <= layout.barX());
        assertTrue(layout.segmentX(layout.segmentCount() - 1)
                + DashboardHudLayout.SEGMENT_WIDTH <= layout.width());
    }

    @Test
    void fillAndCruiseMarkerClampAtBothEnds() {
        assertEquals(0, DashboardHudLayout.filledSegments(-5.0, 150.0f, 10));
        assertEquals(5, DashboardHudLayout.filledSegments(75.0, 150.0f, 10));
        assertEquals(10, DashboardHudLayout.filledSegments(300.0, 150.0f, 10));
        assertEquals(0, DashboardHudLayout.markerSegment(0.0f, 150.0f, 10));
        assertEquals(9, DashboardHudLayout.markerSegment(300.0f, 150.0f, 10));
    }

    @Test
    void unsafeTinyPanelFallsBackInsteadOfDrawingOutsideBounds() {
        DashboardHudLayout.Layout layout = DashboardHudLayout.resolve(30, 8, false);

        assertTrue(!layout.renderable());
        assertTrue(layout.segmentCount() >= 0);
    }

    @Test
    void reverseSpeedIsBaseSpeedWhileRealExcessRemainsBoost() {
        assertEquals(36.0, DashboardHudLayout.baseSpeedKmh(36.0, -36.0));
        assertEquals(36.0, DashboardHudLayout.baseSpeedKmh(54.0, -36.0));
        assertEquals(36.0, DashboardHudLayout.baseSpeedKmh(54.0, 36.0));
    }
}
