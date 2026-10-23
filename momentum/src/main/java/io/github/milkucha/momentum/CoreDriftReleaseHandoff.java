package io.github.milkucha.momentum;

/**
 * Per-car ownership of a still-active Core drift across an enabled-mode change.
 * This tracks only Core's own key history, never Momentum's configurable key
 * or its gated false value while disabled. It cannot create charge or a boost.
 */
public final class CoreDriftReleaseHandoff {
    private Object driver;
    private boolean coreWasSelected;
    private boolean coreOwnsRelease;

    /** Actual object identity prevents reconnects from borrowing a UUID's history. */
    public boolean bindDriver(Object actualDriver) {
        boolean changed = actualDriver != driver;
        if (changed || actualDriver == null) {
            driver = actualDriver;
            coreWasSelected = false;
            coreOwnsRelease = false;
        }
        return changed;
    }

    /** Captured actual driver only; null and equal UUID/value objects are not drivers. */
    public boolean isBoundDriver(Object actualDriver) {
        return actualDriver != null && actualDriver == driver;
    }

    /** Called once before each real driftingTick, before its enabled-mode guard. */
    public boolean beforeDriftTick(boolean momentumEnabled, boolean drifting,
                                   boolean corePreviousHeld, boolean coreHeld) {
        if (driver == null) {
            coreWasSelected = false;
            coreOwnsRelease = false;
            return false;
        }
        if (!momentumEnabled) {
            coreWasSelected = true;
            coreOwnsRelease = false;
            return false;
        }
        if (coreWasSelected && drifting && corePreviousHeld) {
            coreOwnsRelease = true;
        }
        coreWasSelected = false;
        if (!drifting) {
            coreOwnsRelease = false;
        }
        return coreOwnsRelease && corePreviousHeld && !coreHeld;
    }

    public boolean coreOwnsRelease() {
        return coreOwnsRelease;
    }

    /** Ordinary release or low-speed cancellation ends retained source ownership. */
    public void afterDriftTick(boolean stillDrifting) {
        if (!stillDrifting) {
            coreOwnsRelease = false;
        }
    }
}
