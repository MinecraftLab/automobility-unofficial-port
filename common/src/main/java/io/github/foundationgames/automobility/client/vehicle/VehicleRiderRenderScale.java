package io.github.foundationgames.automobility.client.vehicle;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;

/** Matched seated-avatar and camera presentation; vehicle attachments and player physics stay unchanged. */
public final class VehicleRiderRenderScale {
    public static final Identifier MX5_MODEL_ID = Identifier.parse("automobility:frame/mx5_na");
    public static final Identifier DEFENDER_MODEL_ID = Identifier.parse("automobility:frame/defender_90");
    public static final float VANILLA_SCALE = .9375f;
    public static final float SCALE = .75f;
    // LivingEntityRenderer translates -1.501 after AvatarRenderer.scale. The model hip is Y=12px.
    public static final float HIP_COMPENSATION_BLOCKS = (VANILLA_SCALE - SCALE) * (1.501f - 12f / 16f);

    private VehicleRiderRenderScale() { }

    public static boolean appliesTo(Identifier frame) {
        return MX5_MODEL_ID.equals(frame) || DEFENDER_MODEL_ID.equals(frame);
    }

    /** The same cabin inset is used by the drawn avatar and the camera eye. */
    public static double inwardBlocks(Identifier frame, double seatXPixels) {
        if (!appliesTo(frame) || !Double.isFinite(seatXPixels)) return 0;
        double pixels = MX5_MODEL_ID.equals(frame) ? 1.8 : .6;
        return -Math.signum(seatXPixels) * Math.min(Math.abs(seatXPixels), pixels) / 16;
    }

    private static double seatingDrop(Identifier frame) {
        // Native167 puts the MX-5 driver's eyes above its windscreen header.
        return MX5_MODEL_ID.equals(frame) ? .10 : 0;
    }

    /** Scale the eye about the retained model hip; do not move the entity or its attachment. */
    public static double cameraEyeOffset(Identifier frame, double eyeHeight) {
        if (!appliesTo(frame) || !Double.isFinite(eyeHeight) || eyeHeight <= 0) return 0;
        double hip = VANILLA_SCALE * (1.501 - 12.0 / 16);
        return (SCALE / VANILLA_SCALE - 1) * Math.max(0, eyeHeight - hip) - seatingDrop(frame);
    }

    public static boolean apply(Identifier frame, double seatXPixels, PoseStack pose) {
        if (!appliesTo(frame) || !Double.isFinite(seatXPixels)) return false;
        // At body yaw zero, the prior Y-180 rotation and X/Y inversion leave local +X as vehicle +X.
        // Translate toward the cabin centre before scaling, so the inset is not scaled a second time.
        double localX = inwardBlocks(frame, seatXPixels);
        // Local -Y raises the rendered hip after LivingEntityRenderer's Y inversion.
        pose.translate(localX, -HIP_COMPENSATION_BLOCKS + seatingDrop(frame), 0);
        pose.scale(SCALE, SCALE, SCALE);
        return true;
    }
}
