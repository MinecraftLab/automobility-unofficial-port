package io.github.foundationgames.automobility.automobile.light;

/** Finite server-owned lock feedback, separate from the user's selected indicator state. */
public final class VehicleLockSignalController {
    public static final int HALF_PERIOD_TICKS = 5;
    private int remainingOnPhases;
    private int phaseTicks;
    private boolean lit;

    public boolean active() { return remainingOnPhases > 0; }
    public int packed() {
        return active() ? VehicleSignalMode.HAZARD.id() | (lit ? VehicleSignalController.LIT_FLAG : 0) : 0;
    }

    /** Caller supplies an actual authorized lock transition, not a repeated unchanged request. */
    public boolean confirm(boolean locked, boolean enabled, VehicleSignalMode selected) {
        int before = packed();
        clear();
        if (enabled && selected == VehicleSignalMode.OFF) {
            remainingOnPhases = locked ? 1 : 2;
            lit = true;
        }
        return before != packed();
    }

    public boolean tick(boolean enabled, VehicleSignalMode selected) {
        if (!enabled || selected != VehicleSignalMode.OFF) return clear();
        if (!active()) return false;
        if (++phaseTicks < HALF_PERIOD_TICKS) return false;
        phaseTicks = 0;
        if (lit && --remainingOnPhases == 0) return clear();
        lit = !lit;
        return true;
    }

    public boolean clear() {
        boolean changed = active() || lit;
        remainingOnPhases = 0;
        phaseTicks = 0;
        lit = false;
        return changed;
    }

    /** Even a manual dark phase suppresses feedback; no pulse can steal its selected side. */
    public static int visiblePacked(int selected, int confirmation) {
        if (VehicleSignalController.modeFromPacked(selected) != VehicleSignalMode.OFF) return selected;
        return VehicleSignalController.isValidPacked(confirmation) ? confirmation : 0;
    }
}
