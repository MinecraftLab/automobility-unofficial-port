package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.automobile.light.VehicleDrivingLights;

/** Small, deterministic display policy shared by the editor, renderer and compatibility hooks. */
public final class VehicleEnergyPresentation {
    public static final int BASE_HEIGHT = 34;
    public static final int ENERGY_HEIGHT = 12;
    private VehicleEnergyPresentation() {}

    public static int panelHeight(boolean energy) { return BASE_HEIGHT + (energy ? ENERGY_HEIGHT : 0); }

    /** Same fit for actual rendering and drag bounds, including the attached energy row. */
    public static float panelScale(int viewportWidth, int viewportHeight, int logicalWidth, int logicalHeight, int percent) {
        return panelScale(viewportWidth, viewportHeight, logicalWidth, logicalHeight, percent, 1);
    }

    /** Keep GUI1-3 unchanged; larger vanilla GUI scales do not enlarge our overlay past GUI3.
     * The player's own percentage is still a multiplier, not a setting we rewrite.
     */
    public static float guiScaleCompensation(double guiScale) {
        return Double.isFinite(guiScale) && guiScale > 3 ? (float) (3 / guiScale) : 1;
    }

    public static float panelScale(int viewportWidth, int viewportHeight, int logicalWidth, int logicalHeight,
            int percent, double guiScale) {
        float requested = percent / 100f * guiScaleCompensation(guiScale);
        float scale = Math.min(requested, Math.max(1, viewportWidth - 16) / (float) logicalWidth);
        return Math.min(scale, Math.max(1, viewportHeight - 16) / (float) logicalHeight);
    }

    public static boolean reverseMotion(double deltaX, double deltaZ, float yaw) {
        double signed = VehicleDrivingLights.signedSpeed(deltaX, deltaZ, yaw);
        return Double.isFinite(signed) && signed <= -VehicleDrivingLights.DIRECTION_THRESHOLD;
    }

    public static boolean ownsGauge(boolean unified, boolean energyEnabled, boolean visible,
            boolean exactContext, VehicleEnergyReading reading, VehicleEnergyReading.Kind kind) {
        return unified && energyEnabled && visible && exactContext && reading != null
                && reading.kind() == kind && reading.known();
    }

    public static boolean ordinaryBatteryNotice(String key, boolean noSiblings, int argumentCount) {
        return noSiblings && argumentCount == 2 && "message.powerworks.vehicle_battery".equals(key);
    }
}
