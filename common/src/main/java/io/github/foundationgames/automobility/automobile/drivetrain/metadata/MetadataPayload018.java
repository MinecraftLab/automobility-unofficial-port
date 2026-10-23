package io.github.foundationgames.automobility.automobile.drivetrain.metadata;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Dedicated S2C channel. No registration as a serverbound payload. */
public record MetadataPayload018(byte[]body)implements CustomPacketPayload{
    public static final Type<MetadataPayload018>TYPE=new Type<>(Identifier.parse("automobility:drivetrain_metadata_v1"));
    public static final StreamCodec<FriendlyByteBuf,MetadataPayload018>CODEC=StreamCodec.of((b,v)->b.writeByteArray(v.body),b->new MetadataPayload018(b.readByteArray(RuntimeMetadata018.MAX_BYTES)));
    public MetadataPayload018{body=body.clone();RuntimeMetadata018.decode(body);}
    @Override public byte[]body(){return body.clone();}
    @Override public Type<? extends CustomPacketPayload>type(){return TYPE;}
}
