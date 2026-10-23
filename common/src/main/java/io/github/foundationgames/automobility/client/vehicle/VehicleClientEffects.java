package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.Automobility;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;

/** Independent client settings used by rendering and relay audio; no server operations. */
public final class VehicleClientEffects {
    private static VehicleEffectsPreferences preferences = VehicleEffectsPreferences.defaults();
    private static VehicleEffectsPreferenceStore store;
    private static boolean readable;

    private VehicleClientEffects() {}

    public static void initialize() {
        preferences = VehicleEffectsPreferences.defaults();
        store = new VehicleEffectsPreferenceStore(FabricLoader.getInstance().getConfigDir()
                .resolve(VehicleEffectsPreferenceStore.FILE_NAME));
        readable = false;
        try {
            preferences = store.load();
            readable = true;
        } catch (IOException error) {
            Automobility.LOG.warn("Could not read vehicle effects preferences; using defaults and preserving the file", error);
        }
        VehicleSignalAudio.reset();
    }

    public static VehicleEffectsPreferences preferences() { return preferences; }

    public static boolean writable() { return store != null && readable && store.readable(); }

    public static boolean savePreferences(VehicleEffectsPreferences value) {
        if (!writable() || value == null) return false;
        var safe = value.sanitized();
        try {
            store.save(safe);
            preferences = safe;
            return true;
        } catch (IOException | RuntimeException error) {
            readable = store.readable();
            Automobility.LOG.warn("Could not save vehicle effects preferences; keeping the current settings", error);
            return false;
        }
    }

    public static void tick(Minecraft client) {
        VehicleSignalAudio.tick(client, preferences.signalVolumePercent());
    }
}

