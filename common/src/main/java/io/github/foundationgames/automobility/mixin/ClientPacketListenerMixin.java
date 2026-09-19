package io.github.foundationgames.automobility.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Unique
    private AutomobileEntity automobility$positionTarget;
    @Unique
    private Vec3 automobility$previousPosition;
    @Unique
    private boolean automobility$authoritativePosition;

    @Unique
    private void automobility$capturePosition(Entity entity, boolean authoritative) {
        if (this.automobility$positionTarget != null) {
            this.automobility$positionTarget.clientEndPositionUpdate();
        }
        this.automobility$positionTarget = null;
        this.automobility$previousPosition = null;
        this.automobility$authoritativePosition = false;
        if (entity instanceof AutomobileEntity automobile) {
            this.automobility$positionTarget = automobile;
            this.automobility$previousPosition = automobile.position();
            this.automobility$authoritativePosition = authoritative;
            automobile.clientBeginPositionUpdate(authoritative);
        }
    }

    @Unique
    private void automobility$finishPosition(Entity currentEntity) {
        var automobile = this.automobility$positionTarget;
        var previousPosition = this.automobility$previousPosition;
        boolean authoritative = this.automobility$authoritativePosition;
        this.automobility$positionTarget = null;
        this.automobility$previousPosition = null;
        this.automobility$authoritativePosition = false;
        if (automobile != null) automobile.clientEndPositionUpdate();
        if (automobile == null || previousPosition == null || currentEntity != automobile) {
            return;
        }

        if (authoritative) {
            automobile.clientOnAuthoritativePositionApplied(previousPosition);
        } else {
            automobile.clientOnRelativePositionApplied(previousPosition);
        }
    }

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
        var self = (ClientPacketListener) (Object) this;
        this.automobility$capturePosition(self.getLevel().getEntity(packet.id()), true);
    }

    @Inject(method = "handleTeleportEntity", at = @At("RETURN"))
    private void automobility$refreshAutomobileAfterTeleport(
            ClientboundTeleportEntityPacket packet,
            CallbackInfo ci
    ) {
        var self = (ClientPacketListener) (Object) this;
        this.automobility$finishPosition(self.getLevel().getEntity(packet.id()));
    }

    @Inject(
            method = "handleEntityPositionSync",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void automobility$captureAutomobileBeforePositionSync(
            ClientboundEntityPositionSyncPacket packet,
            CallbackInfo ci
    ) {
        var self = (ClientPacketListener) (Object) this;
        var entity = self.getLevel().getEntity(packet.id());
        boolean hardCorrection = false;
        if (entity instanceof AutomobileEntity automobile) {
            var target = packet.position().endPosition();
            var codec = automobile.getPositionCodec();
            hardCorrection = io.github.foundationgames.automobility.entity.AutomobilePositionUpdatePolicy
                    .exceedsRelativeEncoding(codec.encodeX(target), codec.encodeY(target), codec.encodeZ(target))
                    // Vanilla also snaps unticking or very distant entities, even when the
                    // delta from the previous network endpoint still fits a relative packet.
                    || !self.getLevel().isTickingEntity(automobile)
                    || automobile.position().distanceToSqr(target) > 4096.0;
        }
        this.automobility$capturePosition(entity, hardCorrection);
    }

    @Inject(method = "handleEntityPositionSync", at = @At("RETURN"))
    private void automobility$refreshAutomobileAfterPositionSync(
            ClientboundEntityPositionSyncPacket packet,
            CallbackInfo ci
    ) {
        var self = (ClientPacketListener) (Object) this;
        this.automobility$finishPosition(self.getLevel().getEntity(packet.id()));
    }

    @Inject(
            method = "handleMoveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void automobility$captureAutomobileBeforeRelativeMove(
            ClientboundMoveEntityPacket packet,
            CallbackInfo ci
    ) {
        var self = (ClientPacketListener) (Object) this;
        this.automobility$capturePosition(packet.hasPosition() ? packet.getEntity(self.getLevel()) : null, false);
    }

    @Inject(method = "handleMoveEntity", at = @At("RETURN"))
    private void automobility$refreshAutomobileAfterRelativeMove(
            ClientboundMoveEntityPacket packet,
            CallbackInfo ci
    ) {
        var self = (ClientPacketListener) (Object) this;
        this.automobility$finishPosition(packet.getEntity(self.getLevel()));
    }
}
