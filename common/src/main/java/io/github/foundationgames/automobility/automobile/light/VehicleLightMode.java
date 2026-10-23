package io.github.foundationgames.automobility.automobile.light;

import com.mojang.serialization.Codec;

/** Stable saved and network IDs; AUTO is a selection, never an emitted beam. */
public enum VehicleLightMode {
    OFF(0, "off"),
    LOW(1, "low"),
    HIGH(2, "high"),
    AUTO(3, "auto"),
    POSITION(4, "position");

    public static final Codec<VehicleLightMode> CODEC = Codec.INT.xmap(VehicleLightMode::fromId, VehicleLightMode::id);

    private final int id;
    private final String name;

    VehicleLightMode(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int id() {
        return this.id;
    }

    public String translationKey() {
        return "vehicle.automobility.light.mode." + this.name;
    }

    /** Beam policy for an effective mode; AUTO must first resolve on the server. */
    public boolean emitsHeadlightBeam() {
        return this == LOW || this == HIGH;
    }

    /** Front position and rear running lamps are shared by all effective lit modes. */
    public boolean hasPositionLights() {
        return this == POSITION || this == LOW || this == HIGH;
    }

    public static boolean isValidId(int id) {
        return id >= OFF.id && id <= POSITION.id;
    }

    /** Unknown or absent saved settings must not turn on high beams. */
    public static VehicleLightMode fromId(int id) {
        return switch (id) {
            case 1 -> LOW;
            case 2 -> HIGH;
            case 3 -> AUTO;
            case 4 -> POSITION;
            default -> OFF;
        };
    }
}
