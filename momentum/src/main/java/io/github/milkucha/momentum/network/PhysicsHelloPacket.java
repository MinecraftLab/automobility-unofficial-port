package io.github.milkucha.momentum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** C2S declaration of the semantic physics contract understood by this client. */
public record PhysicsHelloPacket(int revision, long nonce) implements CustomPacketPayload {
    public static final Type<PhysicsHelloPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("momentum", "physics_hello_v1"));

    public static final StreamCodec<FriendlyByteBuf, PhysicsHelloPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PhysicsHelloPacket::revision,
            ByteBufCodecs.LONG, PhysicsHelloPacket::nonce,
            PhysicsHelloPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
