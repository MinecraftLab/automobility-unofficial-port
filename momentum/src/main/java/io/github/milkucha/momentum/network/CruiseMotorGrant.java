package io.github.milkucha.momentum.network;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record CruiseMotorGrant(long nonce,long sequence,int entityId,UUID entityUuid,UUID driverUuid,boolean accepted,float targetKmh,int durationMs) implements CustomPacketPayload {
    public static final Type<CruiseMotorGrant> TYPE=new Type<>(Identifier.fromNamespaceAndPath("momentum","cruise_motor_grant_v1"));
    public static final StreamCodec<FriendlyByteBuf,CruiseMotorGrant> CODEC=new StreamCodec<>() {
        public CruiseMotorGrant decode(FriendlyByteBuf b){return new CruiseMotorGrant(b.readLong(),b.readLong(),b.readVarInt(),b.readUUID(),b.readUUID(),b.readBoolean(),b.readFloat(),b.readVarInt());}
        public void encode(FriendlyByteBuf b,CruiseMotorGrant p){b.writeLong(p.nonce);b.writeLong(p.sequence);b.writeVarInt(p.entityId);b.writeUUID(p.entityUuid);b.writeUUID(p.driverUuid);b.writeBoolean(p.accepted);b.writeFloat(p.targetKmh);b.writeVarInt(p.durationMs);}
    };
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
