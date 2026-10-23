package io.github.foundationgames.automobility.util.network.content;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CoreContentReply(int revision, long nonce, int supported) implements CustomPacketPayload {
    public static final int WIRE_BYTES = 16;
    public static final Type<CoreContentReply> TYPE = new Type<>(Identifier.parse("automobility:content_reply_v1"));
    public static final StreamCodec<FriendlyByteBuf, CoreContentReply> CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeInt(value.revision()); buffer.writeLong(value.nonce()); buffer.writeInt(value.supported()); },
            buffer -> {
                if (buffer.readableBytes() != WIRE_BYTES) throw new DecoderException("Invalid Automobility content reply length");
                return new CoreContentReply(buffer.readInt(), buffer.readLong(), buffer.readInt());
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
