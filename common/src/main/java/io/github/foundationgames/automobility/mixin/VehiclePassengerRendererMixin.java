package io.github.foundationgames.automobility.mixin;

import io.github.foundationgames.automobility.client.vehicle.VehicleRiderBodyPose;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class VehiclePassengerRendererMixin {
    @Inject(method = "solveBodyRot(Lnet/minecraft/world/entity/LivingEntity;FF)F",
            at = @At("RETURN"), cancellable = true)
    private static void automobility$alignVehiclePassengerBody(LivingEntity passenger, float headYaw,
            float tickDelta, CallbackInfoReturnable<Float> cir) {
        if (passenger instanceof Player && passenger.getVehicle() instanceof AutomobileEntity auto) {
            var mount = passenger.getVehicle();
            if (VehicleRiderBodyPose.appliesTo(mount)) {
                // Vanilla extraction computes the head angle from this returned body
                // yaw. Entity rotation, camera rotation and player input stay intact.
                cir.setReturnValue(VehicleRiderBodyPose.bodyYawForVehicle(true,
                        auto.getAutomobileYaw(tickDelta), cir.getReturnValue()));
            }
        }
    }
}
