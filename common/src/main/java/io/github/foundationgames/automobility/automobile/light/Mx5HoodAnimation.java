package io.github.foundationgames.automobility.automobile.light;

public final class Mx5HoodAnimation {
    public static final int DURATION_TICKS = 16;
    private final TickDrivenHingeAnimation hinge = new TickDrivenHingeAnimation();

    public void tick(boolean open) { hinge.tick(open, DURATION_TICKS); }
    public float sample(float tickDelta, boolean open) { return hinge.sample(tickDelta, open); }
    public void reset() { hinge.reset(); }
}
