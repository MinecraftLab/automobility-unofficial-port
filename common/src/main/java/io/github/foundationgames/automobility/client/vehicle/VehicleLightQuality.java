package io.github.foundationgames.automobility.client.vehicle;

import java.util.Locale;

/** Saved client budgets for main headlight detail and nearby colored lamp spill. */
public enum VehicleLightQuality {
    ECONOMY, BALANCED, HIGH, POTATO;

    public static VehicleLightQuality[] displayOrder() {
        return new VehicleLightQuality[]{POTATO, ECONOMY, BALANCED, HIGH};
    }

    public String translationKey() {
        return "vehicle.automobility.effects.quality." + name().toLowerCase(Locale.ROOT);
    }
}

