package io.github.foundationgames.automobility.automobile.drivetrain;

import java.util.Objects;

/** Dedicated-server limits apply before a requested vehicle preference is persisted or used. */
public record DrivetrainSettingsPolicy(boolean manualAllowed, float minimumAssistance,
                                       double minimumClutchReleaseSeconds, double maximumClutchReleaseSeconds,
                                       double maximumAnalogSlewSeconds, boolean requireStall, boolean requireDownshiftProtection) {
    public DrivetrainSettingsPolicy {
        Units.between(minimumAssistance, 0.0, 1.0, "minimum assistance");
        Units.between(minimumClutchReleaseSeconds, 0.01, 10.0, "minimum release");
        Units.between(maximumClutchReleaseSeconds, minimumClutchReleaseSeconds, 10.0, "maximum release");
        Units.between(maximumAnalogSlewSeconds, 0.0, 10.0, "maximum analog slew");
    }
    public static DrivetrainSettingsPolicy defaults() { return new DrivetrainSettingsPolicy(true, 0, 0.01, 10, 10, false, false); }
    public static DrivetrainSettingsEnvelope.Settings defaultSettings() {
        return new DrivetrainSettingsEnvelope.Settings(DrivetrainSettingsEnvelope.TransmissionMode.AUTOMATIC, 0.8, 0, 1, false, true);
    }
    public DrivetrainSettingsEnvelope.Settings apply(DrivetrainSettingsEnvelope.Settings requested) {
        Objects.requireNonNull(requested);
        return new DrivetrainSettingsEnvelope.Settings(manualAllowed ? requested.transmissionMode() : DrivetrainSettingsEnvelope.TransmissionMode.AUTOMATIC,
                Math.clamp(requested.clutchReleaseSeconds(), minimumClutchReleaseSeconds, maximumClutchReleaseSeconds),
                Math.min(requested.analogSlewSeconds(), maximumAnalogSlewSeconds), Math.max(requested.assistanceStrength(), minimumAssistance),
                requireStall || requested.stallEnabled(), requireDownshiftProtection || requested.downshiftProtection());
    }
}
