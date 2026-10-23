package io.github.milkucha.momentum.network;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Mutual Fabric channel declaration for the exact gradual-reverse rule, independent of
 * PhysicsContract4 and existing86B codecs. No packet is sent and no gameplay state is authored.
 * An older endpoint lacks the receiver, so each new endpoint retains the old reverse rule.
 */
public record ReverseDriveCapability() implements CustomPacketPayload {
    public static final ReverseDriveCapability INSTANCE = new ReverseDriveCapability();
    public static final Type<ReverseDriveCapability> TYPE = new Type<>(Identifier.fromNamespaceAndPath("momentum", "reverse_acceleration_v1"));
    public static final StreamCodec<FriendlyByteBuf, ReverseDriveCapability> CODEC = StreamCodec.of((buffer, value) -> {}, buffer -> INSTANCE);
    private static Predicate<AutomobileEntity> client = car -> false;
    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {});
    }
    public static void installClient(Predicate<AutomobileEntity> predicate) {client = predicate;}
    public static boolean negotiated(AutomobileEntity car) {
        if (car == null || car.isRemoved()) return false;
        if (car.level().isClientSide()) return client.test(car);
        return car.getControllingPassenger() instanceof ServerPlayer actor && actor.isAlive()
                && actor.getVehicle() == car && actor.level() == car.level() && car.isDriving(actor)
                && actor.connection != null && actor.connection.isAcceptingMessages()
                && actor.level().getServer().getPlayerList().getPlayer(actor.getUUID()) == actor
                && ServerPlayNetworking.canSend(actor, TYPE);
    }
    @Override public Type<? extends CustomPacketPayload> type() {return TYPE;}
}
