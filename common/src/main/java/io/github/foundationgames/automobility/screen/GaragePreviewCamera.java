package io.github.foundationgames.automobility.screen;

/** Pure, immutable showroom controls. This state never changes a real driving camera. */
public record GaragePreviewCamera(float yawDegrees, float zoom, boolean rotating) {
    public static final float DEFAULT_YAW = 35f;
    public static final float DEFAULT_ZOOM = 1f, MIN_ZOOM = 1f, MAX_ZOOM = 1.65f, ZOOM_STEP = 0.1f;

    public GaragePreviewCamera {
        yawDegrees = normalizeYaw(yawDegrees);
        zoom = clampZoom(zoom);
    }

    public static GaragePreviewCamera initial() { return initial(true); }
    public static GaragePreviewCamera initial(boolean rotating) {
        return new GaragePreviewCamera(DEFAULT_YAW, DEFAULT_ZOOM, rotating);
    }

    public static float clampZoom(float zoom) {
        return Float.isFinite(zoom) ? Math.clamp(zoom, MIN_ZOOM, MAX_ZOOM) : DEFAULT_ZOOM;
    }

    /** Fractional wheel deltas stay fractional; no timer or automatic zoom animation is involved. */
    public static float zoomBySteps(float currentZoom, double steps) {
        float current = clampZoom(currentZoom);
        if (!Double.isFinite(steps)) return current;
        return (float) Math.clamp(current + steps * ZOOM_STEP, MIN_ZOOM, MAX_ZOOM);
    }

    public static float normalizeYaw(float yaw) {
        if (!Float.isFinite(yaw)) return DEFAULT_YAW;
        float normalized = yaw % 360f;
        return normalized < 0 ? normalized + 360f : normalized == 0 ? 0 : normalized;
    }

    public GaragePreviewCamera withYaw(float yaw) { return new GaragePreviewCamera(yaw, zoom, rotating); }
    public GaragePreviewCamera rotateBy(float degrees) {
        return Float.isFinite(degrees) ? withYaw(yawDegrees + degrees) : this;
    }
    public GaragePreviewCamera withZoom(float value) { return new GaragePreviewCamera(yawDegrees, value, rotating); }
    public GaragePreviewCamera stepZoom(double steps) { return withZoom(zoomBySteps(zoom, steps)); }
    public GaragePreviewCamera withRotating(boolean value) { return new GaragePreviewCamera(yawDegrees, zoom, value); }
    public GaragePreviewCamera resetZoom() { return withZoom(DEFAULT_ZOOM); }
}
