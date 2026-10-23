package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.Optional;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.Tag;

/** This carrier owns only the namespaced settings tag, never fuel/battery/runtime state. */
public interface CoreDrivetrainSettingsAccess {
    String SETTINGS_KEY = "automobility:drivetrain_settings";
    Tag drivetrain$rawSettings();
    void drivetrain$replaceSettings(DrivetrainSettingsEnvelope settings, CoreDrivetrainBridge.SettingsPermit permit);

    default Optional<DrivetrainSettingsEnvelope> drivetrain$settingsEnvelope() {
        Tag raw = drivetrain$rawSettings();
        if (raw == null) return Optional.of(DrivetrainSettingsEnvelope.absent());
        if (!(raw instanceof ByteArrayTag bytes)) return Optional.empty();
        try { return Optional.of(DrivetrainSettingsEnvelope.decodeQuiet(bytes.getAsByteArray())); }
        catch (IllegalArgumentException oversized) { return Optional.empty(); }
    }
}
