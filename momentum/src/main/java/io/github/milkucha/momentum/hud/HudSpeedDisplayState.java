package io.github.milkucha.momentum.hud;

/**
 * Tick-sampled stabilization of the speed number only.
 *
 * <p>No vehicle, controller, cruise-target or render state is modified. Context
 * objects are compared by reference so reused IDs cannot inherit another
 * world's or vehicle's display. Repeated renders must use {@link #valueFor}.
 */
final class HudSpeedDisplayState {
    private static final double HOLD_RADIUS_KMH = 0.7;
    private static final int CONFIRMATION_SAMPLES = 3;

    private Object world;
    private Object vehicle;
    private long lastTick;
    private boolean initialized;
    private int displayed;
    private int pendingCandidate;
    private int pendingSamples;

    int update(Object world, Object vehicle, long tick, double rawKmh) {
        if (world == null || vehicle == null || !Double.isFinite(rawKmh)) {
            reset();
            return 0;
        }

        double speedKmh = Math.max(0.0, Math.min(999.0, rawKmh));
        int nearest = nearest(speedKmh);
        if (!initialized || this.world != world || this.vehicle != vehicle) {
            return initialize(world, vehicle, tick, nearest);
        }
        // A duplicate tick neither changes the value nor consumes confirmation.
        if (tick == lastTick) return displayed;
        // Only consecutive ticks may confirm a pending adjacent change.
        // Duplicate handling above also makes lastTick + 1 safe at Long.MAX_VALUE:
        // no strictly later signed long exists there; any other tick is rollback.
        if (tick < lastTick || tick != lastTick + 1) {
            return initialize(world, vehicle, tick, nearest);
        }
        lastTick = tick;

        if (nearest == 0 || Math.abs(nearest - displayed) >= 2) {
            return commit(nearest);
        }
        if (speedKmh >= displayed - HOLD_RADIUS_KMH
                && speedKmh <= displayed + HOLD_RADIUS_KMH) {
            clearPending();
            return displayed;
        }

        if (pendingSamples == 0 || pendingCandidate != nearest) {
            pendingCandidate = nearest;
            pendingSamples = 1;
        } else {
            pendingSamples++;
        }
        return pendingSamples >= CONFIRMATION_SAMPLES ? commit(nearest) : displayed;
    }

    /** Read-only: a fallback is rounded immediately, but never changes state. */
    int valueFor(Object world, Object vehicle, double fallbackRawKmh) {
        return initialized && this.world == world && this.vehicle == vehicle
                ? displayed : nearest(fallbackRawKmh);
    }

    void reset() {
        world = null;
        vehicle = null;
        lastTick = 0;
        initialized = false;
        displayed = 0;
        clearPending();
    }

    private int initialize(Object world, Object vehicle, long tick, int nearest) {
        this.world = world;
        this.vehicle = vehicle;
        lastTick = tick;
        initialized = true;
        return commit(nearest);
    }

    private int commit(int nearest) {
        displayed = nearest;
        clearPending();
        return displayed;
    }

    private void clearPending() {
        pendingCandidate = 0;
        pendingSamples = 0;
    }

    private static int nearest(double speedKmh) {
        if (!Double.isFinite(speedKmh)) return 0;
        return (int) Math.round(Math.max(0.0, Math.min(999.0, speedKmh)));
    }
}
