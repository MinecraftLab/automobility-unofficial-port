package io.github.foundationgames.automobility.util.network.content;

import io.github.foundationgames.automobility.automobile.light.Mx5MovingParts;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

public record Mx5State(Identifier dimension, int entityId, UUID uuid, long epoch, long sequence,
                       boolean hood, int flags, boolean active) implements CustomPacketPayload {
    public static final Type<Mx5State> TYPE = new Type<>(Identifier.parse("automobility:mx5_state_v1"));
    public static final StreamCodec<FriendlyByteBuf, Mx5State> CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeUtf(p.dimension.toString(), 128); b.writeInt(p.entityId); b.writeUUID(p.uuid);
                b.writeLong(p.epoch); b.writeLong(p.sequence);
                b.writeByte(p.hood ? 1 : 0); b.writeByte(p.flags); b.writeByte(p.active ? 1 : 0);
            }, b -> {
                var dimension = Identifier.tryParse(b.readUtf(128));
                if (dimension == null || b.readableBytes() != 39) throw new DecoderException("Invalid MX5 state length");
                int id = b.readInt(); var uuid = b.readUUID(); long epoch = b.readLong(), sequence = b.readLong();
                int hood = b.readUnsignedByte(), flags = b.readUnsignedByte(), active = b.readUnsignedByte();
                if (id < 0 || epoch <= 0 || sequence <= 0 || hood > 1 || active > 1
                        || !Mx5MovingParts.valid(flags, active == 1)) throw new DecoderException("Invalid MX5 state");
                return new Mx5State(dimension, id, uuid, epoch, sequence, hood == 1, flags, active == 1);
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
