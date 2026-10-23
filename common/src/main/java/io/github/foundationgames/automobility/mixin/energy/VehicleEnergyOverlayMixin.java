package io.github.foundationgames.automobility.mixin.energy;

import io.github.foundationgames.automobility.client.vehicle.VehicleEnergyHud;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses only the structurally identified ordinary PW notice, including repeated narration. */
@Mixin(ChatListener.class)
public abstract class VehicleEnergyOverlayMixin {
    @Inject(method="handleOverlay",at=@At("HEAD"),cancellable=true,require=1)
    private void automobility$integratedBatteryNotice(Component text,CallbackInfo ci) {
        if(VehicleEnergyHud.suppressBatteryNotice(text)) ci.cancel();
    }
}
