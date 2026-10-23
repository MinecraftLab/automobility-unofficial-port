package io.github.foundationgames.automobility.client.vehicle;

/** Stable thinning of cosmetic smoke; no world RNG, vehicle tick or physics mutation. */
public final class VehicleCosmeticPolicy {
    private VehicleCosmeticPolicy() {}
    public static boolean driftSmoke(int percent, long index) {
        return driftSmoke(percent, index, 0);
    }
    public static boolean driftSmoke(int percent, long index, long tick) {
        if (percent == 100) return true;
        if (percent <= 0) return false;
        long sample = index ^ Long.rotateLeft(tick, 21);
        sample = (sample ^ (sample >>> 30)) * 0xbf58476d1ce4e5b9L;
        sample = (sample ^ (sample >>> 27)) * 0x94d049bb133111ebL;
        sample ^= sample >>> 31;
        return (sample & 3) < percent / 25;
    }
}
