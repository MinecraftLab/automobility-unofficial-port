package io.github.foundationgames.automobility.api.drivetrain.profile;

import io.github.foundationgames.automobility.automobile.drivetrain.profile.*;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Metadata and bounded immutable physical definitions only; never inventory, identity mutation, controls or owner energy. */
public final class ProfileDefinitionPayloads015{
    private ProfileDefinitionPayloads015(){}
    private static void reference(FriendlyByteBuf b,ProfileViews.Reference r){b.writeUUID(r.worldSession());b.writeVarLong(r.epoch());b.writeUtf(r.sha256(),64);}
    private static ProfileViews.Reference reference(FriendlyByteBuf b){return new ProfileViews.Reference(b.readUUID(),b.readVarLong(),b.readUtf(64));}
    public record Announce(ProfileClientCache.Offer offer)implements CustomPacketPayload{
        public static final Type<Announce>TYPE=new Type<>(Identifier.parse("automobility:physical_profiles_announce_v1"));
        public static final StreamCodec<FriendlyByteBuf,Announce>CODEC=StreamCodec.of((b,v)->{var o=v.offer;reference(b,new ProfileViews.Reference(o.worldSession(),o.epoch(),o.sha256()));b.writeVarInt(o.byteLength());},b->{var r=reference(b);return new Announce(new ProfileClientCache.Offer(r.worldSession(),r.epoch(),r.sha256(),b.readVarInt()));});
        public Announce{Objects.requireNonNull(offer);}@Override public Type<? extends CustomPacketPayload>type(){return TYPE;}
    }
    public record Request(ProfileViews.Reference reference,int index)implements CustomPacketPayload{
        public static final Type<Request>TYPE=new Type<>(Identifier.parse("automobility:physical_profiles_request_v1"));
        public static final StreamCodec<FriendlyByteBuf,Request>CODEC=StreamCodec.of((b,v)->{ProfileDefinitionPayloads015.reference(b,v.reference);b.writeVarInt(v.index);},b->new Request(ProfileDefinitionPayloads015.reference(b),b.readVarInt()));
        public Request{Objects.requireNonNull(reference);if(index<0||index>=ProfileClientCache.MAX_CHUNKS)throw new IllegalArgumentException("profile chunk index");}@Override public Type<? extends CustomPacketPayload>type(){return TYPE;}
    }
    public record Chunk(ProfileViews.Reference reference,int index,byte[]bytes)implements CustomPacketPayload{
        public static final Type<Chunk>TYPE=new Type<>(Identifier.parse("automobility:physical_profiles_chunk_v1"));
        public static final StreamCodec<FriendlyByteBuf,Chunk>CODEC=StreamCodec.of((b,v)->{ProfileDefinitionPayloads015.reference(b,v.reference);b.writeVarInt(v.index);b.writeByteArray(v.bytes);},b->new Chunk(ProfileDefinitionPayloads015.reference(b),b.readVarInt(),b.readByteArray(ProfileClientCache.CHUNK_BYTES)));
        public Chunk{Objects.requireNonNull(reference);bytes=bytes.clone();if(index<0||index>=ProfileClientCache.MAX_CHUNKS||bytes.length<1||bytes.length>ProfileClientCache.CHUNK_BYTES)throw new IllegalArgumentException("profile chunk bound");}
        @Override public byte[]bytes(){return bytes.clone();}@Override public Type<? extends CustomPacketPayload>type(){return TYPE;}
    }
}
