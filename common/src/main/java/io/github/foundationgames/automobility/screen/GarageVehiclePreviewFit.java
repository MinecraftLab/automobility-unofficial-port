package io.github.foundationgames.automobility.screen;

/** Pure camera geometry. Bounds are the vertices emitted by the production automobile renderer. */
final class GarageVehiclePreviewFit {
    static final float PITCH_DEGREES = -18f;
    static final float GROUND_FRACTION = 0.74f;
    static final float TOP_MARGIN = 18f;
    static final float BOTTOM_MARGIN = 8f;
    static final float WIDTH_FRACTION = 0.82f;

    private GarageVehiclePreviewFit() {}

    record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        boolean valid() {
            return Float.isFinite(minX) && Float.isFinite(minY) && Float.isFinite(minZ)
                    && Float.isFinite(maxX) && Float.isFinite(maxY) && Float.isFinite(maxZ)
                    && maxX >= minX && maxY > minY && maxZ >= minZ
                    && (maxX > minX || maxZ > minZ);
        }
    }

    record Fit(float scale, float centerX, float groundY, float centerZ, float horizontalRadius) {}

    static Fit fit(Bounds bounds, int width, int height) {
        if (!bounds.valid()) bounds = new Bounds(-1, 0, -2, 1, 2, 2);
        float centerX = (bounds.minX + bounds.maxX) * 0.5f;
        float centerZ = (bounds.minZ + bounds.maxZ) * 0.5f;
        float radius = Math.max(0.01f, (float) Math.hypot((bounds.maxX - bounds.minX) * 0.5,
                (bounds.maxZ - bounds.minZ) * 0.5));
        float pitch = (float) Math.toRadians(-PITCH_DEGREES);
        float floorDepth = radius * (float) Math.sin(pitch);
        float topExtent = (bounds.maxY - bounds.minY) * (float) Math.cos(pitch) + floorDepth;
        float ground = height * GROUND_FRACTION;
        float fitWidth = Math.max(1, width * WIDTH_FRACTION) / (2 * radius);
        float fitAboveGround = Math.max(1, ground - TOP_MARGIN) / topExtent;
        float fitBelowGround = Math.max(1, height - BOTTOM_MARGIN - ground) / floorDepth;
        return new Fit(Math.min(fitWidth, Math.min(fitAboveGround, fitBelowGround)),
                centerX, bounds.minY, centerZ, radius);
    }
}
