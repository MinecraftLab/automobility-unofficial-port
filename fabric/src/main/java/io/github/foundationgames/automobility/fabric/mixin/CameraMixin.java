package io.github.foundationgames.automobility.fabric.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.Camera;

@Mixin(Camera.class)
public class CameraMixin {
    @Shadow private Minecraft minecraft;

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void automobility$applyBoostFovEffect(float tickDelta, CallbackInfoReturnable<Float> cir) {
        var player = minecraft.player;
        if (player != null && player.getVehicle() instanceof AutomobileEntity auto) {
            float boostFov = (float)(Math.sqrt(Math.max(0, auto.getBoostSpeed(tickDelta))) * 18 * minecraft.options.fovEffectScale().get());
            cir.setReturnValue(cir.getReturnValue() + boostFov);
        }
    }
}
