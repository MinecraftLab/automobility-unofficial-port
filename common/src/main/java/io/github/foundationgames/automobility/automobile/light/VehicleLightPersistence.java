package io.github.foundationgames.automobility.automobile.light;

/** Selected settings are saved; the automatic sensor state is always reconstructed. */
public final class VehicleLightPersistence {
    public static final int SCHEMA_VERSION = 1;
    public static final String VERSION_KEY = "vehicleLightSettingsVersion";
    public static final String MODE_KEY = "vehicleLightMode";

    private VehicleLightPersistence() {
    }

    public static VehicleLightMode restore(int schemaVersion, int savedModeId) {
        return schemaVersion == SCHEMA_VERSION ? VehicleLightMode.fromId(savedModeId) : VehicleLightMode.OFF;
    }
}
