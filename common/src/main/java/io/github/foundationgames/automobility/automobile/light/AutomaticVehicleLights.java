package io.github.foundationgames.automobility.automobile.light;

/** Server-only daylight sampling policy with both a dead band and transition delay. */
public final class AutomaticVehicleLights {
    public static final int SAMPLE_INTERVAL_TICKS = 5;
    public static final int TURN_ON_LIGHT_LEVEL = 7;
    public static final int TURN_OFF_LIGHT_LEVEL = 11;
    private static final int DARK_SAMPLES_TO_TURN_ON = 2;
    private static final int BRIGHT_SAMPLES_TO_TURN_OFF = 4;

    private boolean initialized;
    private boolean lowBeamActive;
    private int transitionSamples;
    private long lastSampleTick;

    public void reset() {
        this.initialized = false;
        this.lowBeamActive = false;
        this.transitionSamples = 0;
    }

    public boolean isSampleDue(long currentTick) {
        return !this.initialized || currentTick < this.lastSampleTick
                || currentTick - this.lastSampleTick >= SAMPLE_INTERVAL_TICKS;
    }

    public VehicleLightMode sample(long currentTick, int ambientDaylight) {
        if (!isSampleDue(currentTick)) {
            return effectiveMode();
        }

        boolean clockReset = this.initialized && currentTick < this.lastSampleTick;
        this.lastSampleTick = currentTick;
        if (!this.initialized || clockReset) {
            this.initialized = true;
            this.lowBeamActive = ambientDaylight <= TURN_ON_LIGHT_LEVEL;
            this.transitionSamples = 0;
            return effectiveMode();
        }

        boolean wantsTransition = this.lowBeamActive
                ? ambientDaylight >= TURN_OFF_LIGHT_LEVEL
                : ambientDaylight <= TURN_ON_LIGHT_LEVEL;
        this.transitionSamples = wantsTransition ? this.transitionSamples + 1 : 0;
        int samplesRequired = this.lowBeamActive ? BRIGHT_SAMPLES_TO_TURN_OFF : DARK_SAMPLES_TO_TURN_ON;
        if (this.transitionSamples >= samplesRequired) {
            this.lowBeamActive = !this.lowBeamActive;
            this.transitionSamples = 0;
        }
        return effectiveMode();
    }

    public VehicleLightMode effectiveMode() {
        return this.lowBeamActive ? VehicleLightMode.LOW : VehicleLightMode.OFF;
    }

    /** Artificial block light cannot conceal a dark tunnel from the daylight sensor. */
    public static int ambientDaylight(int skyLight, int skyDarkening) {
        return Math.clamp(skyLight - skyDarkening, 0, 15);
    }
}
