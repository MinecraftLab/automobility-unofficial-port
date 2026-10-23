package io.github.milkucha.momentum.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.milkucha.momentum.MomentumCameraPolicy;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks mouse look input from reaching the player while riding an AutomobileEntity
 * with lockCamera enabled.
 *
 * Minecraft applies mouse deltas by calling Entity.changeLookDirection(yaw, pitch)
 * on the local player each tick. Cancelling that call here means no mouse movement
 * ever reaches the player's yaw/pitch - no fighting, no jitter.
 *
 * The END_CLIENT_TICK handler in MomentumClient still runs and sets
 * player.setYaw(auto.getYaw()) each tick, so the camera follows the car's heading
 * as it turns. With no mouse input competing, this is clean and smooth.
 */
@Mixin(Entity.class)
public class CameraLockMixin {

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void momentum$blockMouseLook(double yaw, double pitch, CallbackInfo ci) {
        if (!((Object) this instanceof LocalPlayer player)) return;
        if (!(player.getVehicle() instanceof AutomobileEntity)) return;
        var camera = MomentumConfig.get().camera;
        if (!MomentumCameraPolicy.lockActive(MomentumConfig.presentationEnabled(), camera.enabled, camera.lock)) return;
        ci.cancel();
    }
}
