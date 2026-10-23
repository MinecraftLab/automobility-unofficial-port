package io.github.foundationgames.automobility.fabric.network.drivetrain;

import io.github.foundationgames.automobility.automobile.drivetrain.DrivetrainSettingsMessages;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record DrivetrainSettingsPayload(byte[] frame) implements CustomPacketPayload {
    public static final Type<DrivetrainSettingsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("automobility", "drivetrain_preferences_v1"));
    public DrivetrainSettingsPayload {
        if (frame == null || frame.length > DrivetrainSettingsMessages.MAX_BYTES) throw new IllegalArgumentException("settings payload");
        frame = frame.clone();
    }
    @Override public byte[] frame() { return frame.clone(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static final StreamCodec<FriendlyByteBuf, DrivetrainSettingsPayload> CODEC = new StreamCodec<>() {
        @Override public DrivetrainSettingsPayload decode(FriendlyByteBuf buffer) {
            int length = buffer.readVarInt();
            if (length < 0 || length > DrivetrainSettingsMessages.MAX_BYTES || buffer.readableBytes() != length) throw new IllegalArgumentException("settings payload framing");
            byte[] frame = new byte[length]; buffer.readBytes(frame); return new DrivetrainSettingsPayload(frame);
        }
        @Override public void encode(FriendlyByteBuf buffer, DrivetrainSettingsPayload value) { buffer.writeVarInt(value.frame.length); buffer.writeBytes(value.frame); }
    };
}
