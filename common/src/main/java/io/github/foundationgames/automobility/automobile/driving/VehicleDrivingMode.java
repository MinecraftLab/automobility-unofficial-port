package io.github.foundationgames.automobility.automobile.driving;

/** A saved vehicle preference. INHERIT follows the current server tune, including custom values. */
public enum VehicleDrivingMode {
    INHERIT(0), TOURING(1), BALANCED(2), SPORT(3);

    private final int id;
    VehicleDrivingMode(int id) { this.id = id; }
    public int id() { return id; }
    public VehicleDrivingMode next() { return values()[(ordinal() + 1) % values().length]; }
    public static boolean isValidId(int id) { return id >= 0 && id < values().length; }
    public static VehicleDrivingMode fromId(int id) { return isValidId(id) ? values()[id] : INHERIT; }
}
