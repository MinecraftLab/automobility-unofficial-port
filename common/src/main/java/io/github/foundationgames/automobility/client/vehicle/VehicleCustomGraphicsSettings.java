package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.client.lighting.VehicleProjectionPolicy;
import java.util.Objects;

/** Bounded client presentation only; never weakens the optics or freshness of a full projection. */
public record VehicleCustomGraphicsSettings(int priorityFullCars, int visibleFullCars, int coneCars,
                                            Spill spill, int driftParticlesPercent, boolean exhaustSmoke) {
    public enum Spill {
        OFF(VehicleLightQuality.POTATO), LIMITED(VehicleLightQuality.ECONOMY),
        BALANCED(VehicleLightQuality.BALANCED), HIGH(VehicleLightQuality.HIGH);
        private final VehicleLightQuality quality;
        Spill(VehicleLightQuality quality) { this.quality = quality; }
        public VehicleLightQuality quality() { return quality; }
    }

    public VehicleCustomGraphicsSettings {
        Objects.requireNonNull(spill);
        if (priorityFullCars < 1 || priorityFullCars > 7 || visibleFullCars < 0 || visibleFullCars > 6
                || priorityFullCars + visibleFullCars > 7
                || coneCars < 0 || coneCars > 7
                || driftParticlesPercent < 0 || driftParticlesPercent > 100 || driftParticlesPercent % 25 != 0)
            throw new IllegalArgumentException("Custom graphics exceed the supported presentation budgets");
        if (driftParticlesPercent == 75) throw new IllegalArgumentException("Supported smoke amounts:0/25/50/100");
    }

    public static VehicleCustomGraphicsSettings forPreset(VehicleLightQuality quality) {
        var profile = VehicleProjectionPolicy.profile(quality);
        Spill spill = switch (quality) {
            case POTATO -> Spill.OFF;
            case ECONOMY -> Spill.LIMITED;
            case BALANCED -> Spill.BALANCED;
            case HIGH -> Spill.HIGH;
        };
        return new VehicleCustomGraphicsSettings(profile.priorityFull(), profile.viewFull(), profile.cones(), spill, 100, true);
    }

    public VehicleProjectionPolicy.Profile profile() {
        return new VehicleProjectionPolicy.Profile(priorityFullCars, visibleFullCars, coneCars);
    }
}
