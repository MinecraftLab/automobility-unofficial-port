package io.github.foundationgames.automobility.mixin;
import io.github.foundationgames.automobility.entity.GarageTransferRuntime;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class GaragePendingPlayerPacketMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="shouldHandleMessage",at=@At("HEAD"),cancellable=true)
    private void automobility$pendingInventory(Packet<?> packet,CallbackInfoReturnable<Boolean> cir){
        if(!(packet instanceof net.minecraft.network.protocol.common.ServerboundKeepAlivePacket)&&GarageTransferRuntime.actorPending(player.level().getServer(),player.getUUID()))cir.setReturnValue(false);
    }
}
