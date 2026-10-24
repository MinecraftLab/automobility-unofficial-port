package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.automobile.light.VehicleDrivingLights;

/** Small, deterministic display policy shared by the editor, renderer and compatibility hooks. */
public final class VehicleEnergyPresentation {
    public static final int BASE_HEIGHT = 32;
    public static final int ENERGY_HEIGHT = 10;
    public static final int PANEL_WIDTH = 176;
    private VehicleEnergyPresentation() {}

    public static int panelHeight(boolean energy) { return BASE_HEIGHT + (energy ? ENERGY_HEIGHT : 0); }

    /** Same fit for actual rendering and drag bounds, including the attached energy row. */
    public static float panelScale(int viewportWidth, int viewportHeight, int logicalWidth, int logicalHeight, int percent) {
        float scale = Math.min(percent / 100f, Math.max(1, viewportWidth - 16) / (float) logicalWidth);
        return Math.min(scale, Math.max(1, viewportHeight - 16) / (float) logicalHeight);
    }

    /** GUI zoom must not turn a small instrument cluster into a large screen overlay. */
    public static float guiScaleCompensation(double guiScale) {
        return Double.isFinite(guiScale) && guiScale > 0 ? (float)Math.clamp(2.0 / guiScale, .5, 1.0) : 1;
    }
    public static float panelScale(int w, int h, int logicalWidth, int logicalHeight, int percent, double guiScale) {
        return panelScale(w, h, logicalWidth, logicalHeight, percent) * guiScaleCompensation(guiScale);
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
