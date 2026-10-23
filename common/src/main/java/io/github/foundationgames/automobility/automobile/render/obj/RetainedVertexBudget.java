package io.github.foundationgames.automobility.automobile.render.obj;

/** Bounds the unchanged triangle-to-degenerate-quad emitter, not optimized pair plans. */
public final class RetainedVertexBudget {
    private RetainedVertexBudget() {}
    public static long vertices(int triangleCorners) {
        return triangleCorners > 0 && triangleCorners % 3 == 0 ? triangleCorners / 3L * 4L : 0;
    }
    public static long upperBytes(int triangleCorners) {
        long vertices = vertices(triangleCorners);
        // Pinned extended ENTITY56 / vanilla ENTITY36. Retain headroom and a fixed pad.
        return vertices == 0 ? 0 : vertices * 80L + 4096;
    }
}
