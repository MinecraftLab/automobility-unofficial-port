package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.automobile.light.VehicleSignalMode;

/** Observes authoritative phase edges; never predicts a phase or catches up missed clicks. */
public final class VehicleSignalAudioObserver {
    public static final long MAX_OBSERVATION_GAP_NS = 250_000_000L;
    public enum Edge { NONE, TICK, TOCK }

    private Object world, vehicle, driver, soundEpoch;
    private VehicleSignalMode mode;
    private boolean lit, mixerMuted, initialized;
    private int volume;
    private long observedAt;

    public Edge observe(Object world, Object vehicle, Object driver, Object soundEpoch,
                        VehicleSignalMode mode, boolean lit, int volumePercent,
                        boolean paused, boolean mixerMuted, long nowNs) {
        int volume = Math.clamp(volumePercent, 0, 100);
        if (world == null || vehicle == null || driver == null || soundEpoch == null
                || mode == null || paused || volume == 0) {
            reset();
            return Edge.NONE;
        }
        long elapsed = nowNs - observedAt;
        boolean continuous = initialized && this.world == world && this.vehicle == vehicle
                && this.driver == driver && this.soundEpoch == soundEpoch
                && this.volume == volume && this.mixerMuted == mixerMuted
                && elapsed >= 0 && elapsed <= MAX_OBSERVATION_GAP_NS;
        Edge edge = Edge.NONE;
        if (continuous && mode != VehicleSignalMode.OFF
                && (this.mode == VehicleSignalMode.OFF ? lit : this.lit != lit)) {
            edge = lit ? Edge.TICK : Edge.TOCK;
        }
        this.world = world;
        this.vehicle = vehicle;
        this.driver = driver;
        this.soundEpoch = soundEpoch;
        this.mode = mode;
        this.lit = lit;
        this.volume = volume;
        this.mixerMuted = mixerMuted;
        this.observedAt = nowNs;
        this.initialized = true;
        return edge;
    }

    /** Drops identities too, so disconnected worlds and former drivers are not retained. */
    public void reset() {
        world = vehicle = driver = soundEpoch = null;
        mode = null;
        initialized = false;
    }
}
