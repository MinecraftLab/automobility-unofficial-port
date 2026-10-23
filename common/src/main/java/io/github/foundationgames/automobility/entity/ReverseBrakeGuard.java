package io.github.foundationgames.automobility.entity;

/**
 * Raw pedal state, independent of temporary input masks used by external engines. A backward
 * pedal used while travelling forward remains a brake until all backward pedal sources release.
 * Input expiry invalidates propulsion without inventing such a release.
 */
final class ReverseBrakeGuard {
    private boolean forward;
    private boolean backward;
    private boolean additionalBrake;
    private boolean additionalBrakeEnabled;
    private boolean waiting;
    private boolean valid;

    void acceptBaseInput(boolean forward, boolean backward, boolean valid, double signedSpeed) {
        if (!valid) {
            // An extra-channel packet can precede the first accepted base packet. Invalid
            // samples must neither invent a held pedal nor erase an already accepted one.
            sample(false, signedSpeed);
            return;
        }
        this.forward = forward;
        this.backward = backward;
        sample(valid, signedSpeed);
    }

    void acceptAdditionalBrake(boolean held, boolean valid, double signedSpeed) {
        acceptAdditionalBrake(held, true, valid, signedSpeed);
    }

    void acceptAdditionalBrake(boolean held, boolean enabled, boolean valid, double signedSpeed) {
        if (!valid) {
            sample(false, signedSpeed);
            return;
        }
        this.additionalBrake = held;
        this.additionalBrakeEnabled = enabled;
        sample(valid, signedSpeed);
    }

    void sample(boolean valid, double signedSpeed) {
        this.valid = valid;
        if (!valid) {
            interrupt();
            return;
        }
        if (!backward && !additionalBrake) {
            waiting = false;
        } else if (forward || !Double.isFinite(signedSpeed) || signedSpeed > 0) {
            waiting = true;
        }
    }

    void interrupt() {
        // Keep a real held pedal latched across expiry, driver changes and unloaded tick gaps.
        // A newly mounted, stationary driver with no inherited held input can still reverse.
        waiting |= backward || additionalBrake;
        valid = false;
    }

    void restoreUntrustedInput(boolean forward, boolean backward) {
        this.forward = forward;
        this.backward = backward;
        interrupt();
    }

    /** A real drive request; a latched forward-travel brake cannot request reverse. */
    boolean driveRequested() {
        return valid && (forward || ((backward || (additionalBrake && additionalBrakeEnabled)) && !waiting));
    }

    /** Invalid samples and temporary masks never manufacture a physical pedal release. */
    boolean drivePedalHeld() {
        return forward || backward || additionalBrake;
    }

    boolean brakeHeld() {
        return valid && (backward || (additionalBrake && additionalBrakeEnabled));
    }

    boolean waitingForRelease() {
        return !valid || waiting;
    }

    float speedFloor() {
        return waitingForRelease() ? 0 : -0.25f;
    }
}
