package io.github.foundationgames.automobility.mixin;

import io.github.foundationgames.automobility.client.vehicle.UnifiedVehicleHud;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Invalidate once metrics change, rather than scanning text during every frame. */
@Mixin(FontManager.class)
public abstract class VehicleHudFontReloadMixin {
    @Inject(method="apply(Lnet/minecraft/client/gui/font/FontManager$Preparation;Lnet/minecraft/util/profiling/ProfilerFiller;)V",at=@At("TAIL"))
    private void automobility$fontReload(CallbackInfo ci) {UnifiedVehicleHud.invalidateFontLayout();}

    @Inject(method="updateOptions(Lnet/minecraft/client/Options;)V",at=@At("TAIL"))
    private void automobility$fontOptions(CallbackInfo ci) {UnifiedVehicleHud.invalidateFontLayout();}
}
