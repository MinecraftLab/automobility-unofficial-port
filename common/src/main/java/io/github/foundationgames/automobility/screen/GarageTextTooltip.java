package io.github.foundationgames.automobility.screen;

/** Pixel bounds only; the screen supplies measurements from the active Minecraft font. */
final class GarageTextTooltip {
    private GarageTextTooltip() {}

    static boolean isClipped(int measuredWidth, int availableWidth) {
        return measuredWidth > availableWidth;
    }

    static boolean isHoveredClipped(int measuredWidth, int x, int y, int width, int height,
                                    int mouseX, int mouseY) {
        return isClipped(measuredWidth, width)
                && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }
}
