package io.github.foundationgames.automobility.entity;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

/** Client-world terrain invalidation, independent of automobile simulation or packet authority. */
public final class AutomobileTerrainRevisions {
    private final Long2LongOpenHashMap chunks = new Long2LongOpenHashMap();
    private long revision;
    private long generation;
    private long missingChunkRevision;

    public void changed(int chunkX, int chunkZ) {
        chunks.put(key(chunkX, chunkZ), nextRevision());
    }

    public void unloaded(int chunkX, int chunkZ) {
        chunks.remove(key(chunkX, chunkZ));
        // An adjacent, now-missing chunk can still intersect a loaded automobile's scan.
        // Retain one shared tombstone instead of retaining every visited chunk forever.
        missingChunkRevision = nextRevision();
    }

    public void storageChanged() {
        // ClientChunkCache can drop or hide chunks when its radius/center changes without
        // invoking ClientLevel.unload. Invalidate those transitions without retaining old keys.
        chunks.clear();
        missingChunkRevision = nextRevision();
        generation++;
    }

    private long nextRevision() {
        if (revision == Long.MAX_VALUE) {
            // Reset as a generation, never let a wrapped negative counter disappear from max().
            chunks.clear();
            missingChunkRevision = 0;
            revision = 0;
            generation++;
        }
        return ++revision;
    }

    int trackedChunkCount() {
        return chunks.size();
    }

    private long revisionIn(int minX, int minZ, int maxX, int maxZ) {
        long newest = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                newest = Math.max(newest, chunks.getOrDefault(key(x, z), missingChunkRevision));
            }
        }
        return newest;
    }

    private static long key(int x, int z) {
        return (x & 0xffffffffL) | ((long) z << 32);
    }

    public interface Source {
        AutomobileTerrainRevisions automobility$terrainRevisions();
    }

    /** One cheap revision sample per automobile; no cached BlockStates or world references. */
    public static final class Sample {
        private static final int MAX_FOOTPRINT_CHUNKS = 64;
        private AutomobileTerrainRevisions source;
        private long worldRevision = -1;
        private long generation = -1;
        private long footprintRevision = -1;
        private int minX, minZ, maxX, maxZ;
        private boolean broadFootprint;
        private boolean invalid = true;

        public void invalidate() {
            invalid = true;
        }

        boolean shouldRefresh(AutomobileTerrainRevisions current, double x, double z, double scanRadius) {
            // Shape queries may consult neighboring blocks (fences, walls, etc.). Include that
            // one-block halo, and use a yaw-independent wheel radius for long addon frames.
            double radius = scanRadius + 1;
            int newMinX = ((int) Math.floor(x - radius)) >> 4;
            int newMinZ = ((int) Math.floor(z - radius)) >> 4;
            int newMaxX = ((int) Math.floor(x + radius)) >> 4;
            int newMaxZ = ((int) Math.floor(z + radius)) >> 4;
            long footprintChunks = ((long) newMaxX - newMinX + 1) * ((long) newMaxZ - newMinZ + 1);
            boolean newBroadFootprint = !Double.isFinite(radius) || radius < 1
                    || !Double.isFinite(x) || !Double.isFinite(z) || footprintChunks > MAX_FOOTPRINT_CHUNKS;
            long currentWorldRevision = current == null ? 0 : current.revision;
            long currentGeneration = current == null ? 0 : current.generation;
            boolean newSource = current != source;
            boolean newGeneration = currentGeneration != generation;
            boolean newFootprint = newMinX != minX || newMinZ != minZ || newMaxX != maxX || newMaxZ != maxZ
                    || newBroadFootprint != broadFootprint;
            if (!invalid && !newSource && !newGeneration && !newFootprint && currentWorldRevision == worldRevision) {
                return false;
            }

            // Unusually large/addon-defined wheelbases still invalidate correctly, but fall
            // back to the world revision instead of making the bookkeeping itself unbounded.
            long currentFootprintRevision = current == null ? 0 : newBroadFootprint ? currentWorldRevision
                    : current.revisionIn(newMinX, newMinZ, newMaxX, newMaxZ);
            boolean refresh = invalid || newSource || newGeneration || newFootprint || currentFootprintRevision != footprintRevision;
            source = current;
            generation = currentGeneration;
            worldRevision = currentWorldRevision;
            footprintRevision = currentFootprintRevision;
            minX = newMinX;
            minZ = newMinZ;
            maxX = newMaxX;
            maxZ = newMaxZ;
            broadFootprint = newBroadFootprint;
            invalid = false;
            return refresh;
        }
    }
}
