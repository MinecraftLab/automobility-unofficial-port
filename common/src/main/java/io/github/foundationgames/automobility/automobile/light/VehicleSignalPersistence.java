package io.github.foundationgames.automobility.automobile.light;

/** Only parked hazards survive storage; directional signals are current-driver operation. */
public final class VehicleSignalPersistence {
    public static final int SCHEMA_VERSION = 1;
    public static final String VERSION_KEY = "vehicleSignalSettingsVersion";
    public static final String MODE_KEY = "vehicleSignalMode";
    private VehicleSignalPersistence() {}
    public static int savedMode(VehicleSignalMode selected) {
        return selected == VehicleSignalMode.HAZARD ? VehicleSignalMode.HAZARD.id() : VehicleSignalMode.OFF.id();
    }
    public static VehicleSignalMode restore(int version, int savedMode) {
        return version == SCHEMA_VERSION && savedMode == VehicleSignalMode.HAZARD.id()
                ? VehicleSignalMode.HAZARD : VehicleSignalMode.OFF;
    }
}
