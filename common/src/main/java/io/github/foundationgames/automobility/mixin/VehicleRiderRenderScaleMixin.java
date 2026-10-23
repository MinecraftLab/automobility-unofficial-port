package io.github.foundationgames.automobility.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.foundationgames.automobility.client.vehicle.VehicleRiderRenderScale;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class VehicleRiderRenderScaleMixin {
    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD"), cancellable = true)
    private void automobility$scaleVehicleRider(AvatarRenderState state, PoseStack pose, CallbackInfo ci) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(state.id) instanceof Player player
                && player.getVehicle() instanceof AutomobileEntity auto) {
            var model = auto.getFrame().model();
            var seat = auto.getRenderedCabinSeat(player);
            // A rideable rear attachment is outside the cabin and keeps the vanilla avatar.
            if (seat != null && VehicleRiderRenderScale.apply(model.modelId(), seat.x(), pose)) ci.cancel();
        }
    }
}
