package io.github.foundationgames.automobility.fabric.network;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.fabric.mixin.ServerContentConnectionAccess;
import io.github.foundationgames.automobility.util.network.Mx5ActionLimiter;
import io.github.foundationgames.automobility.util.network.content.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Authoritative play-channel state; no new tracked metadata IDs or movement fields. */
public final class FabricMx5Server {
    private static final Map<MinecraftServer, IdentityHashMap<Connection, Set<AutomobileEntity>>> TRACKED =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private FabricMx5Server() { }
    private static Connection transport(ServerPlayer player) {
        return ((ServerContentConnectionAccess)(Object)player.connection).automobility$getContentConnection();
    }
    public static boolean compatible(ServerPlayer player) {
        var server = player.level().getServer();
        return server != null && player.connection != null && player.connection.isAcceptingMessages()
                && transport(player).getPacketListener() == player.connection && transport(player).isConnected()
                && FabricCoreContentServer.supports(server, transport(player), CoreContentProtocol.MX5_ANIMATION)
                && ServerPlayNetworking.canSend(player, Mx5State.TYPE);
    }
    private static void send(AutomobileEntity car, ServerPlayer player) {
        if (car.isRemoved() || !car.hasOpeningHood() || car.level() != player.level() || !compatible(player)) return;
        var state = car.mx5State().snapshot();
        ServerPlayNetworking.send(player, new Mx5State(car.level().dimension().identifier(), car.getId(),
                car.getUUID(), state.epoch(), state.sequence(), state.hood(), state.flags(), state.active()));
    }
    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(Mx5Action.TYPE, Mx5Action.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Mx5State.TYPE, Mx5State.CODEC);
        Mx5MovingPartsSync.peerCompatible = FabricMx5Server::compatible;
        Mx5MovingPartsSync.serverChanged = FabricMx5Server::changed;
        ServerPlayNetworking.registerGlobalReceiver(Mx5Action.TYPE, (action, context) -> {
            var server = context.server(); var player = context.player(); var handler = player.connection;
            if (handler == null) return;
            var connection = transport(player);
            server.execute(() -> {
                if (player.connection != handler || connection.getPacketListener() != handler
                        || !connection.isConnected() || server.getPlayerList().getPlayer(player.getUUID()) != player
                        || !player.isAlive() || !compatible(player)
                        || !Mx5ActionLimiter.tryAcquire(server, player.getUUID(), server.getTickCount())
                        || !action.dimension().equals(player.level().dimension().identifier())) return;
                if (!(player.level().getEntity(action.entityId()) instanceof AutomobileEntity car)
                        || !car.isAlive() || car.isRemoved() || car.level() != player.level() || !car.hasOpeningHood()
                        || !car.getUUID().equals(action.uuid()) || car.mx5State().epoch() != action.epoch()) return;
                if (action.action() == Mx5Action.HOOD && player.distanceToSqr(car) <= 64
                        && car.getVehicleAccess().mayConfigure(player.getUUID())) car.requestHoodState(action.value());
                else if (action.action() == Mx5Action.DANCE && !action.value() && car.isDriving(player))
                    car.requestHeadlightDance(player);
            });
        });
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof AutomobileEntity car) { remember(car, player); send(car, player); }
        });
        EntityTrackingEvents.STOP_TRACKING.register((entity, player) -> {
            if (!(entity instanceof AutomobileEntity car) || player.connection == null) return;
            var byConnection = TRACKED.get(player.level().getServer());
            var cars = byConnection == null ? null : byConnection.get(transport(player));
            if (cars != null) cars.remove(car);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> replay(handler.player));
        ClientboundPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
            if (!channels.contains(Mx5State.TYPE.id())) return;
            var player = handler.player; var connection = transport(player);
            server.execute(() -> {
                if (player.connection == handler && connection.getPacketListener() == handler
                        && connection.isConnected()) replay(player);
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var connection = ((ServerContentConnectionAccess)(Object)handler).automobility$getContentConnection();
            server.execute(() -> forget(server, connection));
        });
        ServerConfigurationConnectionEvents.DISCONNECT.register((handler, server) -> {
            var connection = ((ServerContentConnectionAccess)(Object)handler).automobility$getContentConnection();
            server.execute(() -> forget(server, connection));
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof AutomobileEntity car) {
                var byConnection = TRACKED.get(level.getServer());
                if (byConnection != null) byConnection.values().forEach(cars -> cars.remove(car));
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { TRACKED.remove(server); Mx5ActionLimiter.clear(server); });
    }
    private static void remember(AutomobileEntity car, ServerPlayer player) {
        if (player.connection == null) return;
        var byConnection = TRACKED.computeIfAbsent(player.level().getServer(), ignored -> new IdentityHashMap<>());
        if (!car.hasOpeningHood() || car.isRemoved()) {
            var cars = byConnection.get(transport(player)); if (cars != null) cars.remove(car); return;
        }
        byConnection.computeIfAbsent(transport(player), ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(car);
    }
    private static void forget(MinecraftServer server, Connection connection) {
        var byConnection = TRACKED.get(server); if (byConnection != null) byConnection.remove(connection);
    }
    private static void replay(ServerPlayer player) {
        if (!compatible(player)) return;
        var byConnection = TRACKED.get(player.level().getServer());
        var cars = byConnection == null ? null : byConnection.get(transport(player));
        if (cars == null) return;
        cars.removeIf(car -> car.isRemoved() || !car.hasOpeningHood() || car.level() != player.level()
                || !PlayerLookup.tracking(car).contains(player));
        cars.forEach(car -> send(car, player));
    }
    public static void changed(AutomobileEntity car) {
        if (!(car.level() instanceof ServerLevel)) return;
        var players = PlayerLookup.tracking(car);
        players.forEach(player -> { remember(car, player); send(car, player); });
        if (car.getFirstPassenger() instanceof ServerPlayer driver && car.isDriving(driver) && !players.contains(driver)) send(car, driver);
    }
}
