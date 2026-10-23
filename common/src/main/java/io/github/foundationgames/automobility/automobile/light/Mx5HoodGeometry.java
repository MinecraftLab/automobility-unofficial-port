package io.github.foundationgames.automobility.automobile.light;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/** Closed bind pose. The bonnet pivots behind the headlight cut-outs (+Y down). */
public final class Mx5HoodGeometry {
    public static final Identifier MODEL = Identifier.fromNamespaceAndPath("automobility", "frame/mx5_na_hood");
    public static final Vec3 PIVOT = new Vec3(0, -8.843333333333334 / 16, -8.55 / 16);
    public static final float OPEN_ANGLE_DEGREES = -65;
    public static final float ENGINE_SCALE = .65f;
    private Mx5HoodGeometry() {}

    public static boolean appliesTo(Identifier model) { return Mx5PopupGeometry.appliesTo(model); }
    public static float angleDegrees(float progress) {
        return OPEN_ANGLE_DEGREES * (Float.isFinite(progress) ? Math.clamp(progress, 0, 1) : 0);
    }
    public static Vec3 position(Vec3 point, float progress) {
        var v=point.subtract(PIVOT);double angle=Math.toRadians(angleDegrees(progress)),c=Math.cos(angle),s=Math.sin(angle);
        return new Vec3(v.x,c*v.y-s*v.z,s*v.y+c*v.z).add(PIVOT);
    }
    /** Visual bounds only; raised bonnet never changes driving collision boxes. */
    public static AABB cullingBounds(AABB body, double carY, double wheelRadiusPixels) {
        if (!Double.isFinite(carY)) return body;
        double raise=Double.isFinite(wheelRadiusPixels)?Math.max(0,wheelRadiusPixels)/16:0;
        return new AABB(body.minX,body.minY,body.minZ,body.maxX,
                Math.max(body.maxY,carY+raise+27.5/16+.16),body.maxZ);
    }
    public static boolean mayOpen(boolean engineRunning, double speed, double boost) {
        return !engineRunning && Double.isFinite(speed) && Double.isFinite(boost)
                && Math.abs(speed) <= .02 && Math.abs(boost) <= .02;
    }
    /** A rigid hinge preserves distance to the pivot, including rolled/pitched vehicles. */
    public static AABB cullingBounds(AABB body, org.joml.Matrix4f chassis, Vec3 origin) {
        var pivot = new Vec3(chassis.transformPosition(PIVOT.toVector3f())).add(origin);
        if (!Double.isFinite(pivot.x) || !Double.isFinite(pivot.y) || !Double.isFinite(pivot.z)) return body.inflate(3);
        double radius = 1.25; // Authored maximum is 1.237362 blocks, with numerical margin.
        return body.minmax(new AABB(pivot.x-radius, pivot.y-radius, pivot.z-radius,
                pivot.x+radius, pivot.y+radius, pivot.z+radius));
    }
}
