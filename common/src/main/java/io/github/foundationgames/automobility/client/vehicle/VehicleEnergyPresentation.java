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
        float scale = Math.min(percent / 100f, Math.max(1, viewportWidth - 16) / (float) logicalWidth);
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
