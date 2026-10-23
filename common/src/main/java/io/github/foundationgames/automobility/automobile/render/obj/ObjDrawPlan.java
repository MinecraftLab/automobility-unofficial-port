package io.github.foundationgames.automobility.automobile.render.obj;

import java.util.Arrays;

/** Immutable, bake-local standard quad indices. Never changes or retains a global mesh. */
final class ObjDrawPlan {
    private static final ObjDrawPlan NONE = new ObjDrawPlan(new int[0], 0);
    private final int[] corners;
    private final int pairs;

    private ObjDrawPlan(int[] corners, int pairs) {
        this.corners = corners;
        this.pairs = pairs;
    }

    boolean hasPairs() { return pairs != 0; }
    int pairCount() { return pairs; }
    int cornerCount() { return corners.length; }
    int corner(int index) { return corners[index]; }

    static ObjDrawPlan compile(BakedObj mesh) {
        int count = mesh.idVertex().length;
        if (count < 6 || count > 1_000_000 || count % 3 != 0
                || mesh.idNormal().length != count || mesh.idUv().length != count
                || !valid(mesh.idVertex(), mesh.vertex(), 3)
                || !valid(mesh.idNormal(), mesh.normal(), 3)
                || !valid(mesh.idUv(), mesh.uv(), 2)) return NONE;
        int[] result = new int[count / 3 * 4];
        int written = 0, pairs = 0;
        for (int a = 0; a < count; a += 3) {
            int d = a + 5 < count ? otherCorner(mesh, a) : -1;
            result[written++] = a;
            result[written++] = a + 1;
            result[written++] = a + 2;
            result[written++] = d >= 0 ? d : a + 2;
            if (d >= 0) { pairs++; a += 3; }
        }
        return pairs == 0 ? NONE : new ObjDrawPlan(Arrays.copyOf(result, written), pairs);
    }

    private static boolean valid(int[] indices, float[] values, int width) {
        if (values.length % width != 0) return false;
        int count = values.length / width;
        for (int index : indices) if (index < 0 || index >= count) return false;
        return true;
    }

    /** The second triangle must be a cyclic rotation of [a+2,d,a], never reversed. */
    private static int otherCorner(BakedObj mesh, int a) {
        for (int offset = 0; offset < 3; offset++) {
            int c = a + 3 + offset;
            int d = a + 3 + (offset + 1) % 3;
            int first = a + 3 + (offset + 2) % 3;
            if (!sameCorner(mesh, a + 2, c) || !sameCorner(mesh, a, first)) continue;
            int[] quad = {a, a + 1, a + 2, d};
            for (int i = 0; i < 4; i++) {
                if (!finite(mesh, quad[i]) || !sameNormal(mesh, a, quad[i])) return -1;
                for (int j = 0; j < i; j++) if (samePosition(mesh, quad[i], quad[j])) return -1;
            }
            return d;
        }
        return -1;
    }

    private static boolean finite(BakedObj mesh, int corner) {
        return finite(mesh.vertex(), mesh.idVertex()[corner], 3)
                && finite(mesh.normal(), mesh.idNormal()[corner], 3)
                && finite(mesh.uv(), mesh.idUv()[corner], 2);
    }

    private static boolean finite(float[] values, int index, int width) {
        for (int i = index * width; i < (index + 1) * width; i++)
            if (!Float.isFinite(values[i])) return false;
        return true;
    }

    private static boolean sameCorner(BakedObj mesh, int a, int b) {
        return samePosition(mesh, a, b) && sameNormal(mesh, a, b)
                && same(mesh.uv(), mesh.idUv()[a], mesh.idUv()[b], 2);
    }

    private static boolean samePosition(BakedObj mesh, int a, int b) {
        return same(mesh.vertex(), mesh.idVertex()[a], mesh.idVertex()[b], 3);
    }

    private static boolean sameNormal(BakedObj mesh, int a, int b) {
        return same(mesh.normal(), mesh.idNormal()[a], mesh.idNormal()[b], 3);
    }

    private static boolean same(float[] values, int a, int b, int width) {
        for (int i = 0; i < width; i++)
            if (Float.floatToRawIntBits(values[a * width + i])
                    != Float.floatToRawIntBits(values[b * width + i])) return false;
        return true;
    }

    /** Only the current bake is cached; queued callbacks keep their own immutable plan. */
    static final class Cache {
        private BakedObj bake;
        private ObjDrawPlan plan;
        synchronized ObjDrawPlan forBake(BakedObj next) {
            if (next != bake) { plan = compile(next); bake = next; }
            return plan;
        }
    }
}
