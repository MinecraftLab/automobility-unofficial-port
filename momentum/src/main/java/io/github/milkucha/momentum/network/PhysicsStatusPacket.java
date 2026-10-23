package io.github.milkucha.momentum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S2C acknowledgement of the current player's semantic physics declaration. */
public record PhysicsStatusPacket(int revision, long nonce, boolean accepted) implements CustomPacketPayload {
    public static final Type<PhysicsStatusPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("momentum", "physics_status_v1"));

    public static final StreamCodec<FriendlyByteBuf, PhysicsStatusPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PhysicsStatusPacket::revision,
            ByteBufCodecs.LONG, PhysicsStatusPacket::nonce,
            ByteBufCodecs.BOOL, PhysicsStatusPacket::accepted,
            PhysicsStatusPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
