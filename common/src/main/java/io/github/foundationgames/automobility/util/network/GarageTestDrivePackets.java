package io.github.foundationgames.automobility.util.network;

import io.github.foundationgames.automobility.entity.GarageTestDriveService;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.Objects;
import java.util.UUID;

/** A return carries only a nonce. The server supplies every identity and destination. */
public final class GarageTestDrivePackets {
    public static final Identifier RETURN_ID=Identifier.parse("automobility:garage_test_drive_return");
    public static final Identifier STATE_ID=Identifier.parse("automobility:garage_test_drive_state");
    private GarageTestDrivePackets() {}
    public record Return(UUID nonce) implements CustomPacketPayload {
        public Return { Objects.requireNonNull(nonce); }
        public static final Type<Return> TYPE=new Type<>(RETURN_ID);
        public static final StreamCodec<FriendlyByteBuf,Return> CODEC=StreamCodec.of((b,p)->b.writeUUID(p.nonce()),GarageTestDrivePackets::decodeReturn);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record State(boolean active,UUID nonce,UUID vehicle,Identifier dimension,GarageTestDriveService.Reason reason) implements CustomPacketPayload {
        public State { Objects.requireNonNull(nonce); Objects.requireNonNull(vehicle); Objects.requireNonNull(dimension); Objects.requireNonNull(reason); }
        public static final Type<State> TYPE=new Type<>(STATE_ID);
        public static final StreamCodec<FriendlyByteBuf,State> CODEC=StreamCodec.of(GarageTestDrivePackets::writeState,GarageTestDrivePackets::decodeState);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static Return decodeReturn(FriendlyByteBuf buffer) {
        if(buffer.readableBytes()!=16) throw new DecoderException("Invalid garage return size");
        return new Return(buffer.readUUID());
    }
    public static FriendlyByteBuf encodeReturn(Return request) { var b=new FriendlyByteBuf(Unpooled.buffer(16)); b.writeUUID(request.nonce()); return b; }
    public static void encodeReturn(FriendlyByteBuf buffer,Return request) { buffer.writeUUID(request.nonce()); }
    private static void writeState(FriendlyByteBuf b,State p) {
        b.writeByte(p.active()?1:0); b.writeUUID(p.nonce()); b.writeUUID(p.vehicle()); b.writeUtf(p.dimension().toString(),128); b.writeByte(p.reason().ordinal());
    }
    public static FriendlyByteBuf encodeState(State state) { var b=new FriendlyByteBuf(Unpooled.buffer()); writeState(b,state); return b; }
    public static State decodeState(FriendlyByteBuf b) {
        if(b.readableBytes()<36||b.readableBytes()>548) throw new DecoderException("Invalid garage state size");
        int active=b.readUnsignedByte(); var nonce=b.readUUID(); var vehicle=b.readUUID(); var dimension=Identifier.tryParse(b.readUtf(128));
        if(b.readableBytes()!=1||active>1||dimension==null) throw new DecoderException("Invalid garage state");
        int reason=b.readUnsignedByte(); var reasons=GarageTestDriveService.Reason.values();
        if(reason>=reasons.length) throw new DecoderException("Invalid garage state reason");
        return new State(active==1,nonce,vehicle,dimension,reasons[reason]);
    }
}
