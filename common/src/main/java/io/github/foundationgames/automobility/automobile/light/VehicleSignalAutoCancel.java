package io.github.foundationgames.automobility.automobile.light;

/**
 * Transient server-side turn observation. Steering alone cannot arm cancellation:
 * the caller supplies observed forward displacement and a corresponding change of car yaw.
 * All inputs describe the server automobile, never the player's camera.
 */
public final class VehicleSignalAutoCancel {
    public static final float MIN_FORWARD_SPEED = 0.035f;
    public static final float TURN_STEERING = 0.25f;
    public static final float CENTER_STEERING = 0.1f;
    public static final double TURN_DEGREES = 15.0;
    public static final double MAX_YAW_STEP = 30.0;
    public static final int CENTER_TICKS = 5;

    private VehicleSignalMode trackedMode = VehicleSignalMode.OFF;
    private boolean haveYaw;
    private float previousYaw;
    private double turnDegrees;
    private boolean armed;
    private int centeredTicks;

    /** Returns true only at a completed directional turn, never for OFF or hazards. */
    public boolean tick(VehicleSignalMode mode, boolean actualDriver, boolean automaticCancel,
                        float steering, float signedForwardSpeed, float yaw) {
        if (mode == null || !mode.directional() || !actualDriver || !automaticCancel
                || !Float.isFinite(steering) || !Float.isFinite(signedForwardSpeed)
                || !Float.isFinite(yaw) || signedForwardSpeed <= MIN_FORWARD_SPEED) {
            reset();
            return false;
        }
        if (trackedMode != mode) {
            reset();
            trackedMode = mode;
        }
        if (!haveYaw) {
            previousYaw = yaw;
            haveYaw = true;
            return false;
        }

        double yawStep = ((double) yaw - previousYaw) % 360.0;
        if (yawStep > 180.0) yawStep -= 360.0;
        else if (yawStep < -180.0) yawStep += 360.0;
        previousYaw = yaw;
        if (Math.abs(yawStep) > MAX_YAW_STEP) {
            reset();
            return false;
        }

        int direction = mode == VehicleSignalMode.LEFT ? -1 : 1;
        float directedSteering = direction * steering;
        if (directedSteering <= -TURN_STEERING) {
            reset();
            return false;
        }
        if (Math.abs(steering) <= CENTER_STEERING) {
            if (++centeredTicks >= CENTER_TICKS) {
                if (armed) {
                    reset();
                    return true;
                }
                // Separate small steering corrections must not accumulate into a turn.
                turnDegrees = 0;
                centeredTicks = CENTER_TICKS;
            }
            return false;
        }

        centeredTicks = 0;
        if (!armed && directedSteering >= TURN_STEERING) {
            turnDegrees = Math.max(0, turnDegrees + direction * yawStep);
            armed = turnDegrees >= TURN_DEGREES;
        }
        return false;
    }

    /** Preference, mode, driver and world/context transitions discard all turn history. */
    public void reset() {
        trackedMode = VehicleSignalMode.OFF;
        haveYaw = false;
        previousYaw = 0;
        turnDegrees = 0;
        armed = false;
        centeredTicks = 0;
    }
}
