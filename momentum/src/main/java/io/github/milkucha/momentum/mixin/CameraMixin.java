package io.github.milkucha.momentum.mixin;

import io.github.milkucha.momentum.MomentumBrakeState;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/**
 * Subtracts brakeZoomOffset from the camera FOV before the projection matrix is built.
 */
// Applied after Core (1000): keep the minimum-FOV clamp last, even during boosting.
@Mixin(value = Camera.class, priority = 1100)
public class CameraMixin {

    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"), require = 1)
    private float momentum$applyBrakeZoom(float original, float tickDelta) {
        float offset = MomentumBrakeState.brakeZoomDisplay.sample(tickDelta);
        if (offset == 0f) return original;
        return Math.max(1.0f, original - offset);
    }
}
