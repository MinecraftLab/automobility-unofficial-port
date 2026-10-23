package io.github.foundationgames.automobility.client.lighting;

import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** Render-thread memo; source identity never permits reuse of stale visibility. */
final class HeadlightPreparedCache<V> {
    private final int capacity;
    private final LinkedHashMap<Integer, Entry<V>> entries = new LinkedHashMap<>(16, .75f, true);
    private long hits, misses, builds;
    HeadlightPreparedCache(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Positive capacity required");
        this.capacity = capacity;
    }
    V get(int owner, Object source, List<HeadlightBeam> beams, float refinement) {
        Entry<V> entry = entries.get(owner);
        if (entry != null && entry.source == source
                && entry.refinement == Float.floatToRawIntBits(refinement) && same(entry.beams, beams)) {
            hits++; return entry.value;
        }
        misses++; return null;
    }
    void put(int owner, Object source, List<HeadlightBeam> beams, float refinement, V value) {
        if (source == null || value == null) throw new IllegalArgumentException("Owned immutable value required");
        entries.put(owner, new Entry<>(source, List.copyOf(beams), Float.floatToRawIntBits(refinement), value));
        builds++;
        while (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
    }
    void retain(Set<Integer> selected) { entries.keySet().removeIf(id -> !selected.contains(id)); }
    void clear() { entries.clear(); }
    Snapshot snapshot() { return new Snapshot(hits, misses, builds, entries.size()); }
    record Snapshot(long hits, long misses, long builds, int entries) {}
    private record Entry<V>(Object source, List<HeadlightBeam> beams, int refinement, V value) {}
    private static boolean same(List<HeadlightBeam> a, List<HeadlightBeam> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            var x = a.get(i); var y = b.get(i);
            if (!same(x.origin(), y.origin()) || !same(x.forward(), y.forward())
                    || !same(x.right(), y.right()) || !same(x.up(), y.up())
                    || !bits(x.range(), y.range()) || !bits(x.horizontalTangent(), y.horizontalTangent())
                    || !bits(x.verticalTangent(), y.verticalTangent())) return false;
        }
        return true;
    }
    private static boolean same(Vec3 a, Vec3 b) {
        return bits(a.x, b.x) && bits(a.y, b.y) && bits(a.z, b.z);
    }
    private static boolean bits(double a, double b) {
        return Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b);
    }
}
