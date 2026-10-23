package io.github.foundationgames.automobility.automobile.light;

/** Per-car preference. Policy restriction never overwrites this selected value. */
public record VehicleUnattendedLightSettings(int seconds) {
    public static final int DEFAULT_SECONDS = 300, MAX_SECONDS = 86400;
    public static VehicleUnattendedLightSettings defaults() { return new VehicleUnattendedLightSettings(DEFAULT_SECONDS); }
    public static boolean valid(int seconds) { return seconds >= -1 && seconds <= MAX_SECONDS; }
    public VehicleUnattendedLightSettings sanitized() { return valid(seconds) ? this : defaults(); }
    public VehicleUnattendedLightSettings restricted(int maximum, boolean allowUnlimited) {
        if (maximum < 0 || maximum > MAX_SECONDS) throw new IllegalArgumentException("Invalid unattended light policy limit");
        int delay = sanitized().seconds();
        if (delay < 0 && !allowUnlimited) delay = Math.min(DEFAULT_SECONDS, maximum);
        return new VehicleUnattendedLightSettings(delay >= 0 ? Math.min(delay, maximum) : -1);
    }
    public static int nextDelay(int selected, int maximum, boolean allowUnlimited) {
        for (int value : new int[]{0,60,300,600,1800,3600,86400})
            if (value > selected && selected < maximum) return Math.min(value, maximum);
        return allowUnlimited && selected != -1 ? -1 : 0;
    }
}
