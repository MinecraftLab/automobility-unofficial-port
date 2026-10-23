package io.github.foundationgames.automobility.screen;

import java.util.Objects;
import java.util.UUID;

/** An elapsed-clock reveal of a server-confirmed selection, independent of ticks or frame rate. */
public final class GarageSelectionTransition {
    public static final long DURATION_NANOS = 180_000_000L;

    public record Frame(float curtainAlpha, boolean active) {
        private static final Frame STILL = new Frame(0, false);
    }

    private boolean initialized, active;
    private UUID selection;
    private long startedNanos;

    /**
     * Call with the confirmed selection each rendered frame. Draw the matte curtain above the
     * single hero PiP, in a later GUI stratum. The first selection and reduced motion are immediate.
     */
    public Frame update(UUID confirmedSelection, long nowNanos, boolean motionEnabled) {
        if (!initialized) {
            initialized = true;
            selection = confirmedSelection;
            return Frame.STILL;
        }
        if (!Objects.equals(selection, confirmedSelection)) {
            selection = confirmedSelection;
            startedNanos = nowNanos;
            active = motionEnabled && confirmedSelection != null;
        }
        if (!motionEnabled) active = false;
        if (!active) return Frame.STILL;

        // Subtraction also handles a normal nanoTime wrap. A regressing external clock stays covered.
        long elapsed = nowNanos - startedNanos;
        if (elapsed >= DURATION_NANOS) {
            active = false;
            return Frame.STILL;
        }
        float progress = Math.max(0, elapsed) / (float) DURATION_NANOS;
        float eased = progress * progress * (3 - 2 * progress);
        return new Frame(1 - eased, true);
    }
}
