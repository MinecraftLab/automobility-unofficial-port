package io.github.foundationgames.automobility.automobile.light;

/**
 * Transient server-side driving-light policy. Pedals are accepted raw input, while reverse
 * authorization belongs to the existing reverse-release guard, never to the sign of motion.
 */
public final class VehicleDrivingLights {
    public static final int BRAKE_FLAG = 1;
    public static final int REVERSE_FLAG = 2;
    // Server vehicle-move packets can leave zero observations between real movement samples.
    // Keep direction through that dead band; invalid context always clears the remembered sign.
    public static final double DIRECTION_THRESHOLD = 0.005;
    private boolean reversing;
    // Lamp-only reverse selection. A later physics-guard sample cannot reinterpret an
    // already authorized, continuously held reverse pedal as a forward-travel brake.
    private boolean reverseSelected;
    private int packed;

    public int packed() { return packed; }

    public void reset() {
        reversing = false;
        reverseSelected = false;
        packed = 0;
    }

    /** No prediction, NBT or timers: the entity supplies its validated, fresh driver context. */
    public int update(boolean actualDriver, boolean engineRunning, boolean forwardPedal,
                      boolean backwardPedal, boolean waitingForReverseRelease, double signedSpeed) {
        if (!actualDriver || !Double.isFinite(signedSpeed)) {
            reset();
            return packed;
        }
        if (signedSpeed <= -DIRECTION_THRESHOLD) reversing = true;
        else if (signedSpeed >= DIRECTION_THRESHOLD) reversing = false;

        if (!engineRunning || forwardPedal || !backwardPedal) reverseSelected = false;
        if (engineRunning && backwardPedal && !forwardPedal && !waitingForReverseRelease)
            reverseSelected = true;

        boolean reverse = engineRunning && backwardPedal && !forwardPedal && reverseSelected;
        boolean brake = backwardPedal && !reverse
                || forwardPedal && reversing;
        // Both pedals and braking against reverse travel must never show a reverse lamp.
        packed = brake ? BRAKE_FLAG : reverse ? REVERSE_FLAG : 0;
        return packed;
    }

    public static boolean brakeLit(int flags) {
        return flags == BRAKE_FLAG;
    }

    public static boolean reverseLit(int flags) {
        return flags == REVERSE_FLAG;
    }

    /** Observed displacement projected onto the actual chassis heading; no Vec3 allocation. */
    public static double signedSpeed(double deltaX, double deltaZ, float yawDegrees) {
        if (!Double.isFinite(deltaX) || !Double.isFinite(deltaZ) || !Float.isFinite(yawDegrees))
            return Double.NaN;
        double heading = Math.toRadians(-yawDegrees);
        return deltaX * Math.sin(heading) + deltaZ * Math.cos(heading);
    }
}
