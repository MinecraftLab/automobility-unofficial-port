package io.github.foundationgames.automobility.client.vehicle;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;

/** Compact seated avatar rendering only; vehicle attachments and player physics stay unchanged. */
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

    public static boolean apply(Identifier frame, double seatXPixels, PoseStack pose) {
        if (!appliesTo(frame) || !Double.isFinite(seatXPixels)) return false;
        double inwardPixels = MX5_MODEL_ID.equals(frame) ? 1.8 : DEFENDER_MODEL_ID.equals(frame) ? .6 : 0;
        // At body yaw zero, the prior Y-180 rotation and X/Y inversion leave local +X as vehicle +X.
        // Translate toward the cabin centre before scaling, so the inset is not scaled a second time.
        double localX = -Math.signum(seatXPixels) * Math.min(Math.abs(seatXPixels), inwardPixels) / 16;
        // Local -Y raises the rendered hip after LivingEntityRenderer's Y inversion.
        pose.translate(localX, -HIP_COMPENSATION_BLOCKS, 0);
        pose.scale(SCALE, SCALE, SCALE);
        return true;
    }
}
