package io.github.foundationgames.automobility.client.lighting;

import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;

import java.util.List;

/** Pure chassis/face geometry shared by visible lamp rendering and geometry checks. */
public final class HeadlightLampGeometry {
    public static final int FULL_BRIGHT = 0x00F000F0;
    public static final int BEZEL_COLOR = 0xFF15181C;
    public static final int BRAKE_COLOR = 0xFFFF222A;
    public static final int REVERSE_COLOR = 0xFFFFF8EE;
    private static final double BEZEL_RECESS = -0.01 / 16;

    private HeadlightLampGeometry() {}

    /** Same ordered transforms as the body and VehicleHeadlightRenderer.beams. */
    public static Matrix4f chassis(float verticalOffset, Quaternionfc angularRotation, float yaw,
                                   float wheelRadiusPx, float bounce, float engineShake) {
        return new Matrix4f()
                .translate(0, verticalOffset, 0)
                .rotate(angularRotation)
                .rotateZ((float)Math.PI)
                .rotateY((float)Math.toRadians(yaw + 180))
                .translate(0, -wheelRadiusPx / 16 + bounce + engineShake, 0);
    }

    public static List<Vec3> bezel(VehicleHeadlightLayout.LampFace face) {
        return rectangle(face, face.width(), face.height(), BEZEL_RECESS);
    }

    public static List<Vec3> lens(VehicleHeadlightLayout.LampFace face) {
        return rectangle(face, face.width() * 0.8, face.height() * 0.7, 0);
    }

    private static List<Vec3> rectangle(VehicleHeadlightLayout.LampFace face, double width, double height,
                                        double normalOffset) {
        Vec3 center = face.center().add(face.normal().scale(normalOffset));
        Vec3 right = face.right().scale(width / 2);
        Vec3 down = face.down().scale(height / 2);
        // Down then right is outward-facing in the model's y-down coordinates.
        return List.of(center.subtract(right).subtract(down), center.subtract(right).add(down),
                center.add(right).add(down), center.add(right).subtract(down));
    }

    /** Takes the effective mode; AUTO's selected setting is resolved by the server first. */
    public static LensAppearance appearance(VehicleLightMode effectiveMode, int ambientLight) {
        if (effectiveMode == VehicleLightMode.OFF) {
            return new LensAppearance(0xFF394B55, ambientLight, false);
        }
        if (effectiveMode == VehicleLightMode.POSITION) {
            // Dim through RGB, retaining opaque alpha so shader alpha tests cannot erase the lens.
            return new LensAppearance(0xFF817D70, FULL_BRIGHT, true);
        }
        return new LensAppearance(effectiveMode == VehicleLightMode.HIGH ? 0xFFFFFCF3 : 0xFFFFF4DC,
                FULL_BRIGHT, true);
    }

    /** Steady rear position lights; HIGH does not brighten them or create rear beams. */
    public static LensAppearance rearAppearance(VehicleLightMode effectiveMode, int ambientLight) {
        return rearAppearance(effectiveMode, false, ambientLight);
    }

    /** The brake raises intensity in the same saturated red hue, even with headlamps off. */
    public static LensAppearance rearAppearance(VehicleLightMode effectiveMode, boolean brakeLit, int ambientLight) {
        if (brakeLit) return new LensAppearance(BRAKE_COLOR, FULL_BRIGHT, true);
        return effectiveMode == VehicleLightMode.OFF
                ? new LensAppearance(0xFF572127, ambientLight, false)
                : new LensAppearance(0xFF781014, FULL_BRIGHT, true);
    }

    public static LensAppearance reverseAppearance(boolean lit, int ambientLight) {
        return lit ? new LensAppearance(REVERSE_COLOR, FULL_BRIGHT, true)
                : new LensAppearance(0xFF687076, ambientLight, false);
    }

    /** The server supplies the signal's phase; dark lenses still use normal world light. */
    public static LensAppearance signalAppearance(boolean lit, int ambientLight) {
        return lit ? new LensAppearance(0xFFFFAE24, FULL_BRIGHT, true)
                : new LensAppearance(0xFF5B3815, ambientLight, false);
    }

    public record LensAppearance(int color, int light, boolean emissive) {}
}
