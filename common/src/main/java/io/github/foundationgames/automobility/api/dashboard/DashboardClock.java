package io.github.foundationgames.automobility.api.dashboard;

/** A captured media position, never a second advancing playback clock. */
public record DashboardClock(Kind kind, long positionMillis, long durationMillis, boolean paused) {
    public enum Kind { FINITE, UNKNOWN_DURATION, LIVE }

    public DashboardClock {
        if (kind == null || positionMillis < 0 || durationMillis < 0
                || kind == Kind.FINITE && (durationMillis <= 0 || positionMillis > durationMillis)
                || kind != Kind.FINITE && durationMillis != 0
                || kind == Kind.LIVE && positionMillis != 0) throw new IllegalArgumentException("invalid captured media clock");
    }

    public static DashboardClock finite(long position, long duration, boolean paused) {
        if (position < 0 || duration <= 0) throw new IllegalArgumentException("invalid finite clock");
        return new DashboardClock(Kind.FINITE, Math.min(position, duration), duration, paused);
    }

    public static DashboardClock unknown(long position, boolean paused) {
        return new DashboardClock(Kind.UNKNOWN_DURATION, position, 0, paused);
    }

    public static DashboardClock live(boolean paused) { return new DashboardClock(Kind.LIVE, 0, 0, paused); }
    public double progress() { return kind == Kind.FINITE ? positionMillis / (double) durationMillis : 0.0; }
}
