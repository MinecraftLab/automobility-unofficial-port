package io.github.foundationgames.automobility.client.vehicle;

import java.util.Objects;

/** One bounded command handoff, bound to object identities rather than reusable entity IDs. */
record PendingVehicleMenu<L,P,V>(Kind kind, L level, P player, V vehicle, Object originalScreen, long tick) {
    static final int MAX_WAIT_TICKS = 40;
    enum Kind { VEHICLE, EFFECTS }
    enum State { WAIT, READY, DISCARD }

    PendingVehicleMenu {
        Objects.requireNonNull(kind); Objects.requireNonNull(level); Objects.requireNonNull(player);
        if (kind == Kind.VEHICLE) Objects.requireNonNull(vehicle);
        else if (vehicle != null) throw new IllegalArgumentException("Effects menu has no vehicle target");
    }

    State evaluate(L currentLevel, P currentPlayer, Object screen, long now,
                   boolean actorAlive, boolean vehicleCurrent) {
        long elapsed = now - tick;
        if (level != currentLevel || player != currentPlayer || !actorAlive
                || kind == Kind.VEHICLE && !vehicleCurrent || elapsed < 0 || elapsed > MAX_WAIT_TICKS)
            return State.DISCARD;
        if (screen == null) return State.READY;
        return originalScreen != null && screen == originalScreen ? State.WAIT : State.DISCARD;
    }
}
