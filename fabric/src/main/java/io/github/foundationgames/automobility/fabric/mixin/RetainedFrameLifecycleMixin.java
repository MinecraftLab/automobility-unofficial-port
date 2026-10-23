package io.github.foundationgames.automobility.fabric.mixin;

import io.github.foundationgames.automobility.fabric.render.RetainedT3Feature;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class RetainedFrameLifecycleMixin {
    @Inject(method = "renderFrame(Z)V", at = @At("HEAD"), require = 1)
    private void automobility$releaseCanceledRetainedSubmits(boolean tick, CallbackInfo ci) {
        RetainedT3Feature.beginFrame();
        io.github.foundationgames.automobility.client.lighting.VehicleHeadlightRenderer.beginFrame();
    }
}
