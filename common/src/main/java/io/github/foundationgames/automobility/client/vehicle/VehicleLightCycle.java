package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;

/**
 * Bounded key intent for one current driver. Each step waits for an authoritative selected-mode
 * change; this helper never predicts entity state, changes effective light policy, or sends packets.
 */
public final class VehicleLightCycle {
    public static final int MAX_QUEUED_CLICKS = 16;
    public static final int REQUEST_TIMEOUT_TICKS = 40;
    public static final int MIN_REQUEST_INTERVAL_TICKS = 2;

    private Object world, vehicle, driver;
    private VehicleLightMode lastObserved, pendingMode;
    private long lastTick, pendingSinceTick, lastRequestTick;
    private boolean requestSent;
    private int queuedClicks;

    /** Observe selected tracked data even while a menu is open. Identity replacement drops intent. */
    public void observe(Object world, Object vehicle, Object driver, VehicleLightMode selected, long tick) {
        if (world == null || vehicle == null || driver == null || selected == null) {
            reset();
            return;
        }
        if (this.world != world || this.vehicle != vehicle || this.driver != driver) {
            reset();
            this.world = world;
            this.vehicle = vehicle;
            this.driver = driver;
            lastObserved = selected;
            lastTick = tick;
            return;
        }
        if (tick < lastTick) {
            clearRequests();
        } else if (pendingMode != null) {
            if (tick - pendingSinceTick >= REQUEST_TIMEOUT_TICKS) {
                // No request IDs exist in the legacy packet. Do not replay queued clicks after
                // rejection, loss, or an overdue reply: resume from the actual tracked selection.
                clearRequests();
            } else if (selected != lastObserved) {
                if (selected == pendingMode) pendingMode = null;
                else clearRequests();
            }
        } else if (selected != lastObserved) {
            clearRequests();
        }
        lastObserved = selected;
        lastTick = tick;
    }

    /**
     * Preserve individual clicks, but send at most one outstanding request. Poll with zero clicks
     * on ordinary driver ticks to drain the queue after confirmations. Excess clicks are bounded.
     */
    public VehicleLightMode cycle(Object world, Object vehicle, Object driver, VehicleLightMode selected,
                                  long tick, int clicks) {
        observe(world, vehicle, driver, selected, tick);
        if (this.world == null) return null;
        if (clicks > 0) queuedClicks += Math.min(clicks, MAX_QUEUED_CLICKS - queuedClicks);
        if (pendingMode != null || queuedClicks == 0
                || requestSent && tick - lastRequestTick < MIN_REQUEST_INTERVAL_TICKS) return null;

        pendingMode = nextMode(lastObserved);
        queuedClicks--;
        pendingSinceTick = lastRequestTick = tick;
        requestSent = true;
        return pendingMode;
    }

    /** Shared presentation intent; the server still owns the selected/effective mode. */
    public static VehicleLightMode nextMode(VehicleLightMode mode) {
        return switch (mode) {
            case OFF, AUTO -> VehicleLightMode.POSITION;
            case POSITION -> VehicleLightMode.LOW;
            case LOW -> VehicleLightMode.HIGH;
            case HIGH -> VehicleLightMode.AUTO;
        };
    }

    /** Explicit menu/profile/OFF actions supersede both the pending step and the queue. */
    public void reset() {
        world = vehicle = driver = null;
        lastObserved = null;
        lastTick = 0;
        clearRequests();
    }

    private void clearRequests() {
        pendingMode = null;
        queuedClicks = 0;
        pendingSinceTick = lastRequestTick = 0;
        requestSent = false;
    }
}
