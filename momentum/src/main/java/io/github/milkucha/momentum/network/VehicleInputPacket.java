package io.github.milkucha.momentum.network;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.platform.Platform;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record VehicleInputPacket(int entityId, boolean brake, boolean drift) implements CustomPacketPayload {
    public static final Type<VehicleInputPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("momentum", "vehicle_input_v1"));

    public static final StreamCodec<FriendlyByteBuf, VehicleInputPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VehicleInputPacket::entityId,
            ByteBufCodecs.BOOL, VehicleInputPacket::brake,
            ByteBufCodecs.BOOL, VehicleInputPacket::drift,
            VehicleInputPacket::new
    );

    public static void sendToTracking(AutomobileEntity automobile, boolean brake, boolean drift) {
        var packet = new VehicleInputPacket(automobile.getId(), brake, drift);
        Platform.get().forEachTrackingPlayer(automobile, player -> {
            if (!automobile.isDriving(player) && ServerPlayNetworking.canSend(player, TYPE)) {
                ServerPlayNetworking.send(player, packet);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
