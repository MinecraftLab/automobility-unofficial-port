package io.github.foundationgames.automobility.client.vehicle;

/** Local presentation preferences only; no vehicle gameplay state belongs in this file. */
public record VehicleHudPreferences(boolean enabled, int scalePercent, Position position, int cameraDistanceBlocks) {
    /** Source compatibility for callers that configure only the existing HUD. */
    public VehicleHudPreferences(boolean enabled, int scalePercent, Position position) {
        this(enabled, scalePercent, position, VehicleCameraDistance.DEFAULT_BLOCKS);
    }

    public static VehicleHudPreferences defaults() {
        return new VehicleHudPreferences(true, 100, Position.TOP_LEFT);
    }

    public VehicleHudPreferences sanitized() {
        int scale = Math.clamp(Math.round(scalePercent / 25f) * 25, 75, 150);
        return new VehicleHudPreferences(enabled, scale, position == null ? Position.TOP_LEFT : position,
                VehicleCameraDistance.sanitize(cameraDistanceBlocks));
    }

    public VehicleHudPreferences withEnabled(boolean value) {
        return new VehicleHudPreferences(value, scalePercent, position, cameraDistanceBlocks).sanitized();
    }

    public VehicleHudPreferences nextScale() {
        return new VehicleHudPreferences(enabled, scalePercent >= 150 ? 75 : scalePercent + 25,
                position, cameraDistanceBlocks).sanitized();
    }

    public VehicleHudPreferences nextPosition() {
        Position[] values = Position.values();
        Position current = position == null ? Position.TOP_LEFT : position;
        return new VehicleHudPreferences(enabled, scalePercent, values[(current.ordinal() + 1) % values.length],
                cameraDistanceBlocks).sanitized();
    }

    public VehicleHudPreferences withCameraDistance(int blocks) {
        return new VehicleHudPreferences(enabled, scalePercent, position, blocks).sanitized();
    }

    public VehicleHudPreferences nextCameraDistance() {
        int current = VehicleCameraDistance.sanitize(cameraDistanceBlocks);
        return withCameraDistance(current >= VehicleCameraDistance.MAX_BLOCKS
                ? VehicleCameraDistance.DEFAULT_BLOCKS : current + VehicleCameraDistance.STEP_BLOCKS);
    }

    public enum Position { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT }
}
