package io.github.foundationgames.automobility.client.vehicle;

/** Category-local saves cannot overwrite another category's newer published values. */
public final class VehicleSettingsDrafts {
    private VehicleSettingsDrafts() {}
    public static VehicleEffectsPreferences graphics(VehicleEffectsPreferences current, VehicleEffectsPreferences draft) {
        return new VehicleEffectsPreferences(draft.quality(),current.signalVolumePercent(),draft.customEnabled(),draft.custom()).sanitized();
    }
    public static VehicleEffectsPreferences sound(VehicleEffectsPreferences current, VehicleEffectsPreferences draft) {
        return current.withSignalVolumePercent(draft.signalVolumePercent());
    }
    public static int footerButtonWidth(int screenWidth) {
        return Math.max(1,Math.min(150,(Math.max(1,screenWidth)-24)/2));
    }
}
