package io.github.milkucha.momentum.mixin;

import io.github.milkucha.momentum.network.ServerPhysicsSessions;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional RIA: no extra dependency, tracked fields or change to its seat layout. */
@Pseudo
@Mixin(targets = "com.sshakusora.riautomobility.network.SeatSwitchNetworking", remap = false)
public abstract class RiaSeatSessionMixin {
    @Inject(method = "handle(Lnet/minecraft/server/level/ServerPlayer;)Z",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void momentum$requireCompatibleSeatSwitch(ServerPlayer player,
            CallbackInfoReturnable<Boolean> cir) {
        if (!ServerPhysicsSessions.canDrive(player)) {
            ServerPhysicsSessions.warn(player);
            cir.setReturnValue(false);
        }
    }
}
