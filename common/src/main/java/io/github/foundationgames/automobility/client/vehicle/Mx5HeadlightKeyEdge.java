package io.github.foundationgames.automobility.client.vehicle;

/** One toggle per physical press; queued autorepeat and a held key never retrigger a dance. */
public final class Mx5HeadlightKeyEdge {
    private boolean held;

    public boolean press(boolean down, int clicks, boolean eligible) {
        boolean edge = !held && (down || clicks > 0);
        held = down;
        return eligible && edge;
    }

    public void reset() { held = false; }
}
