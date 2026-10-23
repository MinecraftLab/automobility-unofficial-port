package io.github.foundationgames.automobility.client.lighting;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Render-thread cache of immutable geometry only; visibility and world state are never stored. */
public final class HeadlightGeometryCache<K, V> {
    private final int maxEntries;
    private final int maxWeight;
    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);
    private WeakReference<Object> world = new WeakReference<>(null);
    private Object models;
    private int weight;
    private long hits, misses, evictions, generationChanges;

    public HeadlightGeometryCache(int maxEntries, int maxWeight) {
        if (maxEntries <= 0 || maxWeight <= 0) throw new IllegalArgumentException("Invalid geometry cache bounds");
        this.maxEntries = maxEntries;
        this.maxWeight = maxWeight;
    }

    /** Identity epochs also release old sprites/model references after a world change or reload. */
    public void selectGeneration(Object world, Object models) {
        Objects.requireNonNull(world);
        Objects.requireNonNull(models);
        if (this.world.get() != world || this.models != models) {
            entries.clear();
            weight = 0;
            this.world = new WeakReference<>(world);
            this.models = models;
            generationChanges++;
        }
    }

    public V get(K key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) {
            misses++;
            return null;
        }
        hits++;
        return entry.value;
    }

    /** Oversized entries bypass admission; eviction never changes the renderer's traversal. */
    public boolean put(K key, V value, int entryWeight) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        if (entryWeight <= 0 || entryWeight > maxWeight) return false;
        Entry<V> previous = entries.remove(key);
        if (previous != null) weight -= previous.weight;
        while (!entries.isEmpty() && (entries.size() >= maxEntries || weight > maxWeight - entryWeight)) {
            var oldest = entries.entrySet().iterator();
            Entry<V> removed = oldest.next().getValue();
            oldest.remove();
            weight -= removed.weight;
            evictions++;
        }
        entries.put(key, new Entry<>(value, entryWeight));
        weight += entryWeight;
        return true;
    }

    public Snapshot snapshot() {
        return new Snapshot(entries.size(), weight, hits, misses, evictions, generationChanges);
    }

    public record Snapshot(int entries, int weight, long hits, long misses, long evictions, long generationChanges) {}
    private record Entry<V>(V value, int weight) {}
}
