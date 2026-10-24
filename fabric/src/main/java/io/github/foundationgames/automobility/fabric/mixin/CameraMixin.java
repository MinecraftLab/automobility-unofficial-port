package io.github.foundationgames.automobility.fabric.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.client.vehicle.VehicleCameraDistance;
import io.github.foundationgames.automobility.client.vehicle.VehicleClientControls;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import io.github.foundationgames.automobility.client.vehicle.VehicleRiderRenderScale;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Camera;

@Mixin(value = Camera.class, priority = 1000)
public abstract class CameraMixin {
    @Shadow private Minecraft minecraft;

    @Shadow public abstract Entity entity();

    @Shadow private Vec3 position;
    @Shadow protected abstract void setPosition(Vec3 position);

    /** Correct the eye anchor before vanilla resolves the camera type and all collision rays. */
    @Inject(method = "alignWithEntity(F)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Options;getCameraType()Lnet/minecraft/client/CameraType;", ordinal = 0),
            require = 1, allow = 1)
    private void automobility$seatedEye(float tickDelta, CallbackInfo ci) {
        Entity viewed = entity();
        if (viewed == null || viewed != minecraft.player || !(viewed.getVehicle() instanceof AutomobileEntity car)) return;
        var seat = car.getRenderedCabinSeat(viewed);
        if (seat == null) return; // Includes unresolved/RIA handoffs and rear attachments.
        var frame = car.getFrame().model().modelId();
        double inward = VehicleRiderRenderScale.inwardBlocks(frame, seat.x());
        double vertical = VehicleRiderRenderScale.cameraEyeOffset(frame, viewed.getEyeHeight());
        if (inward == 0 && vertical == 0) return;
        double yaw = Math.toRadians(car.getAutomobileYaw(tickDelta));
        setPosition(position.add(inward * Math.cos(yaw), vertical, inward * Math.sin(yaw)));
    }

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
