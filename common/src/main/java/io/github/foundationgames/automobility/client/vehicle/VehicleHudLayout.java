package io.github.foundationgames.automobility.client.vehicle;

/** Bounded layout shared by the live status panel and its settings preview. */
public final class VehicleHudLayout {
    private VehicleHudLayout() {}

    public static Layout resolve(int screenWidth, int screenHeight, int contentWidth, VehicleHudPreferences preferences) {
        var settings = preferences.sanitized();
        int availableWidth = Math.max(1, screenWidth - 16);
        int logicalWidth = Math.max(72, contentWidth);
        float scale = Math.min(settings.scalePercent() / 100f, availableWidth / (float) logicalWidth);
        scale = Math.min(scale, Math.max(1, screenHeight - 16) / 23f);
        int width = (int) Math.ceil(logicalWidth * scale);
        int height = (int) Math.ceil(23 * scale);
        int x = settings.position() == VehicleHudPreferences.Position.TOP_RIGHT ? screenWidth - width - 8 : 8;
        // Leave room for the speedometer, all six keyboard hints and the narrow compact dashboard.
        int y = switch (settings.position()) {
            case TOP_LEFT -> 12;
            case TOP_RIGHT -> 148;
            case BOTTOM_LEFT -> screenHeight - height - (screenWidth < 426 ? 100 : 58);
        };
        return new Layout(Math.clamp(x, 0, Math.max(0, screenWidth - width)),
                Math.clamp(y, 0, Math.max(0, screenHeight - height)), logicalWidth, scale);
    }

    /** Preview anchors are relative to its canvas; gameplay HUD reservations do not apply here. */
    public static Layout resolvePreview(int canvasWidth, int canvasHeight, int contentWidth, VehicleHudPreferences preferences) {
        var settings = preferences.sanitized();
        int insetY = canvasHeight >= 43 ? 4 : 1;
        int logicalWidth = Math.max(72, contentWidth);
        float scale = Math.min(settings.scalePercent() / 100f, Math.max(1, canvasWidth - 8) / (float) logicalWidth);
        scale = Math.min(scale, Math.max(1, canvasHeight - insetY * 2) / 23f);
        int width = (int) Math.ceil(logicalWidth * scale);
        int height = (int) Math.ceil(23 * scale);
        int x = settings.position() == VehicleHudPreferences.Position.TOP_RIGHT ? canvasWidth - width - 4 : 4;
        int y = settings.position() == VehicleHudPreferences.Position.BOTTOM_LEFT ? canvasHeight - height - insetY : insetY;
        return new Layout(Math.clamp(x, 0, Math.max(0, canvasWidth - width)),
                Math.clamp(y, 0, Math.max(0, canvasHeight - height)), logicalWidth, scale);
    }

    public record Layout(int x, int y, int logicalWidth, float scale) {}
}
