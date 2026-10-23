package io.github.foundationgames.automobility.client.lighting;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Matrix4d;

/** Pure clip-space correction for the verified Complementary eight-frame TAA sequence. */
public final class HeadlightProjectionJitter {
    private static final boolean PREFIX_ENABLED = Boolean.parseBoolean(System.getProperty("automobility.experimental.jitterPrefix", "true"));
    private static final ThreadLocal<PrefixCache> PREFIX = ThreadLocal.withInitial(PrefixCache::new);
    private static final float[] X = {0.125f, -0.125f, 0.625f, 0.375f, -0.625f, -0.875f, 0.375f, 0.875f};
    private static final float[] Y = {-0.375f, 0.375f, 0.125f, -0.625f, 0.625f, -0.125f, -0.875f, 0.875f};

    private HeadlightProjectionJitter() { }

    public record Offset(float x, float y) { }

    /** Matches lib/antialiasing/jitter.glsl: the values are already NDC offsets, without an extra factor of two. */
    public static Offset offset(int frameCounter, int jitterMode, int width, int height) {
        if (width <= 0 || height <= 0 || jitterMode < 0 || jitterMode > 3) {
            throw new IllegalArgumentException("Invalid TAA viewport or mode");
        }
        float scale = switch (jitterMode) {
            case 0 -> 0;
            case 1 -> 0.125f;
            case 2 -> 0.33f;
            default -> 1;
        };
        int phase = Math.floorMod(frameCounter, 8);
        return new Offset(X[phase] * scale / width, Y[phase] * scale / height);
    }

    /**
     * The submitted entity pose is camera-relative but precedes the gbuffer view rotation.
     * Therefore E = V^-1 P^-1 J P V M, with J adding offset * clip.w only to clip.x/y.
     * The original normal matrix, world-space visibility and collision geometry remain owned by the caller.
     */
    public static Matrix4f apply(Matrix4fc pose, Matrix4fc projection, Matrix4fc view,
                                 Offset offset, boolean active) {
        return apply(pose, projection, view, offset, active, PREFIX_ENABLED ? PREFIX.get() : null);
    }

    static Matrix4f apply(Matrix4fc pose, Matrix4fc projection, Matrix4fc view,
                         Offset offset, boolean active, PrefixCache cache) {
        if (!active || (offset.x() == 0 && offset.y() == 0)) {
            return new Matrix4f(pose);
        }
        if (!Float.isFinite(offset.x()) || !Float.isFinite(offset.y())) {
            throw new IllegalArgumentException("Non-finite TAA offset");
        }
        requireFinite(pose);
        Matrix4d prefix = cache == null ? prefix(projection, view, offset) : new Matrix4d(cache.get(projection, view, offset));
        Matrix4f result = new Matrix4f(prefix.mul(new Matrix4d(pose)));
        requireFinite(result);
        return result;
    }

    private static Matrix4d prefix(Matrix4fc projection, Matrix4fc view, Offset offset) {
        Matrix4d inverseProjection = inverse(projection);
        Matrix4d inverseView = inverse(view);
        Matrix4d jitter = new Matrix4d().m30(offset.x()).m31(offset.y());
        // Conjugation nearly cancels large projection/inverse terms. Keep those
        // intermediate operations in double precision before emitting float vertices.
        return inverseView.mul(inverseProjection).mul(jitter)
                .mul(new Matrix4d(projection)).mul(new Matrix4d(view));
    }

    /** One bounded entry, exact input snapshots; never retains a caller's mutable matrix or pose. */
    static final class PrefixCache {
        private final int[] projectionBits = new int[16], viewBits = new int[16];
        private int projectionProperties, viewProperties, offsetX, offsetY;
        private Matrix4d value;
        private int builds;

        private Matrix4d get(Matrix4fc projection, Matrix4fc view, Offset offset) {
            // A hit has all bits/properties of a previously validated input. A miss
            // goes through the original validation order, including null/error cases.
            if (value != null && projection != null && view != null
                    && projectionProperties == projection.properties() && viewProperties == view.properties()
                    && offsetX == Float.floatToRawIntBits(offset.x()) && offsetY == Float.floatToRawIntBits(offset.y())
                    && matches(projection, projectionBits) && matches(view, viewBits)) return value;
            Matrix4d built = prefix(projection, view, offset);
            snapshot(projection, projectionBits); snapshot(view, viewBits);
            projectionProperties = projection.properties(); viewProperties = view.properties();
            offsetX = Float.floatToRawIntBits(offset.x()); offsetY = Float.floatToRawIntBits(offset.y());
            value = built; builds++;
            return value;
        }

        int builds() { return builds; }

        private static boolean matches(Matrix4fc matrix, int[] bits) {
            for (int i=0;i<16;i++) if (Float.floatToRawIntBits(matrix.get(i>>>2, i&3)) != bits[i]) return false;
            return true;
        }

        private static void snapshot(Matrix4fc matrix, int[] bits) {
            for (int i=0;i<16;i++) bits[i] = Float.floatToRawIntBits(matrix.get(i>>>2, i&3));
        }
    }

    private static Matrix4d inverse(Matrix4fc matrix) {
        requireFinite(matrix);
        Matrix4d copy = new Matrix4d(matrix);
        double determinant = copy.determinant();
        if (!Double.isFinite(determinant) || determinant == 0) {
            throw new IllegalArgumentException("Singular TAA transform");
        }
        copy.invert();
        if (!copy.isFinite()) throw new IllegalArgumentException("Non-finite TAA inverse");
        return copy;
    }

    private static void requireFinite(Matrix4fc matrix) {
        if (matrix == null || !matrix.isFinite()) {
            throw new IllegalArgumentException("Non-finite TAA transform");
        }
    }
}
