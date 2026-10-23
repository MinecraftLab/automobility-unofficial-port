package io.github.foundationgames.automobility.mixin.energy;

import io.github.foundationgames.automobility.client.vehicle.VehicleEnergyHud;
import io.github.foundationgames.automobility.client.vehicle.VehicleEnergyReading;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leaves refuelling progress, low-fuel sounds and every gameplay callback untouched. */
@Pseudo
@Mixin(targets="dev.rosenvik.pumpsandpistons.client.PumpsAndPistonsAutomobilityClient",remap=false)
public abstract class PumpsFuelGaugeMixin {
    @Inject(method="renderGauge(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",at=@At("HEAD"),cancellable=true,require=1,remap=false)
    private static void automobility$integratedFuelGauge(GuiGraphicsExtractor graphics,CallbackInfo ci) {
        if(VehicleEnergyHud.owns(VehicleEnergyReading.Kind.FUEL)) ci.cancel();
    }
}
