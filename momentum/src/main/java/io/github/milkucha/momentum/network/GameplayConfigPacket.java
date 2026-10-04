package io.github.milkucha.momentum.network;

import io.github.milkucha.momentum.config.MomentumConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record GameplayConfigPacket(int protocolVersion, String json) implements CustomPacketPayload {
    public static final int PROTOCOL_VERSION = 1;
    private static final int MAX_CONFIG_LENGTH = 32 * 1024;

    public static final Type<GameplayConfigPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("momentum", "gameplay_config_v1"));

    public static final StreamCodec<FriendlyByteBuf, GameplayConfigPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GameplayConfigPacket::protocolVersion,
            ByteBufCodecs.stringUtf8(MAX_CONFIG_LENGTH), GameplayConfigPacket::json,
            GameplayConfigPacket::new
    );

    public static GameplayConfigPacket current() {
        return new GameplayConfigPacket(PROTOCOL_VERSION, MomentumConfig.serializeForSync());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
