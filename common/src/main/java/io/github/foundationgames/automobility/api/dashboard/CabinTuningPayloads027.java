package io.github.foundationgames.automobility.api.dashboard;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Cosmetic metadata and explicit changes only; no mechanical intent, raw NBT or player inventory crosses this channel. */
public final class CabinTuningPayloads027 {
    public enum Action{QUERY,SAVE}
    public enum Result{READY,CHANGED,STALE,DENIED,UNAVAILABLE}
    public record Target(String dimension,UUID car,int entityId){public Target{Objects.requireNonNull(dimension);Objects.requireNonNull(car);if(dimension.length()>128||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||car.equals(new UUID(0,0))||entityId<0)throw new IllegalArgumentException("cabin target");}}
    public record Capabilities(UUID session)implements CustomPacketPayload{
        public static final Type<Capabilities>TYPE=new Type<>(id("cabin_tuning_caps_v1"));
        public static final StreamCodec<FriendlyByteBuf,Capabilities>CODEC=StreamCodec.of((b,v)->b.writeUUID(v.session),b->new Capabilities(b.readUUID()));
        public Capabilities{Objects.requireNonNull(session);}
        @Override public Type<? extends CustomPacketPayload>type(){return TYPE;}
    }
    public record Control(UUID session,long sequence,Target target,UUID incarnation,long revision,Action action,byte[]proposal)implements CustomPacketPayload{
        public static final Type<Control>TYPE=new Type<>(id("cabin_tuning_control_v1"));
        public static final StreamCodec<FriendlyByteBuf,Control>CODEC=StreamCodec.of((b,v)->{b.writeUUID(v.session);b.writeVarLong(v.sequence);writeTarget(b,v.target);b.writeUUID(v.incarnation);b.writeVarLong(v.revision);b.writeVarInt(v.action.ordinal());b.writeByteArray(v.proposal);},b->new Control(b.readUUID(),b.readVarLong(),readTarget(b),b.readUUID(),b.readVarLong(),code(b,Action.values()),b.readByteArray(CabinTuning027.MAX_BYTES)));
        public Control{Objects.requireNonNull(session);Objects.requireNonNull(target);Objects.requireNonNull(incarnation);Objects.requireNonNull(action);proposal=proposal.clone();if(sequence<1||revision<0||proposal.length>CabinTuning027.MAX_BYTES||action==Action.QUERY&&proposal.length!=0)throw new IllegalArgumentException("cabin control");}
        @Override public byte[]proposal(){return proposal.clone();}
        @Override public Type<? extends CustomPacketPayload>type(){return TYPE;}
    }
    public record State(UUID session,long sequence,Target target,UUID incarnation,CabinTuning027.Status status,byte[]settings,boolean mayConfigure,Result result)implements CustomPacketPayload{
        public static final Type<State>TYPE=new Type<>(id("cabin_tuning_state_v1"));
        public static final StreamCodec<FriendlyByteBuf,State>CODEC=StreamCodec.of((b,v)->{b.writeUUID(v.session);b.writeVarLong(v.sequence);writeTarget(b,v.target);b.writeUUID(v.incarnation);b.writeVarInt(v.status.ordinal());b.writeByteArray(v.settings);b.writeBoolean(v.mayConfigure);b.writeVarInt(v.result.ordinal());},b->new State(b.readUUID(),b.readVarLong(),readTarget(b),b.readUUID(),code(b,CabinTuning027.Status.values()),b.readByteArray(CabinTuning027.MAX_BYTES),b.readBoolean(),code(b,Result.values())));
        public State{Objects.requireNonNull(session);Objects.requireNonNull(target);Objects.requireNonNull(incarnation);Objects.requireNonNull(status);Objects.requireNonNull(result);settings=settings.clone();if(sequence<1||settings.length>CabinTuning027.MAX_BYTES||status==CabinTuning027.Status.OPAQUE&&settings.length!=0||status!=CabinTuning027.Status.OPAQUE&&CabinTuning027.read(settings).status()!=CabinTuning027.Status.KNOWN)throw new IllegalArgumentException("cabin state");}
        @Override public byte[]settings(){return settings.clone();}
        public Optional<CabinTuning027.Settings>preferences(){return status==CabinTuning027.Status.OPAQUE?Optional.empty():CabinTuning027.read(settings).settings();}
        @Override public Type<? extends CustomPacketPayload>type(){return TYPE;}
    }
    private static Identifier id(String path){return Identifier.fromNamespaceAndPath("automobility",path);}
    private static void writeTarget(FriendlyByteBuf b,Target t){b.writeUtf(t.dimension,128);b.writeUUID(t.car);b.writeVarInt(t.entityId);}
    private static Target readTarget(FriendlyByteBuf b){return new Target(b.readUtf(128),b.readUUID(),b.readVarInt());}
    private static <T>T code(FriendlyByteBuf b,T[]values){int n=b.readVarInt();if(n<0||n>=values.length)throw new IllegalArgumentException("cabin enumeration");return values[n];}
    private CabinTuningPayloads027(){}
}
