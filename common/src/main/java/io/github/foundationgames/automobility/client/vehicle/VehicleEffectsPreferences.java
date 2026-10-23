package io.github.foundationgames.automobility.client.vehicle;

/** Per-client presentation settings, never vehicle NBT, profiles or gameplay packets. */
public record VehicleEffectsPreferences(VehicleLightQuality quality, int signalVolumePercent,
                                        boolean customEnabled, VehicleCustomGraphicsSettings custom) {
    public VehicleEffectsPreferences(VehicleLightQuality quality, int signalVolumePercent) {
        this(quality, signalVolumePercent, false, VehicleCustomGraphicsSettings.forPreset(
                quality == null ? VehicleLightQuality.BALANCED : quality));
    }
    public static VehicleEffectsPreferences defaults() {
        return new VehicleEffectsPreferences(VehicleLightQuality.BALANCED, 40);
    }

    public VehicleEffectsPreferences sanitized() {
        return new VehicleEffectsPreferences(quality == null ? VehicleLightQuality.BALANCED : quality,
                Math.max(0, Math.min(100, signalVolumePercent)), customEnabled,
                custom == null ? VehicleCustomGraphicsSettings.forPreset(
                        quality == null ? VehicleLightQuality.BALANCED : quality) : custom);
    }

    public VehicleEffectsPreferences withQuality(VehicleLightQuality value) {
        return new VehicleEffectsPreferences(value, signalVolumePercent, false, custom).sanitized();
    }

    public VehicleEffectsPreferences withSignalVolumePercent(int value) {
        return new VehicleEffectsPreferences(quality, value, customEnabled, custom).sanitized();
    }

    public VehicleEffectsPreferences withCustom(VehicleCustomGraphicsSettings value) {
        return new VehicleEffectsPreferences(quality, signalVolumePercent, true,
                java.util.Objects.requireNonNull(value)).sanitized();
    }

    public VehicleCustomGraphicsSettings graphics() {
        return customEnabled ? custom : VehicleCustomGraphicsSettings.forPreset(quality);
    }
}

