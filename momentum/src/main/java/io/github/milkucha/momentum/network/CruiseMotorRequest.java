package io.github.milkucha.momentum.network;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record CruiseMotorRequest(int revision,long nonce,long sequence,int operation,int entityId,UUID entityUuid,float targetKmh) implements CustomPacketPayload {
    public static final int CANCEL=0,SET=1,RENEW=2,MANUAL_FORWARD=3;
    public static final Type<CruiseMotorRequest> TYPE=new Type<>(Identifier.fromNamespaceAndPath("momentum","cruise_motor_request_v1"));
    public static final StreamCodec<FriendlyByteBuf,CruiseMotorRequest> CODEC=new StreamCodec<>() {
        public CruiseMotorRequest decode(FriendlyByteBuf b){return new CruiseMotorRequest(b.readVarInt(),b.readLong(),b.readLong(),b.readUnsignedByte(),b.readVarInt(),b.readUUID(),b.readFloat());}
        public void encode(FriendlyByteBuf b,CruiseMotorRequest p){b.writeVarInt(p.revision);b.writeLong(p.nonce);b.writeLong(p.sequence);b.writeByte(p.operation);b.writeVarInt(p.entityId);b.writeUUID(p.entityUuid);b.writeFloat(p.targetKmh);}
    };
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
