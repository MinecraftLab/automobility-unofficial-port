package io.github.foundationgames.automobility.automobile.engine;

public record VehicleEngineSettings(boolean automaticStart, int idleShutdownSeconds, boolean lightsWithEngine) {
    public static final int DEFAULT_IDLE_SECONDS = 600;
    public static final int MAX_IDLE_SECONDS = 86_400;
    public static VehicleEngineSettings defaults() { return new VehicleEngineSettings(true, DEFAULT_IDLE_SECONDS, true); }
    public static boolean validDelay(int seconds) { return seconds >= -1 && seconds <= MAX_IDLE_SECONDS; }
    public VehicleEngineSettings sanitized() {
        return validDelay(idleShutdownSeconds) ? this : new VehicleEngineSettings(automaticStart, DEFAULT_IDLE_SECONDS, lightsWithEngine);
    }
    public VehicleEngineSettings restricted(int maximumSeconds, boolean allowNever) {
        var safe = sanitized();
        int delay = safe.idleShutdownSeconds;
        if (delay < 0 && !allowNever) delay = Math.min(DEFAULT_IDLE_SECONDS, maximumSeconds);
        if (delay >= 0) delay = Math.min(delay, maximumSeconds);
        return new VehicleEngineSettings(safe.automaticStart, delay, safe.lightsWithEngine);
    }

    public static int nextDelay(int current, int maximum, boolean allowNever) {
        if (current < 0) return 0;
        if (current >= maximum) return allowNever ? -1 : 0;
        for (int seconds : new int[]{60, 300, 600, 1800, 3600, MAX_IDLE_SECONDS})
            if (seconds > current) return Math.min(seconds, maximum);
        return allowNever ? -1 : 0;
    }
}
