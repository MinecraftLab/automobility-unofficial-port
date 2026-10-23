package io.github.foundationgames.automobility.automobile.engine;

/** Server-clock ignition and vacant-car timer; never loads chunks or predicts client state. */
public final class VehicleIgnition {
    public static final int OFF = 0, STARTING = 1, RUNNING = 2;
    public static final int START_TICKS = 12;
    private int state = OFF;
    private long startAt = -1, vacantAt = -1, lastStart = Long.MIN_VALUE;
    private boolean waitForThrottleRelease;

    public int state() { return state; }
    public long vacantAt() { return vacantAt; }
    public long startedAt() { return startAt; }

    public boolean request(boolean start, long tick, boolean hasEngine) {
        if (!start) {
            if (state == OFF) return false;
            state = OFF;
            startAt = vacantAt = -1;
            waitForThrottleRelease = true;
            return true;
        }
        if (!hasEngine || state != OFF || (lastStart != Long.MIN_VALUE && tick - lastStart < 20)) return false;
        state = STARTING;
        startAt = lastStart = tick;
        vacantAt = -1;
        waitForThrottleRelease = false;
        return true;
    }

    /** Returns true only when this tick begins a real new ignition transition. */
    public boolean tick(long tick, boolean occupied, boolean driver, boolean throttle, boolean hasEngine,
                        VehicleEngineSettings settings) {
        return tick(tick, occupied, driver, throttle, throttle, hasEngine, settings);
    }

    /** Drive eligibility and physical pedal release are separate, including brake/reverse. */
    public boolean tick(long tick, boolean occupied, boolean driver, boolean driveRequest, boolean pedalHeld,
                        boolean hasEngine, VehicleEngineSettings settings) {
        if (!pedalHeld) waitForThrottleRelease = false;
        if (!hasEngine) {
            request(false, tick, false);
            return false;
        }
        boolean started = state == OFF && driver && driveRequest && settings.automaticStart()
                && !waitForThrottleRelease && request(true, tick, true);
        if (state == STARTING && tick - startAt >= START_TICKS) state = RUNNING;
        if (state != OFF) {
            if (occupied) vacantAt = -1;
            else {
                if (vacantAt < 0 || tick < vacantAt) vacantAt = tick;
                if (settings.idleShutdownSeconds() >= 0 && tick - vacantAt >= settings.idleShutdownSeconds() * 20L)
                    request(false, tick, hasEngine);
            }
        }
        return started;
    }

    /** A parked engine keeps its deadline through chunk unload; process reloads start safely OFF. */
    public void restoreUnloaded(int savedState, long savedStartAt, long savedVacantAt, boolean sameServerSession) {
        state = sameServerSession && savedState >= OFF && savedState <= RUNNING ? savedState : OFF;
        startAt = state == STARTING ? savedStartAt : -1;
        vacantAt = state != OFF ? savedVacantAt : -1;
        lastStart = Long.MIN_VALUE;
        waitForThrottleRelease = false;
    }
}
