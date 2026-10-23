package io.github.foundationgames.automobility.automobile.render.obj;

/** Counts pending reservations and ALL live buffers, including fenced retirement. */
public final class RetainedMeshBudget {
    private final java.util.Set<Reservation> pending = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final long limit;
    private long reserved, live, peak;

    public RetainedMeshBudget(long limit) {
        if (limit <= 0) throw new IllegalArgumentException("Positive byte limit required");
        this.limit = limit;
    }

    public synchronized Reservation reserve(long upperBytes) {
        if (upperBytes <= 0 || upperBytes > limit - live - reserved) return null;
        reserved += upperBytes;
        peak = Math.max(peak, live + reserved);
        var result = new Reservation(upperBytes);
        pending.add(result);
        return result;
    }

    /** Frame boundary only: leaves allocated/fenced GPU bytes untouched. */
    public synchronized void cancelPending() {
        for (var reservation : pending.toArray(Reservation[]::new)) reservation.close();
    }

    public synchronized void freed(long bytes) {
        if (bytes <= 0 || bytes > live) throw new IllegalStateException("Invalid GPU retirement");
        live -= bytes;
    }

    public synchronized long liveBytes() { return live; }
    public synchronized long reservedBytes() { return reserved; }
    public synchronized long peakBytes() { return peak; }

    public final class Reservation implements AutoCloseable {
        private long remaining;
        private boolean closed;
        private Reservation(long remaining) { this.remaining = remaining; }

        public boolean permits(long bytes) {
            synchronized (RetainedMeshBudget.this) {
                return !closed && bytes > 0 && bytes <= remaining;
            }
        }

        public void allocated(long bytes) {
            synchronized (RetainedMeshBudget.this) {
                if (!permits(bytes)) throw new IllegalStateException("Allocation exceeds reserved GPU bytes");
                remaining -= bytes;
                reserved -= bytes;
                live += bytes;
            }
        }

        @Override public void close() {
            synchronized (RetainedMeshBudget.this) {
                if (!closed) { reserved -= remaining; remaining = 0; closed = true; pending.remove(this); }
            }
        }
    }
}
