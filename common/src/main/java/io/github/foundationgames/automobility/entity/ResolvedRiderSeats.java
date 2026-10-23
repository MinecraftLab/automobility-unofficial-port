package io.github.foundationgames.automobility.entity;

import java.util.IdentityHashMap;
import java.util.Map;

/** Client presentation only: remembers the seat actually used by positionRider, not list order. */
public final class ResolvedRiderSeats<P, F> {
    private record Seat<F>(F frame, int index) { }
    private final Map<P, Seat<F>> seats = new IdentityHashMap<>();

    public void capture(P passenger, F frame, int index) {
        if (passenger == null || frame == null || index < 0) {
            forget(passenger);
            return;
        }
        var previous = seats.get(passenger);
        if (previous == null || previous.frame() != frame || previous.index() != index) {
            seats.put(passenger, new Seat<>(frame, index));
        }
    }

    public int index(P passenger, F frame, boolean currentlyMounted) {
        var seat = seats.get(passenger);
        if (!currentlyMounted || seat == null || seat.frame() != frame) {
            forget(passenger);
            return -1;
        }
        return seat.index();
    }

    public void forget(P passenger) { seats.remove(passenger); }
    public void clear() { seats.clear(); }
    int size() { return seats.size(); }
}
