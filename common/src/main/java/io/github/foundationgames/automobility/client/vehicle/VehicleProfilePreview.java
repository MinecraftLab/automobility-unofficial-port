package io.github.foundationgames.automobility.client.vehicle;

import java.util.List;
import java.util.Objects;

/** A captured, immutable selection; opening or reading it never confirms an application. */
public final class VehicleProfilePreview {
    private final VehicleConfigurationProfile profile;
    private boolean submitted;

    public VehicleProfilePreview(VehicleConfigurationProfile profile) {
        this.profile = Objects.requireNonNull(profile);
    }

    public VehicleConfigurationProfile profile() {
        return profile;
    }

    /** Compare selected operational preferences; labels and transient vehicle state are not inputs. */
    public Differences compare(VehicleConfigurationProfile current) {
        Objects.requireNonNull(current);
        var targetEngine = profile.engine();
        var currentEngine = current.engine();
        return new Differences(profile.lights() != current.lights(),
                targetEngine.automaticStart() != currentEngine.automaticStart(),
                targetEngine.idleShutdownSeconds() != currentEngine.idleShutdownSeconds(),
                targetEngine.lightsWithEngine() != currentEngine.lightsWithEngine(),
                profile.feedback() != current.feedback(),
                profile.signalAutoCancel() != current.signalAutoCancel(),
                profile.comfortSignals() != current.comfortSignals(),
                profile.drivingMode() != current.drivingMode());
    }

    /** Selected preferences only. The optional driving row follows server capability, not effective tuning. */
    public record Differences(boolean lights, boolean autoStart, boolean idleDelay, boolean linked,
                              boolean feedback, boolean autoCancel, boolean comfort, boolean drivingMode) {
        public Differences(boolean lights, boolean autoStart, boolean idleDelay, boolean linked,
                           boolean feedback, boolean autoCancel, boolean comfort) {
            this(lights, autoStart, idleDelay, linked, feedback, autoCancel, comfort, false);
        }

        public int count() {
            return count(true);
        }

        public int count(boolean showDrivingMode) {
            return (lights ? 1 : 0) + (autoStart ? 1 : 0) + (idleDelay ? 1 : 0) + (linked ? 1 : 0)
                    + (feedback ? 1 : 0) + (autoCancel ? 1 : 0) + (comfort ? 1 : 0)
                    + (showDrivingMode && drivingMode ? 1 : 0);
        }
    }

    public boolean available(List<VehicleConfigurationProfile> currentProfiles) {
        return currentProfiles.contains(profile);
    }

    public boolean canApply(List<VehicleConfigurationProfile> currentProfiles, boolean mayConfigure) {
        return !submitted && mayConfigure && available(currentProfiles);
    }

    /** Returns the exact reviewed record once, only while its full contents still exist in the library. */
    public VehicleConfigurationProfile confirm(List<VehicleConfigurationProfile> currentProfiles, boolean mayConfigure) {
        if (!canApply(currentProfiles, mayConfigure)) return null;
        submitted = true;
        return profile;
    }
}
