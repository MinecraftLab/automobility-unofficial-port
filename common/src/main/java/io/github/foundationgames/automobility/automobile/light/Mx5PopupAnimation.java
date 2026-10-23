package io.github.foundationgames.automobility.automobile.light;

/** One clock per popup side; there is deliberately no competing aggregate popup clock. */
public final class Mx5PopupAnimation {
    public static final int DURATION_TICKS = 12;
    private final TickDrivenHingeAnimation hinge = new TickDrivenHingeAnimation();

    public static boolean requestedOpen(VehicleLightMode effectiveMode) { return effectiveMode.emitsHeadlightBeam(); }
    public void tick(boolean open) { tick(open, DURATION_TICKS); }
    public void tick(boolean open, int durationTicks) { hinge.tick(open, durationTicks); }
    public float sample(float tickDelta, boolean open) { return hinge.sample(tickDelta, open); }
    public void reset() { hinge.reset(); }
}
