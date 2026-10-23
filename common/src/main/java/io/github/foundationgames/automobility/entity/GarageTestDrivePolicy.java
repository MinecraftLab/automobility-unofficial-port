package io.github.foundationgames.automobility.entity;

/** Admission only; these are the existing garage stationary tolerances, not driving physics. */
public final class GarageTestDrivePolicy {
    public static final long LIFETIME_TICKS = 20L * 60 * 20;
    public static final double RADIUS = 512;
    private GarageTestDrivePolicy() {}

    public static boolean stopped(double horizontal, double vertical, double velocitySquared, boolean grounded) {
        return grounded && Double.isFinite(horizontal) && Double.isFinite(vertical) && Double.isFinite(velocitySquared)
                && Math.abs(horizontal) <= .01 && Math.abs(vertical) <= .035 && velocitySquared >= 0 && velocitySquared <= .0025;
    }
    public static boolean withinLifetime(long started, long now) { return started >= 0 && now >= started && now - started < LIFETIME_TICKS; }
    public static boolean withinRadius(double distanceSquared) { return Double.isFinite(distanceSquared) && distanceSquared >= 0 && distanceSquared <= RADIUS * RADIUS; }
    public static boolean soleDriver(boolean owner, boolean driver, int passengers, boolean carRiding) {
        return owner && driver && passengers == 1 && !carRiding;
    }
}
