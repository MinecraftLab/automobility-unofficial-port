package io.github.foundationgames.automobility.automobile.light;

import java.util.concurrent.ThreadLocalRandom;

/** Small atomic visual state, separate from the established entity metadata schema. */
public final class Mx5MovingParts {
    private long epoch, sequence = 1;
    private boolean remoteBound, hood, showActive;
    private int flags;

    public Mx5MovingParts() { this(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)); }
    public Mx5MovingParts(long epoch) {
        if (epoch <= 0) throw new IllegalArgumentException("Positive incarnation required");
        this.epoch = epoch;
    }

    public boolean update(boolean hood, int flags, boolean active) {
        requireValid(flags, active);
        if (this.hood == hood && this.flags == flags && showActive == active) return false;
        this.hood = hood; this.flags = flags; showActive = active;
        if (sequence == Long.MAX_VALUE) throw new IllegalStateException("Visual sequence exhausted");
        sequence++;
        return true;
    }

    /** Once bound, an old packet can never rebind this entity to another incarnation. */
    public boolean applyRemote(long epoch, long sequence, boolean hood, int flags, boolean active) {
        if (epoch <= 0 || sequence <= 0 || !valid(flags, active)) return false;
        if (remoteBound && (this.epoch != epoch || sequence <= this.sequence)) return false;
        this.epoch = epoch; this.sequence = sequence; this.hood = hood;
        this.flags = flags; showActive = active; remoteBound = true;
        return true;
    }

    public Snapshot snapshot() { return new Snapshot(epoch, sequence, hood, flags, showActive); }
    public long epoch() { return epoch; }
    public boolean remoteBound() { return remoteBound; }
    public boolean hood() { return hood; }
    public int flags() { return flags; }
    public boolean showActive() { return showActive; }
    public static boolean valid(int flags, boolean active) {
        return (flags == 0 || Mx5HeadlightDance.activeFromPacked(flags)) && (active || flags == 0);
    }
    private static void requireValid(int flags, boolean active) {
        if (!valid(flags, active)) throw new IllegalArgumentException("Invalid popup state");
    }
    public record Snapshot(long epoch, long sequence, boolean hood, int flags, boolean active) { }
}
