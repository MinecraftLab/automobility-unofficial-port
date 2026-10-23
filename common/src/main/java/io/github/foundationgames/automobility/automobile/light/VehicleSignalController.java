package io.github.foundationgames.automobility.automobile.light;

import java.util.Objects;

/** One server-owned clock; clients consume the atomic packed snapshot and never advance it. */
public final class VehicleSignalController {
    public static final int HALF_PERIOD_TICKS = 10;
    public static final int LIT_FLAG = 4;
    public static final int TOGGLE_LEFT = 5, TOGGLE_RIGHT = 6, TOGGLE_HAZARD = 7;
    public static final int THREE_LEFT = 8, THREE_RIGHT = 9;
    public static final int COMFORT_ON_PHASES = 3;
    private VehicleSignalMode mode = VehicleSignalMode.OFF;
    private int phaseTicks;
    private boolean lit;
    private int comfortOnPhases;
    private final VehicleSignalAutoCancel automaticCancellation = new VehicleSignalAutoCancel();

    public VehicleSignalMode mode() { return mode; }
    public boolean lit() { return lit; }
    public int packed() { return mode.id() | (lit ? LIT_FLAG : 0); }
    public boolean comfortActive() { return comfortOnPhases > 0; }

    /** Explicit same-mode requests are idempotent and must not restart the flash period. */
    public boolean setMode(VehicleSignalMode next) {
        Objects.requireNonNull(next, "mode");
        if (mode == next) {
            if (!comfortActive()) return false;
            // A manual selection keeps the current phase and becomes continuous.
            comfortOnPhases = 0;
            resetAutomaticCancellation();
            return true;
        }
        restore(next);
        return true;
    }

    /** Loaded hazard state starts a fresh ON phase; a runtime phase is never persisted. */
    public void restore(VehicleSignalMode restored) {
        mode = Objects.requireNonNull(restored, "mode");
        resetAutomaticCancellation();
        comfortOnPhases = 0;
        phaseTicks = 0;
        lit = mode != VehicleSignalMode.OFF;
    }

    public boolean request(int request) {
        return request(request, true);
    }

    public boolean request(int request, boolean comfortEnabled) {
        if (!isValidRequest(request)) return false;
        if (isComfortRequest(request)) {
            // A duplicate handshake cannot extend a running finite series or replace manual signals.
            if (!comfortEnabled || mode != VehicleSignalMode.OFF || comfortActive()) return false;
            restore(request == THREE_LEFT ? VehicleSignalMode.LEFT : VehicleSignalMode.RIGHT);
            comfortOnPhases = COMFORT_ON_PHASES;
            return true;
        }
        if (VehicleSignalMode.isValidId(request)) return setMode(VehicleSignalMode.fromId(request));
        VehicleSignalMode target = VehicleSignalMode.fromId(request - LIT_FLAG);
        return setMode(mode == target ? VehicleSignalMode.OFF : target);
    }

    /** Advance exactly one server entity tick, unaffected by commands changing world time. */
    public boolean tick(boolean actualDriver) {
        resetAutomaticCancellation();
        if (mode.directional() && !actualDriver) return setMode(VehicleSignalMode.OFF);
        return advancePhase();
    }

    /** Observe server car motion, then advance the existing authoritative flash clock. */
    public boolean tick(boolean actualDriver, boolean automaticCancel, float steering,
                        float signedForwardSpeed, float yaw) {
        if (mode.directional() && !actualDriver) return setMode(VehicleSignalMode.OFF);
        if (comfortActive()) {
            resetAutomaticCancellation();
            if (!Float.isFinite(steering) || !Float.isFinite(signedForwardSpeed) || !Float.isFinite(yaw))
                return cancelComfort();
            return advancePhase();
        }
        if (automaticCancellation.tick(mode, actualDriver, automaticCancel, steering, signedForwardSpeed, yaw))
            return setMode(VehicleSignalMode.OFF);
        return advancePhase();
    }

    public void resetAutomaticCancellation() {
        automaticCancellation.reset();
    }

    /** Driver, input and world/context resets never leave a finite series running. */
    public boolean cancelComfort() {
        return comfortActive() && setMode(VehicleSignalMode.OFF);
    }

    private boolean advancePhase() {
        if (mode == VehicleSignalMode.OFF) return false;
        if (++phaseTicks < HALF_PERIOD_TICKS) return false;
        phaseTicks = 0;
        if (lit && comfortActive() && --comfortOnPhases == 0) {
            restore(VehicleSignalMode.OFF);
            return true;
        }
        lit = !lit;
        return true;
    }

    public static boolean isValidRequest(int request) {
        return VehicleSignalMode.isValidId(request) || request >= TOGGLE_LEFT && request <= THREE_RIGHT;
    }

    public static boolean isComfortRequest(int request) {
        return request == THREE_LEFT || request == THREE_RIGHT;
    }

    public static boolean isValidPacked(int packed) {
        return packed >= 0 && packed <= 7 && packed != LIT_FLAG;
    }

    public static VehicleSignalMode modeFromPacked(int packed) {
        return isValidPacked(packed) ? VehicleSignalMode.fromId(packed & 3) : VehicleSignalMode.OFF;
    }

    public static boolean litFromPacked(int packed) {
        return isValidPacked(packed) && (packed & LIT_FLAG) != 0;
    }
}
