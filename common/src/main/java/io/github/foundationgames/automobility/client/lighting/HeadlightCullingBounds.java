package io.github.foundationgames.automobility.client.lighting;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

/** Pure world-space bounds for the actual finite spotlight volumes, including their car body. */
public final class HeadlightCullingBounds {
    private static final double BASIS_TOLERANCE = 0.00001;

    private HeadlightCullingBounds() {}

    /**
     * The elliptical cross-section's support is a convex function of axial distance. Its
     * coordinate extrema therefore occur in the near or far plane. The caller supplies a
     * margin for complete surface quads and the small camera-space view-layering offset.
     * Unknown/non-rigid bases retain the caller's orientation-independent fallback.
     */
    public static AABB enclose(AABB body, List<HeadlightBeam> beams, double margin, AABB fallback) {
        Objects.requireNonNull(fallback, "fallback");
        if (!finite(body) || beams == null || !Double.isFinite(margin) || margin < 0) return fallback;
        if (beams.isEmpty()) return body;
        double minX = body.minX, minY = body.minY, minZ = body.minZ;
        double maxX = body.maxX, maxY = body.maxY, maxZ = body.maxZ;
        for (HeadlightBeam beam : beams) {
            if (beam == null || !rigid(beam.forward(), beam.right(), beam.up())) return fallback;
            // Intensity uses dot products. Dual axes also cover their small float-normalization error.
            double determinant = beam.forward().dot(beam.right().cross(beam.up()));
            Vec3 forward = beam.right().cross(beam.up()).scale(1 / determinant);
            Vec3 right = beam.up().cross(beam.forward()).scale(1 / determinant);
            Vec3 up = beam.forward().cross(beam.right()).scale(1 / determinant);
            for (int plane = 0; plane < 2; plane++) {
                double axial = plane == 0 ? 0 : beam.range();
                Vec3 center = beam.origin().add(forward.scale(axial));
                double horizontal = 0.15 + axial * beam.horizontalTangent();
                double vertical = 0.10 + axial * beam.verticalTangent();
                double extentX = Math.hypot(right.x * horizontal, up.x * vertical) + margin;
                double extentY = Math.hypot(right.y * horizontal, up.y * vertical) + margin;
                double extentZ = Math.hypot(right.z * horizontal, up.z * vertical) + margin;
                if (!finite(center) || !Double.isFinite(extentX) || !Double.isFinite(extentY) || !Double.isFinite(extentZ)) return fallback;
                minX = Math.min(minX, outwardLow(center.x - extentX));
                minY = Math.min(minY, outwardLow(center.y - extentY));
                minZ = Math.min(minZ, outwardLow(center.z - extentZ));
                maxX = Math.max(maxX, outwardHigh(center.x + extentX));
                maxY = Math.max(maxY, outwardHigh(center.y + extentY));
                maxZ = Math.max(maxZ, outwardHigh(center.z + extentZ));
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean rigid(Vec3 forward, Vec3 right, Vec3 up) {
        return finite(forward) && finite(right) && finite(up)
                && Math.abs(forward.lengthSqr() - 1) <= BASIS_TOLERANCE
                && Math.abs(right.lengthSqr() - 1) <= BASIS_TOLERANCE
                && Math.abs(up.lengthSqr() - 1) <= BASIS_TOLERANCE
                && Math.abs(forward.dot(right)) <= BASIS_TOLERANCE
                && Math.abs(forward.dot(up)) <= BASIS_TOLERANCE
                && Math.abs(right.dot(up)) <= BASIS_TOLERANCE;
    }

    private static double outwardLow(double value) {
        return Math.nextDown(value - 8 * Math.ulp(value) - 0.000000001);
    }

    private static double outwardHigh(double value) {
        return Math.nextUp(value + 8 * Math.ulp(value) + 0.000000001);
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static boolean finite(AABB box) {
        return box != null && Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ);
    }
}
