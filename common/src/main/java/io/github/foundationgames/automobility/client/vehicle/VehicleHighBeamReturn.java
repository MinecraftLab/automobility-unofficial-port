package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;

/**
 * Ephemeral intent for the current driver's high-beam key. This never predicts entity state,
 * sends packets, or changes a saved setting; the caller sends the returned selection normally.
 */
public final class VehicleHighBeamReturn {
    public static final int REQUEST_TIMEOUT_TICKS = 40;

    private Object world;
    private Object vehicle;
    private Object driver;
    private VehicleLightMode lastObserved;
    private long lastTick;
    private VehicleLightMode returnMode;
    private VehicleLightMode pendingMode;
    private long pendingSinceTick;
    private boolean pendingAmbiguous;

    /**
     * Observe the authoritative selection every client tick, including while a screen is open.
     * Pass a missing identity when there is no current driver. Identities use reference equality,
     * so an entity replacement or a new world/player cannot inherit another driver's intent.
     */
    public void observe(Object world, Object vehicle, Object driver, VehicleLightMode observed, long tick) {
        if (world == null || vehicle == null || driver == null || observed == null) {
            reset();
            return;
        }
        if (this.world != world || this.vehicle != vehicle || this.driver != driver) {
            reset();
            this.world = world;
            this.vehicle = vehicle;
            this.driver = driver;
            lastObserved = observed;
            lastTick = tick;
            return;
        }
        if (tick < lastTick) clearIntent();

        if (pendingMode != null) {
            long elapsed = tick - pendingSinceTick;
            if (elapsed < 0 || elapsed >= REQUEST_TIMEOUT_TICKS) {
                // With no request IDs, a coalesced reply can only establish final convergence.
                // A rejected/lost request must not keep overriding the actual server selection.
                if (observed == pendingMode) clearPending();
                else clearIntent();
            } else if (!pendingAmbiguous && observed != lastObserved && observed == pendingMode) {
                clearPending();
            }
        }
        if (pendingMode == null && returnMode != null && observed != VehicleLightMode.HIGH) clearIntent();
        lastObserved = observed;
        lastTick = tick;
    }

    /**
     * Resolve one high-beam click. AUTO and POSITION return to their selection after HIGH;
     * other modes retain the existing OFF/LOW to HIGH and HIGH to LOW behavior. Missing driver context
     * returns null, which must not be sent. Explicit menu/light-button actions call reset first.
     */
    public VehicleLightMode toggleHigh(Object world, Object vehicle, Object driver, VehicleLightMode observed, long tick) {
        observe(world, vehicle, driver, observed, tick);
        if (this.world == null) return null;

        VehicleLightMode current = pendingMode == null ? observed : pendingMode;
        VehicleLightMode requested = current == VehicleLightMode.HIGH
                ? (returnMode == null ? VehicleLightMode.LOW : returnMode)
                : VehicleLightMode.HIGH;
        returnMode = requested == VehicleLightMode.HIGH
                && (current == VehicleLightMode.AUTO || current == VehicleLightMode.POSITION) ? current : null;

        // Several requests can share one data-tracker update. An earlier matching HIGH must
        // not acknowledge a later HIGH when an intermediate return selection is still in flight.
        pendingAmbiguous = pendingMode != null;
        pendingMode = requested;
        pendingSinceTick = tick;
        return requested;
    }

    /** Drop all intent and references on an explicit light action, dismount, or lost driver. */
    public void reset() {
        world = vehicle = driver = null;
        lastObserved = null;
        lastTick = 0;
        clearIntent();
    }

    private void clearIntent() {
        returnMode = null;
        clearPending();
    }

    private void clearPending() {
        pendingMode = null;
        pendingSinceTick = 0;
        pendingAmbiguous = false;
    }
}
