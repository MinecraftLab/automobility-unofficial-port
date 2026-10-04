package io.github.milkucha.momentum.mixin;

import io.github.foundationgames.automobility.automobile.AutomobileStats;
import io.github.milkucha.momentum.config.MomentumConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AutomobileStatsMixin - scales the comfortable speed threshold.
 *
 * Automobility's comfortableSpeed is computed in AutomobileStats.from() from
 * engine/frame/wheel stats. It acts as a soft speed cap: above it, acceleration
 * drops to 25% of normal (see movementTick line ~794). The threshold also affects
 * boost top-up speed and off-road speed caps.
 *
 * Injecting into getComfortableSpeed() at RETURN covers all three call sites in
 * movementTick simultaneously, without needing multiple redirects.
 *
 * A multiplier of 1.0 = vanilla. 1.5 = threshold 50% higher (e.g. 60 km/h → 90 km/h).
 */
@Mixin(value = AutomobileStats.class, remap = false)
public class AutomobileStatsMixin {

    @Inject(method = "getComfortableSpeed", at = @At("RETURN"), cancellable = true)
    private void momentum$scaleComfortableSpeed(CallbackInfoReturnable<Float> cir) {
        if (!MomentumConfig.gameplay().enabled || !MomentumConfig.gameplay().movement.enabled) return;
        cir.setReturnValue(cir.getReturnValue() * MomentumConfig.gameplay().movement.comfortableSpeedMultiplier);
    }
}
