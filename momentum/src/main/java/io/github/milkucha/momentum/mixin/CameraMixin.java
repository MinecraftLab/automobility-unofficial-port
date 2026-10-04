package io.github.milkucha.momentum.mixin;

import io.github.milkucha.momentum.MomentumBrakeState;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Subtracts brakeZoomOffset from the camera FOV before the projection matrix is built.
 */
@Mixin(Camera.class)
public class CameraMixin {

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void momentum$applyBrakeZoom(float tickDelta, CallbackInfoReturnable<Float> cir) {
        float offset = MomentumBrakeState.brakeZoomOffset;
        if (offset == 0f) return;
        cir.setReturnValue(Math.max(1.0f, cir.getReturnValue() - offset));
    }
}
