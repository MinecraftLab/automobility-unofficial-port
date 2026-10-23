package io.github.foundationgames.automobility.automobile.light;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Rigid hinge in the exported model's block units (+Y down, -Z forward). */
public final class Mx5PopupGeometry {
    public static final Identifier FRAME = Identifier.fromNamespaceAndPath("automobility", "frame/mx5_na");
    public static final Identifier LEFT_MODEL = Identifier.fromNamespaceAndPath("automobility", "frame/mx5_na_headlights_left");
    public static final Identifier RIGHT_MODEL = Identifier.fromNamespaceAndPath("automobility", "frame/mx5_na_headlights_right");
    public static final Vec3 PIVOT = new Vec3(0, -9.2 / 16, -19.2 / 16);
    public static final float CLOSED_ANGLE_DEGREES = 40;

    private Mx5PopupGeometry() {}

    public static boolean appliesTo(Identifier frameModel) {
        return FRAME.equals(frameModel);
    }

    public static float angleDegrees(float progress) {
        float finite = Float.isFinite(progress) ? Math.clamp(progress, 0, 1) : 0;
        return (1 - finite) * CLOSED_ANGLE_DEGREES;
    }

    public static boolean lightReady(Identifier frameModel, float progress) {
        return !appliesTo(frameModel) || Float.isFinite(progress) && progress >= 1 - 0.000001f;
    }

    public static Vec3 position(Vec3 point, float progress) {
        return direction(point.subtract(PIVOT), progress).add(PIVOT);
    }

    public static Vec3 direction(Vec3 vector, float progress) {
        double angle = Math.toRadians(angleDegrees(progress)), c = Math.cos(angle), s = Math.sin(angle);
        return new Vec3(vector.x, c * vector.y - s * vector.z, s * vector.y + c * vector.z);
    }
    /** One hinge matrix shared by lens/small-light geometry, matching the split body render. */
    public static org.joml.Matrix4f chassis(org.joml.Matrix4f chassis, float progress) {
        return new org.joml.Matrix4f(chassis).translate((float)PIVOT.x, (float)PIVOT.y, (float)PIVOT.z)
                .rotateX((float)Math.toRadians(angleDegrees(progress)))
                .translate((float)-PIVOT.x, (float)-PIVOT.y, (float)-PIVOT.z);
    }
}
