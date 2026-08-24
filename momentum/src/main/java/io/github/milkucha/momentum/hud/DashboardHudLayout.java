package io.github.milkucha.momentum.hud;

final class DashboardHudLayout {
    static final int PANEL_INSET = 2;
    static final int ICON_WIDTH = 8;
    static final int NUMBER_AREA_WIDTH = 18;
    static final int UNIT_AREA_WIDTH = 11;
    static final int CRUISE_AREA_WIDTH = 16;
    static final int SEGMENT_WIDTH = 4;
    static final int SEGMENT_SPACING = 1;

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
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        float safeFraction = Math.clamp(xFraction, 0.0f, 1.0f);
        int x = configuredX >= 0 ? configuredX
                : screenWidth - safeWidth - (int) (screenWidth * safeFraction);
        int y = configuredY >= 0 ? configuredY
                : screenHeight - safeHeight - Math.max(0, marginBottom);
        return new Origin(
                Math.clamp(x, 0, Math.max(0, screenWidth - safeWidth)),
                Math.clamp(y, 0, Math.max(0, screenHeight - safeHeight)));
    }

    static Layout resolve(int width, int height, boolean cruiseActive) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        int iconX = PANEL_INSET;
        int iconY = Math.max(PANEL_INSET, (safeHeight - 7) / 2);
        int numberX = iconX + ICON_WIDTH + 2;
        int numberWidth = Math.min(NUMBER_AREA_WIDTH,
                Math.max(0, safeWidth - numberX - PANEL_INSET));
        int auxiliaryX = numberX + numberWidth + 2;
        int requestedAuxiliaryWidth = UNIT_AREA_WIDTH
                + (cruiseActive ? 1 + CRUISE_AREA_WIDTH : 0);
        int auxiliaryWidth = Math.min(requestedAuxiliaryWidth,
                Math.max(0, safeWidth - auxiliaryX - PANEL_INSET));
        int barX = auxiliaryX + auxiliaryWidth + 3;
        int barWidth = Math.max(0, safeWidth - barX - 3);
        int segmentCount = Math.max(0,
                (barWidth + SEGMENT_SPACING) / (SEGMENT_WIDTH + SEGMENT_SPACING));
        int actualBarWidth = segmentCount == 0 ? 0
                : segmentCount * SEGMENT_WIDTH + (segmentCount - 1) * SEGMENT_SPACING;
        int segmentHeight = Math.clamp(safeHeight - 8, 3, 7);
        int barY = Math.max(PANEL_INSET, (safeHeight - segmentHeight) / 2);
        return new Layout(safeWidth, safeHeight, iconX, iconY, numberX, numberWidth,
                auxiliaryX, auxiliaryWidth,
                barX + Math.max(0, (barWidth - actualBarWidth) / 2), barY,
                segmentCount, segmentHeight);
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
                  int numberX, int numberWidth, int auxiliaryX, int auxiliaryWidth,
                  int barX, int barY,
                  int segmentCount, int segmentHeight) {
        boolean renderable() {
            return width >= 48 && height >= 12 && numberWidth >= 12 && segmentCount >= 2;
        }

        int segmentX(int index) {
            return barX + index * (SEGMENT_WIDTH + SEGMENT_SPACING);
        }
    }

    record Origin(int x, int y) {}

    record PanelSize(int width, int height) {}
}
