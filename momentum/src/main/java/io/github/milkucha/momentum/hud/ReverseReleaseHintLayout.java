package io.github.milkucha.momentum.hud;

/** Pure visibility and viewport policy; this hint never changes driving state. */
final class ReverseReleaseHintLayout {
    static final int EDGE_MARGIN = 4;
    static final int MIN_WIDTH = 96;
    static final int MAX_WIDTH = 304;
    static final int MAIN_POD_CLEARANCE = 17;
    static final int ACTION_BAR_RESERVE = 84;
    static final int VERTICAL_PADDING = 8;
    static final int MAX_LINE_HEIGHT = 32;
    static final int MAX_LINE_COUNT = 8;

    private ReverseReleaseHintLayout() {}

    static boolean visible(boolean driver, boolean menuOpen, boolean hudEnabled,
                           boolean compactDashboard, boolean hintEnabled,
                           boolean waitingForRelease, boolean rawBrakeHeld,
                           boolean stationary) {
        return driver && !menuOpen && hudEnabled && compactDashboard
                && hintEnabled && waitingForRelease && rawBrakeHeld && stationary;
    }

    /** HUD-only threshold: this does not change the reverse guard or physics. */
    static boolean stationary(double signedEffectiveSpeed) {
        return Double.isFinite(signedEffectiveSpeed) && Math.abs(signedEffectiveSpeed) <= 0.001;
    }

    /** Returns null when the complete translated panel cannot fit safely. */
    static Panel resolve(int screenWidth, int screenHeight, int mainX, int mainY,
                         int requestedWidth, int lineHeight, int lineCount) {
        if (screenWidth <= 0 || screenHeight <= 0 || requestedWidth <= 0
                || lineHeight < 1 || lineHeight > MAX_LINE_HEIGHT
                || lineCount < 1 || lineCount > MAX_LINE_COUNT) return null;

        // Long arithmetic also covers hostile config values and synthetic tiny viewports.
        long maximumWidth = Math.min(MAX_WIDTH, (long) screenWidth - 2L * EDGE_MARGIN);
        if (maximumWidth < MIN_WIDTH) return null;
        long width = Math.clamp((long) requestedWidth, MIN_WIDTH, maximumWidth);
        long height = (long) lineHeight * lineCount + VERTICAL_PADDING;
        long maximumX = (long) screenWidth - width - EDGE_MARGIN;
        long x = Math.clamp((long) mainX, EDGE_MARGIN, maximumX);
        long bottom = Math.min((long) mainY - MAIN_POD_CLEARANCE,
                (long) screenHeight - ACTION_BAR_RESERVE);
        long y = bottom - height;

        if (x < EDGE_MARGIN || y < EDGE_MARGIN
                || x + width > (long) screenWidth - EDGE_MARGIN
                || y + height > (long) screenHeight - EDGE_MARGIN) return null;
        return new Panel((int) x, (int) y, (int) width, (int) height);
    }

    record Panel(int x, int y, int width, int height) {}
}
