package io.github.foundationgames.automobility.client.vehicle;

/** Read-only presentation of confirmed provider data; never an energy store or estimate. */
public record VehicleEnergyReading(Kind kind, State state, long stored, long capacity, boolean diesel) {
    public enum Kind { NONE, FUEL, BATTERY }
    public enum State { NORMAL, LOW, CRITICAL, EMPTY, WRONG_FUEL, UNAVAILABLE }
    public static final VehicleEnergyReading NONE = new VehicleEnergyReading(Kind.NONE, State.UNAVAILABLE, 0, 0, false);

    public static VehicleEnergyReading unavailable(Kind kind) {
        return new VehicleEnergyReading(kind, State.UNAVAILABLE, 0, 0, false);
    }

    public static VehicleEnergyReading of(Kind kind, long stored, long capacity, boolean wrongFuel, boolean diesel) {
        if (kind == Kind.NONE) return NONE;
        if (stored < 0 || capacity <= 0 || stored > capacity) return unavailable(kind);
        double ratio = stored / (double) capacity;
        State state = wrongFuel && stored > 0 ? State.WRONG_FUEL : stored == 0 ? State.EMPTY
                : ratio <= .10 ? State.CRITICAL : ratio <= .25 ? State.LOW : State.NORMAL;
        return new VehicleEnergyReading(kind, state, stored, capacity, diesel);
    }

    public boolean present() { return kind != Kind.NONE; }
    public boolean known() { return present() && state != State.UNAVAILABLE; }

    /** Partial tanks never look empty or full solely because of display rounding. */
    public int percent() {
        if (!known()) return -1;
        if (stored == 0) return 0;
        if (stored == capacity) return 100;
        return Math.clamp((int) Math.floor(stored / (double) capacity * 100), 1, 99);
    }

    public int filledSegments(int count) {
        if (!known() || stored == 0 || count <= 0) return 0;
        return Math.clamp((int) Math.ceil(stored / (double) capacity * count), 1, count);
    }
}
