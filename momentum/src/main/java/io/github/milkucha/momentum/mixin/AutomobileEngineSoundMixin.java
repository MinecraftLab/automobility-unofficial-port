package io.github.milkucha.momentum.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.sound.AutomobileSoundInstance;
import io.github.milkucha.momentum.config.MomentumConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the engine sound pitch formula to make the pitch-bend ceiling configurable.
 *
 * Automobility's original formula:
 *   pitch = 4^(speed - 0.9) + 0.32
 *
 * This hits Minecraft's pitch ceiling (~2.0) at speed ≈ 1.274 blocks/tick = ~91.7 km/h,
 * after which the sound stays flat regardless of further acceleration.
 *
 * Momentum replaces the formula with:
 *   pitch = 4^(speed × (91.7 / ceilingKmh) - 0.9) + 0.32
 *
 * The scale factor (91.7 / ceilingKmh) compresses the exponent so the ceiling is
 * reached at the configured speed instead. Idle pitch (speed = 0) is identical to
 * vanilla since the speed term vanishes.
 *
 * When Momentum is disabled the vanilla formula runs unchanged.
 */
@Mixin(value = AutomobileSoundInstance.EngineSound.class, remap = false)
public abstract class AutomobileEngineSoundMixin {

    private static final float VANILLA_CEILING_KMH = 91.7f;

    @Inject(method = "getPitch", at = @At("HEAD"), cancellable = true)
    private void momentum$enginePitch(AutomobileEntity automobile, CallbackInfoReturnable<Float> cir) {
        if (io.github.foundationgames.automobility.client.vehicle.VehiclePowertrainPresentation.reading(automobile).isPresent()) return;
        MomentumConfig cfg = MomentumConfig.get();
        if (!MomentumConfig.presentationEnabled()) return;

        float ceilingKmh = cfg.sound.enginePitchCeiling;
        float scale = VANILLA_CEILING_KMH / ceilingKmh;
        float speed = (float) automobile.getEffectiveSpeed();
        double exponent = speed * scale - 0.9;
        float pitch = (float) (Math.pow(4.0, exponent) + 0.32);
        cir.setReturnValue(pitch);
    }
}
