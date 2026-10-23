package io.github.foundationgames.automobility.automobile.light;

/** Server-tick transient choreography. Selected lights and AUTO are never changed or saved here. */
public final class Mx5HeadlightDance {
    public static final int DURATION_TICKS = 80;
    public static final int MOTION_TICKS = 8;
    public static final int START_COOLDOWN_TICKS = 10;
    private static final int ACTIVE = 1, LEFT_OPEN = 2, RIGHT_OPEN = 4, LEFT_LIT = 8, RIGHT_LIT = 16;
    private boolean active, observed, requested;
    private long startTick, lastTick, lastRequestTick;

    public boolean toggle(long tick, boolean eligible) {
        update(tick, eligible);
        if (!eligible) return false;
        if (active) {
            active = false;
            lastRequestTick = tick;
            requested = true;
            return true;
        }
        if (requested && tick - lastRequestTick >= 0 && tick - lastRequestTick < START_COOLDOWN_TICKS) return false;
        active = true;
        startTick = lastRequestTick = tick;
        requested = true;
        return true;
    }

    public int update(long tick, boolean eligible) {
        if (observed && (tick < lastTick || tick - lastTick < 0)) reset();
        observed = true;
        lastTick = tick;
        if (!eligible || active && (tick - startTick < 0 || tick - startTick >= DURATION_TICKS)) active = false;
        if (!active) return 0;
        int age = (int)(tick - startTick);
        if (age >= 68) return 0; // Twelve ticks restore the current normal pose and mode.
        if (age < 12) return ACTIVE | LEFT_OPEN | RIGHT_OPEN | LEFT_LIT | RIGHT_LIT;
        int phase = (age - 12) / 14;
        return (phase % 2 == 0)
                ? ACTIVE | LEFT_OPEN | LEFT_LIT
                : ACTIVE | RIGHT_OPEN | RIGHT_LIT;
    }

    public boolean active() { return active; }

    public void reset() {
        active = observed = requested = false;
        startTick = lastTick = lastRequestTick = 0;
    }

    public static boolean activeFromPacked(int packed) {
        return (packed & ~31) == 0 && (packed & ACTIVE) != 0
                && ((packed & LEFT_LIT) == 0 || (packed & LEFT_OPEN) != 0)
                && ((packed & RIGHT_LIT) == 0 || (packed & RIGHT_OPEN) != 0);
    }

    public static boolean requestedOpen(int packed, boolean left, VehicleLightMode normal) {
        return activeFromPacked(packed) ? (packed & (left ? LEFT_OPEN : RIGHT_OPEN)) != 0
                : Mx5PopupAnimation.requestedOpen(normal);
    }

    public static VehicleLightMode lightMode(int packed, boolean left, VehicleLightMode normal) {
        return activeFromPacked(packed)
                ? ((packed & (left ? LEFT_LIT : RIGHT_LIT)) != 0 ? VehicleLightMode.LOW : VehicleLightMode.OFF)
                : normal;
    }
}
