package io.github.foundationgames.automobility.client.lighting;

import org.joml.Vector3fc;

/** Exact block-local bilinear samples, independent of lamp pose, world visibility and tint. */
public final class HeadlightPatchGeometry {
    private final double x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3;
    private final double s0, t0, step;
    private Uv uv;

    private HeadlightPatchGeometry(Point p0, Point p1, Point p2, Point p3, double s0, double t0, double step) {
        x0 = p0.x; y0 = p0.y; z0 = p0.z;
        x1 = p1.x; y1 = p1.y; z1 = p1.z;
        x2 = p2.x; y2 = p2.y; z2 = p2.z;
        x3 = p3.x; y3 = p3.y; z3 = p3.z;
        this.s0 = s0; this.t0 = t0; this.step = step;
    }

    public static HeadlightPatchGeometry create(Vector3fc p0, Vector3fc p1, Vector3fc p2, Vector3fc p3,
                                                 int column, int row, int divisions) {
        if (divisions <= 0 || column < 0 || column >= divisions || row < 0 || row >= divisions) {
            throw new IllegalArgumentException("Invalid quad subdivision");
        }
        double step = 1.0 / divisions, s0 = column * step, t0 = row * step;
        return new HeadlightPatchGeometry(
                sample(p0, p1, p2, p3, s0, t0),
                sample(p0, p1, p2, p3, s0 + step, t0),
                sample(p0, p1, p2, p3, s0 + step, t0 + step),
                sample(p0, p1, p2, p3, s0, t0 + step), s0, t0, step);
    }

    private static Point sample(Vector3fc p0, Vector3fc p1, Vector3fc p2, Vector3fc p3, double s, double t) {
        double x = 0, y = 0, z = 0;
        for (int q = 0; q < 4; q++) {
            double weight = weight(q, s, t);
            Vector3fc p = switch (q) { case 0 -> p0; case 1 -> p1; case 2 -> p2; default -> p3; };
            x += p.x() * weight;
            y += p.y() * weight;
            z += p.z() * weight;
        }
        return new Point(x, y, z);
    }

    public double x(int vertex) {
        return switch (vertex) { case 0 -> x0; case 1 -> x1; case 2 -> x2; case 3 -> x3; default -> throw new IndexOutOfBoundsException(); };
    }
    public double y(int vertex) {
        return switch (vertex) { case 0 -> y0; case 1 -> y1; case 2 -> y2; case 3 -> y3; default -> throw new IndexOutOfBoundsException(); };
    }
    public double z(int vertex) {
        return switch (vertex) { case 0 -> z0; case 1 -> z1; case 2 -> z2; case 3 -> z3; default -> throw new IndexOutOfBoundsException(); };
    }

    /** Called only after a successful visibility test. The returned arrays are never mutated. */
    public Uv uv(float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3) {
        if (uv == null) {
            float[] u = new float[4], v = new float[4];
            for (int i = 0; i < 4; i++) {
                double s = i == 1 || i == 2 ? s0 + step : s0;
                double t = i >= 2 ? t0 + step : t0;
                for (int q = 0; q < 4; q++) {
                    float w = (float)weight(q, s, t);
                    u[i] += switch (q) { case 0 -> u0; case 1 -> u1; case 2 -> u2; default -> u3; } * w;
                    v[i] += switch (q) { case 0 -> v0; case 1 -> v1; case 2 -> v2; default -> v3; } * w;
                }
            }
            uv = new Uv(u, v);
        }
        return uv;
    }

    /** A hot-cache lookup avoids unpacking the source quad's eight UV values again. */
    public Uv uv() { return uv; }
    public boolean hasUv() { return uv != null; }

    private static double weight(int q, double s, double t) {
        return switch (q) {
            case 0 -> (1 - s) * (1 - t);
            case 1 -> s * (1 - t);
            case 2 -> s * t;
            default -> (1 - s) * t;
        };
    }

    public record Uv(float[] u, float[] v) {}
    private record Point(double x, double y, double z) {}
}
