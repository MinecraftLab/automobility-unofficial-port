package io.github.foundationgames.automobility.entity;

/** Placement clearance only; does not change driving, collision dimensions or saved component data. */
public final class GaragePlacementPolicy {
    private GaragePlacementPolicy() {}

    public static double liftForGround(double surfaceY, double boundsMinY) {
        if (!Double.isFinite(surfaceY) || !Double.isFinite(boundsMinY)) return Double.NaN;
        return Math.max(0, surfaceY - boundsMinY) + 0.015;
    }
}
