package io.github.foundationgames.automobility.screen;

/** Pure shared layout: drawing, focus targets and native inventory hit boxes use the same coordinates. */
public final class GarageUiLayout {
    public static final int PANEL_WIDTH = 608, PANEL_HEIGHT = 352;
    public static final int RAIL_WIDTH = 112, CONTENT_X = 136, CONTENT_WIDTH = 460;
    public static final int INVENTORY_X = 426, INVENTORY_Y = 228, HOTBAR_Y = 286, SLOT_PITCH = 18;
    public static final int MIN_VIEWPORT_WIDTH = PANEL_WIDTH + 12, MIN_VIEWPORT_HEIGHT = PANEL_HEIGHT + 6;

    private GarageUiLayout() {}

    public enum Mode { OVERVIEW, TUNING, DETAILS }

    public record Box(int x, int y, int width, int height) {
        public Box {
            if (width < 0 || height < 0) throw new IllegalArgumentException("Negative garage box size");
        }
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
        public boolean contains(Box other) {
            return other.x >= x && other.y >= y && other.right() <= right() && other.bottom() <= bottom();
        }
        public boolean intersects(Box other) {
            return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }
        public Box inset(int amount) {
            int padding = Math.max(0, Math.min(amount, Math.min(width, height) / 2));
            return new Box(x + padding, y + padding, width - padding * 2, height - padding * 2);
        }
    }

    public record Layout(boolean small, Mode mode, Box panel, Box header, Box rail, Box hero,
                         Box drawer, Box footer, Box motorCard, Box wheelCard, Box primaryAction,
                         Box inventory) {
        public Box at(int relativeX, int relativeY, int width, int height) {
            return new Box(panel.x + relativeX, panel.y + relativeY, width, height);
        }
        public boolean inventoryVisible() { return !small && mode == Mode.TUNING; }
        public Box statsTooltip() { return mode == Mode.OVERVIEW ? at(144,224,446,25) : at(430,54,160,mode==Mode.TUNING?98:132); }
        public Box installedParts() { return at(430,153,160,34); }
    }

    public static Layout compute(int guiWidth, int guiHeight, Mode mode) {
        if (mode == null) throw new IllegalArgumentException("Missing garage mode");
        int x = Math.max(0, (guiWidth - PANEL_WIDTH) / 2);
        int y = Math.max(0, (guiHeight - PANEL_HEIGHT) / 2);
        boolean small = guiWidth < MIN_VIEWPORT_WIDTH || guiHeight < MIN_VIEWPORT_HEIGHT;
        return new Layout(small, mode,
                new Box(x, y, PANEL_WIDTH, PANEL_HEIGHT),
                new Box(x + 12, y + 12, 584, 24),
                new Box(x + 12, y + 48, RAIL_WIDTH, 292),
                new Box(x + CONTENT_X, y + 48, CONTENT_WIDTH, mode == Mode.OVERVIEW ? 204 : 140),
                new Box(x + CONTENT_X, y + 188, CONTENT_WIDTH, 122),
                new Box(x + CONTENT_X, y + 318, CONTENT_WIDTH, 22),
                new Box(x + CONTENT_X, y + 262, 132, 48),
                new Box(x + 276, y + 262, 132, 48),
                new Box(x + 416, y + 262, 180, 48),
                new Box(x + INVENTORY_X, y + INVENTORY_Y, 162, HOTBAR_Y - INVENTORY_Y + 16));
    }
}
