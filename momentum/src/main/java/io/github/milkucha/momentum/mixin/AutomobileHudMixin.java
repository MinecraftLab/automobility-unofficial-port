package io.github.milkucha.momentum.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.screen.AutomobileHud;
import io.github.milkucha.momentum.config.MomentumConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AutomobileHudMixin - suppresses Automobility's built-in speedometer so
 * Momentum's own HUD panel renders in its place without overlap.
 *
 * We only cancel renderSpeedometer(). The renderControlHints() call (key binding
 * hints that fade in when the car is idle) is intentionally untouched.
 *
 * Note: the correct type here is GuiGraphics (the Mojang/common name used in
 * Automobility's source), NOT DrawContext (which is Fabric's yarn-mapped alias).
 * Since we set remap=false, we use Automobility's own unobfuscated names.
 */
@Mixin(value = AutomobileHud.class, remap = false)
public abstract class AutomobileHudMixin {

    @Inject(
        method = "renderSpeedometer",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void momentum$suppressOriginalSpeedometer(
            GuiGraphicsExtractor graphics,
            AutomobileEntity auto,
            CallbackInfo ci) {
        MomentumConfig cfg = MomentumConfig.get();
        if (!MomentumConfig.gameplay().enabled || !cfg.barHud.enabled) return;
        ci.cancel();
    }
}
