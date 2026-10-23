package io.github.foundationgames.automobility.client.lighting;

/** Pure parent-to-child presentation on the native quad's 0-2 triangle diagonal. */
public final class HeadlightRefinement {
    private HeadlightRefinement() {}

    /** Affine geometry keeps dyadic child coordinates on the parent's GPU triangles. */
    public static boolean affine(HeadlightPatchGeometry geometry) {
        for (int axis = 0; axis < 3; axis++) {
            double a = coordinate(geometry, 0, axis), b = coordinate(geometry, 1, axis);
            double c = coordinate(geometry, 2, axis), d = coordinate(geometry, 3, axis);
            if (!Double.isFinite(a + b + c + d) || Math.abs(a + c - b - d) > 1e-6) return false;
        }
        return true;
    }

    private static double coordinate(HeadlightPatchGeometry geometry, int vertex, int axis) {
        return switch (axis) { case 0 -> geometry.x(vertex); case 1 -> geometry.y(vertex); default -> geometry.z(vertex); };
    }

    /** Bilinear child UVs preserve a parent raster field only for affine UVs. */
    public static boolean affineUv(float u0, float v0, float u1, float v1,
                                    float u2, float v2, float u3, float v3) {
        return Float.isFinite(u0 + u1 + u2 + u3) && Float.isFinite(v0 + v1 + v2 + v3)
                && Math.abs((double)u0 + u2 - u1 - u3) <= 1e-6
                && Math.abs((double)v0 + v2 - v1 - v3) <= 1e-6;
    }

    public static boolean canReplace(int tests, int accepted, int rays, int lamps) {
        if (lamps < 1 || lamps > 2) throw new IllegalArgumentException("Invalid refinement lamps");
        return tests >= 4 && accepted >= 4 && rays >= 4 * lamps;
    }

    public static float sample(float a, float b, float c, float d, double s, double t) {
        if (!Float.isFinite(a) || !Float.isFinite(b) || !Float.isFinite(c) || !Float.isFinite(d)
                || !Double.isFinite(s) || !Double.isFinite(t) || s < 0 || s > 1 || t < 0 || t > 1)
            throw new IllegalArgumentException("Invalid native parent field");
        return (float)(t <= s ? a * (1 - s) + b * (s - t) + c * t
                : a * (1 - t) + c * s + d * (t - s));
    }

    public static float child(float a, float b, float c, float d, int column, int row, int vertex) {
        if (column < 0 || column > 1 || row < 0 || row > 1 || vertex < 0 || vertex > 3)
            throw new IllegalArgumentException("Invalid dyadic child");
        double s = (column + (vertex == 1 || vertex == 2 ? 1 : 0)) * 0.5;
        double t = (row + (vertex >= 2 ? 1 : 0)) * 0.5;
        return sample(a, b, c, d, s, t);
    }

    public static float blend(float coarse, float fine, float weight) {
        if (!Float.isFinite(coarse) || !Float.isFinite(fine) || !Float.isFinite(weight) || weight < 0 || weight > 1)
            throw new IllegalArgumentException("Invalid refinement transition");
        return coarse + (fine - coarse) * weight;
    }
}
