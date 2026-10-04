package io.github.milkucha.momentum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S packet that carries the current state of the 2 Momentum keys:
 * brake and drift.
 *
 * Sent by the client in START_CLIENT_TICK whenever any key state changes.
 * Received on the server to populate ServerKeyState so AutomobileEntityMixin
 * can read the correct key state regardless of logical side.
 */
public record KeyStatePacket(boolean brake, boolean drift) implements CustomPacketPayload {

    public static final Type<KeyStatePacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("momentum", "key_state"));

    public static final StreamCodec<FriendlyByteBuf, KeyStatePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, KeyStatePacket::brake,
        ByteBufCodecs.BOOL, KeyStatePacket::drift,
        KeyStatePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
