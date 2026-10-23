package io.github.foundationgames.automobility.client.lighting;

import net.minecraft.world.phys.Vec3;

/** An elliptical spotlight. No world light levels or game mechanics are changed. */
public record HeadlightBeam(Vec3 origin, Vec3 forward, Vec3 right, Vec3 up,
                            double range, double horizontalTangent, double verticalTangent) {
    public HeadlightBeam {
        if (!origin.isFinite() || !forward.isFinite() || !right.isFinite() || !up.isFinite()
                || !Double.isFinite(range) || !Double.isFinite(horizontalTangent)
                || !Double.isFinite(verticalTangent) || range <= 0
                || horizontalTangent <= 0 || verticalTangent <= 0) {
            throw new IllegalArgumentException("Invalid spotlight parameters");
        }
    }

    /** Smooth angular edge and distance falloff. Surfaces behind the lamp receive no light. */
    public float intensity(Vec3 point) {
        return point == null ? 0 : intensity(point.x, point.y, point.z);
    }

    /** Allocation-free equivalent for candidates that have not materialized a vertex yet. */
    public float intensity(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return 0;
        }
        double deltaX = x - origin.x;
        double deltaY = y - origin.y;
        double deltaZ = z - origin.z;
        double axial = deltaX * forward.x + deltaY * forward.y + deltaZ * forward.z;
        if (axial <= 0 || axial >= range) {
            return 0;
        }
        double horizontal = (deltaX * right.x + deltaY * right.y + deltaZ * right.z) / (0.15 + axial * horizontalTangent);
        double vertical = (deltaX * up.x + deltaY * up.y + deltaZ * up.z) / (0.10 + axial * verticalTangent);
        double radial = horizontal * horizontal + vertical * vertical;
        if (radial >= 1) {
            return 0;
        }
        double angular = 1 - smoothstep(0.45, 1, radial);
        double distance = 1 - smoothstep(range * 0.35, range, axial);
        return (float)(angular * distance * 0.78);
    }

    private static double smoothstep(double lower, double upper, double value) {
        double t = Math.max(0, Math.min(1, (value - lower) / (upper - lower)));
        return t * t * (3 - 2 * t);
    }
}
