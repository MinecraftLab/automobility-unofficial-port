package io.github.foundationgames.automobility.screen;

/** Exact projected AABB fit for a static thumbnail; the rotating hero keeps its own framing. */
final class GarageVehicleThumbnailFit {
    static final float YAW_DEGREES = 35f;
    private static final float EDGE_MARGIN = 2f;

    private GarageVehicleThumbnailFit() {}

    record Fit(float scale, float centerX, float centerY, float centerZ) {}

    static Fit fit(GarageVehiclePreviewFit.Bounds bounds, int width, int height) {
        if (!bounds.valid()) bounds = new GarageVehiclePreviewFit.Bounds(-1, 0, -2, 1, 2, 2);
        float spanX = bounds.maxX() - bounds.minX(), spanY = bounds.maxY() - bounds.minY();
        float spanZ = bounds.maxZ() - bounds.minZ();
        double yaw = Math.toRadians(YAW_DEGREES);
        double pitch = Math.toRadians(-GarageVehiclePreviewFit.PITCH_DEGREES);
        double projectedWidth = Math.abs(Math.cos(yaw)) * spanX + Math.abs(Math.sin(yaw)) * spanZ;
        double projectedDepth = Math.abs(Math.sin(yaw)) * spanX + Math.abs(Math.cos(yaw)) * spanZ;
        double projectedHeight = Math.cos(pitch) * spanY + Math.sin(pitch) * projectedDepth;
        float scale = (float) Math.min(Math.max(1, width - 2 * EDGE_MARGIN) / Math.max(.01, projectedWidth),
                Math.max(1, height - 2 * EDGE_MARGIN) / Math.max(.01, projectedHeight));
        return new Fit(scale, (bounds.minX() + bounds.maxX()) * .5f,
                (bounds.minY() + bounds.maxY()) * .5f, (bounds.minZ() + bounds.maxZ()) * .5f);
    }
}
