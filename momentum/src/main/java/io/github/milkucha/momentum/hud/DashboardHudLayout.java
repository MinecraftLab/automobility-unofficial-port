package io.github.milkucha.momentum.hud;

final class DashboardHudLayout {
    static final int COMPACT_WIDTH = 104;
    static final int COMPACT_HEIGHT = 17;

    static final int ICON_X = 3;
    static final int ICON_Y = 5;
    static final int ICON_WIDTH = 8;
    static final int NUMBER_X = 13;
    static final int NUMBER_WIDTH = 20;
    static final int NUMBER_Y = 4;
    static final int UNIT_X = 36;
    static final int UNIT_Y = 6;
    static final int DIVIDER_X = 49;
    static final int DIVIDER_Y = 3;
    static final int DIVIDER_HEIGHT = 11;
    static final int BAR_X = 52;
    static final int BAR_Y = 5;
    static final int SEGMENT_COUNT = 10;
    static final int SEGMENT_WIDTH = 4;
    static final int SEGMENT_SPACING = 1;
    static final int SEGMENT_HEIGHT = 7;

    static final int TOP_POD_OFFSET_Y = -13;
    static final int CRUISE_OFFSET_X = 66;
    static final int CRUISE_WIDTH = 38;
    static final int CRUISE_HEIGHT = 11;
    static final int NARROW_VIEWPORT_WIDTH = 426;
    static final int NARROW_BOTTOM_MARGIN = 58;

    private DashboardHudLayout() {}

    static PanelSize resolvePanelSize(int screenWidth, int configuredWidth, int configuredHeight) {
        int maximumWidth = Math.max(24, screenWidth - 8);
        return new PanelSize(
                Math.clamp(configuredWidth, 24, maximumWidth),
                Math.clamp(configuredHeight, 1, 48));
    }

    static Origin resolveOrigin(int screenWidth, int screenHeight,
                                int width, int height,
                                int configuredX, int configuredY,
                                float xFraction, int marginBottom) {
        return resolveOrigin(screenWidth, screenHeight, width, height,
                configuredX, configuredY, xFraction, marginBottom, false);
    }

    static Origin resolveCompactOrigin(int screenWidth, int screenHeight,
                                       int width, int height,
                                       int configuredX, int configuredY,
                                       float xFraction, int marginBottom) {
        Origin resolved = resolveOrigin(screenWidth, screenHeight, width, height,
                configuredX, configuredY, xFraction, marginBottom, true);
        int maximumY = Math.max(0, screenHeight - Math.max(1, height));
        int minimumDockableY = Math.min(-TOP_POD_OFFSET_Y, maximumY);
        return new Origin(resolved.x(), Math.max(minimumDockableY, resolved.y()));
    }

    private static Origin resolveOrigin(int screenWidth, int screenHeight,
                                        int width, int height,
                                        int configuredX, int configuredY,
                                        float xFraction, int marginBottom,
                                        boolean raiseOnNarrowViewport) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        float safeFraction = Math.clamp(xFraction, 0.0f, 1.0f);
        int x = configuredX >= 0 ? configuredX
                : screenWidth - safeWidth - (int) (screenWidth * safeFraction);
        int responsiveBottomMargin = configuredY < 0 && raiseOnNarrowViewport
                && screenWidth < NARROW_VIEWPORT_WIDTH
                ? Math.max(Math.max(0, marginBottom), NARROW_BOTTOM_MARGIN)
                : Math.max(0, marginBottom);
        int y = configuredY >= 0 ? configuredY
                : screenHeight - safeHeight - responsiveBottomMargin;
        return new Origin(
                Math.clamp(x, 0, Math.max(0, screenWidth - safeWidth)),
                Math.clamp(y, 0, Math.max(0, screenHeight - safeHeight)));
    }

    static Origin resolveCruiseOrigin(Origin mainOrigin) {
        return new Origin(mainOrigin.x() + CRUISE_OFFSET_X,
                Math.max(0, mainOrigin.y() + TOP_POD_OFFSET_Y));
    }

    static Layout resolve(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        return new Layout(safeWidth, safeHeight,
                ICON_X, ICON_Y, NUMBER_X, NUMBER_WIDTH, NUMBER_Y,
                UNIT_X, UNIT_Y, DIVIDER_X, DIVIDER_Y, DIVIDER_HEIGHT,
                BAR_X, BAR_Y, SEGMENT_COUNT, SEGMENT_HEIGHT);
    }

    static int filledSegments(double speedKmh, float maxSpeedKmh, int segmentCount) {
        if (segmentCount <= 0 || !Double.isFinite(speedKmh)
                || !Float.isFinite(maxSpeedKmh) || maxSpeedKmh <= 0.0f) return 0;
        double ratio = Math.clamp(Math.max(0.0, speedKmh) / maxSpeedKmh, 0.0, 1.0);
        return Math.clamp((int) Math.round(ratio * segmentCount), 0, segmentCount);
    }

    static int markerSegment(float targetKmh, float maxSpeedKmh, int segmentCount) {
        if (segmentCount <= 0 || !Float.isFinite(targetKmh)
                || !Float.isFinite(maxSpeedKmh) || maxSpeedKmh <= 0.0f) return -1;
        int filled = filledSegments(targetKmh, maxSpeedKmh, segmentCount);
        return Math.clamp(Math.max(1, filled) - 1, 0, segmentCount - 1);
    }

    static double baseSpeedKmh(double effectiveSpeedKmh, double signedEngineSpeedKmh) {
        if (!Double.isFinite(effectiveSpeedKmh) || effectiveSpeedKmh <= 0.0) return 0.0;
        if (!Double.isFinite(signedEngineSpeedKmh)) return effectiveSpeedKmh;
        return Math.min(effectiveSpeedKmh, Math.abs(signedEngineSpeedKmh));
    }

    record Layout(int width, int height, int iconX, int iconY,
                  int numberX, int numberWidth, int numberY,
                  int unitX, int unitY,
                  int dividerX, int dividerY, int dividerHeight,
                  int barX, int barY, int segmentCount, int segmentHeight) {
        boolean renderable() {
            return width >= COMPACT_WIDTH && height >= COMPACT_HEIGHT;
        }

        int segmentX(int index) {
            return barX + index * (SEGMENT_WIDTH + SEGMENT_SPACING);
        }
    }

    record Origin(int x, int y) {}

    record PanelSize(int width, int height) {}
}
