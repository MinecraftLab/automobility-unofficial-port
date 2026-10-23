package io.github.foundationgames.automobility.client.lighting;

import net.minecraft.world.phys.Vec3;

/** Scalar forms of the former temporary-vector expressions, with unchanged double grouping. */
final class HeadlightPatchMath {
    private HeadlightPatchMath() {}

    static double world(double local, int block, double normal, double offset) {
        return (local + block) + normal * offset;
    }

    static double center(double a, double b, double c, double d) {
        double result = 0;
        result += a * 0.25; result += b * 0.25; result += c * 0.25; result += d * 0.25;
        return result;
    }

    static double facing(double x, double y, double z, Vec3 origin, Vec3 normal) {
        return (x - origin.x) * normal.x + (y - origin.y) * normal.y + (z - origin.z) * normal.z;
    }

    static double blockCenterDistanceSquared(int x, int y, int z, Vec3 origin) {
        double dx = (x + 0.5) - origin.x, dy = (y + 0.5) - origin.y, dz = (z + 0.5) - origin.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
