package io.github.foundationgames.automobility.client.lighting;

import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Exact ray results for one synchronous small-lamp surface collection.
 * Discard this object after the collection: world state and source order/geometry
 * must stay fixed while it is used. Active blink phases do not affect admission.
 */
public final class VehicleSmallLampVisibility {
    private static final float MIN_INTENSITY = 1.0f / 255;
    private final int maxRays;
    private final BiPredicate<Vec3, Vec3> unobstructed;
    private final Map<Key, Boolean> visibility = new HashMap<>();
    private int rays;

    public VehicleSmallLampVisibility(int maxRays, BiPredicate<Vec3, Vec3> unobstructed) {
        if (maxRays < 0) throw new IllegalArgumentException("Negative visibility ray limit");
        this.maxRays = maxRays;
        this.unobstructed = Objects.requireNonNull(unobstructed, "unobstructed");
    }

    /**
     * Returns four source-bit masks, including zeros for blocked/ineligible points.
     * A null result rejects the entire quad before any callback if its unique
     * missing rays do not fit. Already cached results remain usable at the limit.
     */
    public int[] sample(List<VehicleSmallLampLighting.Light> lights, Vec3[] points, Vec3 normal) {
        Objects.requireNonNull(lights, "lights");
        Objects.requireNonNull(points, "points");
        Objects.requireNonNull(normal, "normal");
        if (lights.size() > VehicleSmallLampLighting.MAX_SOURCES || points.length != 4) {
            throw new IllegalArgumentException("At most ten sources and exactly four vertices required");
        }
        if (!normal.isFinite() || !Double.isFinite(normal.lengthSqr()) || normal.lengthSqr() <= 0) {
            throw new IllegalArgumentException("Finite nonzero surface normal required");
        }
        for (var light : lights) Objects.requireNonNull(light, "light");
        for (var point : points) {
            Objects.requireNonNull(point, "point");
            if (!point.isFinite()) throw new IllegalArgumentException("Finite receiver vertex required");
        }

        Key[][] eligible = new Key[4][lights.size()];
        Map<Key, Vec3> missing = new LinkedHashMap<>();
        for (int vertex = 0; vertex < 4; vertex++) {
            Vec3 point = points[vertex];
            for (int source = 0; source < lights.size(); source++) {
                var light = lights.get(source);
                if (!light.enabled() || light.potentialIntensity(point) < MIN_INTENSITY
                        || !(light.origin().subtract(point).dot(normal) > 0)) continue;
                Key key = new Key(source, point);
                eligible[vertex][source] = key;
                if (!visibility.containsKey(key)) missing.putIfAbsent(key, light.origin());
            }
        }
        if (missing.size() > maxRays - rays) return null;
        for (var request : missing.entrySet()) {
            Key key = request.getKey();
            // Count the actual request even if native clipping throws; a retry cannot exceed the cap.
            rays++;
            visibility.put(key, unobstructed.test(request.getValue(), key.receiver()));
        }

        int[] masks = new int[4];
        for (int vertex = 0; vertex < 4; vertex++) {
            for (int source = 0; source < lights.size(); source++) {
                Key key = eligible[vertex][source];
                if (key != null && Boolean.TRUE.equals(visibility.get(key))) masks[vertex] |= 1 << source;
            }
        }
        return masks;
    }

    public int rays() { return rays; }

    // Vec3 equality preserves exact double endpoints, including negative and far-world coordinates.
    private record Key(int sourceIndex, Vec3 receiver) {}
}
