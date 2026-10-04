package io.github.foundationgames.automobility.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
    @Shadow
    public ClientInput input;

    @Shadow @Final protected Minecraft minecraft;

    @Inject(method = "rideTick", at = @At("TAIL"))
    public void automobility$setAutomobileInputs(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer)(Object)this;
        if (self.getVehicle() instanceof AutomobileEntity vehicle && vehicle.isDriving(self)) {
            var keyPresses = this.input.keyPresses;
            if (Platform.get().controller().inControllerMode() && minecraft.screen == null) {
                vehicle.provideClientInput(
                        Platform.get().controller().accelerating(),
                        Platform.get().controller().braking(),
                        keyPresses.left(),
                        keyPresses.right(),
                        Platform.get().controller().drifting(),
                        keyPresses.sprint()
                );
            } else {
                vehicle.provideClientInput(
                        keyPresses.forward(),
                        keyPresses.backward(),
                        keyPresses.left(),
                        keyPresses.right(),
                        keyPresses.jump(),
                        keyPresses.sprint()
                );
            }
        }
    }
}
