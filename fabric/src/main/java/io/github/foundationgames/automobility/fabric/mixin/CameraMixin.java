package io.github.foundationgames.automobility.fabric.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.client.vehicle.VehicleCameraDistance;
import io.github.foundationgames.automobility.client.vehicle.VehicleClientControls;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import net.minecraft.client.Camera;

@Mixin(value = Camera.class, priority = 1000)
public abstract class CameraMixin {
    @Shadow private Minecraft minecraft;

    @Shadow public abstract Entity entity();

    /** Both third-person views only; vanilla still resolves all eight collision rays. */
    @ModifyArg(method = "alignWithEntity(F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"),
            index = 0, require = 1, allow = 1)
    private float automobility$vehicleCameraDistance(float vanillaRequested) {
        Entity viewed = entity();
        if (viewed == null || viewed != minecraft.player || !(viewed.getVehicle() instanceof AutomobileEntity))
            return vanillaRequested;
        return VehicleCameraDistance.requested(vanillaRequested,
                VehicleClientControls.preferences().cameraDistanceBlocks());
    }

    // Compose instead of cancelling RETURN; Momentum then applies its final brake clamp.
    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"), require = 1)
    private float automobility$applyBoostFovEffect(float original, float tickDelta) {
        var player = minecraft.player;
        if (player != null && player.getVehicle() instanceof AutomobileEntity auto) {
            float boostFov = (float)(Math.sqrt(Math.max(0, auto.getBoostSpeed(tickDelta))) * 18 * minecraft.options.fovEffectScale().get());
            return original + boostFov;
        }
        return original;
    }
}
