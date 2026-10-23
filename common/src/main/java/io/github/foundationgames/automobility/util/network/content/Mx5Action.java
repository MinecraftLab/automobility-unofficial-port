package io.github.foundationgames.automobility.util.network.content;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

/** Client intent only: no client-provided animation phase or permission. */
public record Mx5Action(Identifier dimension, int entityId, UUID uuid, long epoch, int action, boolean value)
        implements CustomPacketPayload {
    public static final int HOOD = 1, DANCE = 2;
    public static final Type<Mx5Action> TYPE = new Type<>(Identifier.parse("automobility:mx5_action_v1"));
    public static final StreamCodec<FriendlyByteBuf, Mx5Action> CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeUtf(p.dimension.toString(), 128); b.writeInt(p.entityId); b.writeUUID(p.uuid);
                b.writeLong(p.epoch); b.writeByte(p.action); b.writeByte(p.value ? 1 : 0);
            }, b -> {
                var dimension = Identifier.tryParse(b.readUtf(128));
                if (dimension == null || b.readableBytes() != 30) throw new DecoderException("Invalid MX5 action");
                int id = b.readInt(); var uuid = b.readUUID(); long epoch = b.readLong();
                int action = b.readUnsignedByte(), value = b.readUnsignedByte();
                if (id < 0 || epoch <= 0 || (action != HOOD && action != DANCE) || value > 1
                        || action == DANCE && value != 0) throw new DecoderException("Invalid MX5 intent");
                return new Mx5Action(dimension, id, uuid, epoch, action, value == 1);
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
