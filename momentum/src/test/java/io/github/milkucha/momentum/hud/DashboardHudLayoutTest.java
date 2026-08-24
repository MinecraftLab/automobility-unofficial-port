package io.github.milkucha.momentum.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DashboardHudLayoutTest {
    @Test
    void livePresetResolvesTheExactSharedClusterGeometry() {
        DashboardHudLayout.Origin main = DashboardHudLayout.resolveCompactOrigin(
                854, 480, 104, 17, -1, -1, 0.016f, 12);

        assertEquals(new DashboardHudLayout.Origin(737, 451), main);
        assertEquals(new DashboardHudLayout.Origin(803, 438),
                DashboardHudLayout.resolveCruiseOrigin(main));
    }

    @Test
    void offscreenExplicitOriginsAreClampedWithoutChangingTheirAnchorMode() {
        assertEquals(new DashboardHudLayout.Origin(0, 451),
                DashboardHudLayout.resolveCompactOrigin(854, 480, 104, 17,
                        -1, -1, 1.0f, 12));
        assertEquals(new DashboardHudLayout.Origin(750, 463),
                DashboardHudLayout.resolveCompactOrigin(854, 480, 104, 17,
                        10_000, 10_000, 0.016f, 12));
    }

    @Test
    void explicitTopEdgePositionKeepsCruisePodDockedWithoutOverlap() {
        DashboardHudLayout.Origin main = DashboardHudLayout.resolveCompactOrigin(
                854, 480, 104, 17, 100, 0, 0.016f, 12);
        DashboardHudLayout.Origin cruise = DashboardHudLayout.resolveCruiseOrigin(main);

        assertEquals(new DashboardHudLayout.Origin(100, 13), main);
        assertEquals(new DashboardHudLayout.Origin(166, 0), cruise);
        assertEquals(cruise.y() + DashboardHudLayout.CRUISE_HEIGHT + 2, main.y());
    }

    @Test
    void narrowViewportRaisesTheWholeClusterAboveTouchControls() {
        DashboardHudLayout.Origin main = DashboardHudLayout.resolveCompactOrigin(
                425, 240, 104, 17, -1, -1, 0.016f, 12);

        assertEquals(new DashboardHudLayout.Origin(315, 165), main);
        assertEquals(182, main.y() + DashboardHudLayout.COMPACT_HEIGHT);
        assertEquals(240 - DashboardHudLayout.NARROW_BOTTOM_MARGIN,
                main.y() + DashboardHudLayout.COMPACT_HEIGHT);
        assertEquals(new DashboardHudLayout.Origin(381, 152),
                DashboardHudLayout.resolveCruiseOrigin(main));
    }

    @Test
    void legacyOriginKeepsItsConfiguredBottomMarginOnNarrowViewports() {
        assertEquals(new DashboardHudLayout.Origin(315, 211),
                DashboardHudLayout.resolveOrigin(
                        425, 240, 104, 17, -1, -1, 0.016f, 12));
    }

    @Test
    void panelSizeUsesViewportBoundsAndTheNewPreset() {
        assertEquals(new DashboardHudLayout.PanelSize(104, 17),
                DashboardHudLayout.resolvePanelSize(854, 104, 17));
        assertEquals(new DashboardHudLayout.PanelSize(312, 48),
                DashboardHudLayout.resolvePanelSize(320, 4096, 4096));
        assertEquals(new DashboardHudLayout.PanelSize(24, 1),
                DashboardHudLayout.resolvePanelSize(16, 1, 1));
    }

    @Test
    void mainPanelUsesTheExactTenSegmentInstrumentLayout() {
        DashboardHudLayout.Layout layout = DashboardHudLayout.resolve(104, 17);

        assertTrue(layout.renderable());
        assertEquals(3, layout.iconX());
        assertEquals(5, layout.iconY());
        assertEquals(13, layout.numberX());
        assertEquals(4, layout.numberY());
        assertEquals(36, layout.unitX());
        assertEquals(6, layout.unitY());
        assertEquals(49, layout.dividerX());
        assertEquals(52, layout.barX());
        assertEquals(5, layout.barY());
        assertEquals(10, layout.segmentCount());
        assertEquals(97, layout.segmentX(9));
        assertEquals(101, layout.segmentX(9) + DashboardHudLayout.SEGMENT_WIDTH);
    }

    @Test
    void cruisePodDoesNotMutateOrResizeTheMainLayout() {
        DashboardHudLayout.Layout beforeCruise = DashboardHudLayout.resolve(104, 17);
        DashboardHudLayout.resolveCruiseOrigin(new DashboardHudLayout.Origin(737, 451));
        DashboardHudLayout.Layout duringCruise = DashboardHudLayout.resolve(104, 17);

        assertEquals(beforeCruise, duringCruise);
        assertEquals(10, duringCruise.segmentCount());
        assertEquals(104, duringCruise.width());
        assertEquals(17, duringCruise.height());
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
        DashboardHudLayout.Layout layout = DashboardHudLayout.resolve(30, 8);

        assertTrue(!layout.renderable());
        assertEquals(10, layout.segmentCount());
    }

    @Test
    void reverseSpeedIsBaseSpeedWhileRealExcessRemainsBoost() {
        assertEquals(36.0, DashboardHudLayout.baseSpeedKmh(36.0, -36.0));
        assertEquals(36.0, DashboardHudLayout.baseSpeedKmh(54.0, -36.0));
        assertEquals(36.0, DashboardHudLayout.baseSpeedKmh(54.0, 36.0));
    }
}
