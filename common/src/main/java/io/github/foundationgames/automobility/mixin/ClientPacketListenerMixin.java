package io.github.foundationgames.automobility.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Unique
    private AutomobileEntity automobility$teleportTarget;
    @Unique
    private double automobility$preTeleportY;

    @Inject(
            method = "handleTeleportEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void automobility$captureAutomobileBeforeTeleport(
            ClientboundTeleportEntityPacket packet,
            CallbackInfo ci
    ) {
        this.automobility$teleportTarget = null;
        var self = (ClientPacketListener) (Object) this;
        var entity = self.getLevel().getEntity(packet.id());
        if (entity instanceof AutomobileEntity automobile) {
            this.automobility$teleportTarget = automobile;
            this.automobility$preTeleportY = automobile.getY();
        }
    }

    @Inject(method = "handleTeleportEntity", at = @At("RETURN"))
    private void automobility$refreshAutomobileAfterTeleport(
            ClientboundTeleportEntityPacket packet,
            CallbackInfo ci
    ) {
        var automobile = this.automobility$teleportTarget;
        this.automobility$teleportTarget = null;
        if (automobile == null) {
            return;
        }

        var self = (ClientPacketListener) (Object) this;
        if (self.getLevel().getEntity(packet.id()) == automobile) {
            automobile.clientOnTeleportApplied(this.automobility$preTeleportY);
        }
    }
}
