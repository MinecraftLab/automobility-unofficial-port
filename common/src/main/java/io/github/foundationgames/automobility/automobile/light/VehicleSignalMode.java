package io.github.foundationgames.automobility.automobile.light;

/** Stable operational signal IDs, independent of the selected headlamp mode. */
public enum VehicleSignalMode {
    OFF(0), LEFT(1), RIGHT(2), HAZARD(3);
    private final int id;
    VehicleSignalMode(int id) { this.id = id; }
    public int id() { return id; }
    public boolean directional() { return this == LEFT || this == RIGHT; }
    public boolean left() { return this == LEFT || this == HAZARD; }
    public boolean right() { return this == RIGHT || this == HAZARD; }
    public static boolean isValidId(int id) { return id >= 0 && id <= 3; }
    public static VehicleSignalMode fromId(int id) {
        return switch (id) { case 1 -> LEFT; case 2 -> RIGHT; case 3 -> HAZARD; default -> OFF; };
    }
}
