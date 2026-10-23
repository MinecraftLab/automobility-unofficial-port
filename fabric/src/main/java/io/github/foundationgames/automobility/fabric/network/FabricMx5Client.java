package io.github.foundationgames.automobility.fabric.network;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.fabric.mixin.ClientContentConnectionAccess;
import io.github.foundationgames.automobility.util.network.content.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.resources.Identifier;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Connection/world/incarnation fenced snapshots, with bounded spawn-before-frame deferral. */
public final class FabricMx5Client {
    private static final int MAX_PENDING = 256;
    private static final long TTL = 5_000_000_000L;
    private static Connection owner;
    private static ClientLevel seenLevel;
    private record Key(Identifier dimension, int id, UUID uuid) { }
    private record Pending(Mx5State state, ClientLevel level, long received) { }
    private static final LinkedHashMap<Key, Pending> PENDING = new LinkedHashMap<>();
    private FabricMx5Client() { }
    private static Connection transport(ClientPacketListener handler) {
        return ((ClientContentConnectionAccess)(Object)handler).automobility$getContentConnection();
    }
    private static void clear(Connection connection) {
        if (owner != connection) return;
        PENDING.clear(); seenLevel = null; owner = null;
    }
    private static void observeLevel(Minecraft client) {
        var current = client.level;
        if (current == seenLevel) return;
        seenLevel = current;
        PENDING.entrySet().removeIf(e -> e.getValue().level() != null && e.getValue().level() != current
                || current != null && !e.getKey().dimension().equals(current.dimension().identifier()));
    }
    private static boolean current(Minecraft client, ClientPacketListener handler, Connection connection) {
        return client.getConnection() == handler && connection.isConnected() && connection.getPacketListener() == handler
                && CoreClientContentSession.isCurrent(connection)
                && CoreClientContentSession.supports(CoreContentProtocol.MX5_ANIMATION);
    }
    /** true includes rejected duplicate/identity; false is a short spawn/frame deferral only. */
    private static boolean apply(Minecraft client, Mx5State state) {
        var level = client.level;
        if (level == null) return false;
        if (!state.dimension().equals(level.dimension().identifier())) return true;
        var entity = level.getEntity(state.entityId());
        if (entity == null) return false;
        if (!(entity instanceof AutomobileEntity car) || car.isRemoved() || !car.getUUID().equals(state.uuid())) return true;
        // A custom snapshot may precede vanilla FRAME metadata even for an already tracked ordinary car.
        if (!car.hasOpeningHood()) return false;
        car.applyMx5RemoteState(state.epoch(), state.sequence(), state.hood(), state.flags(), state.active());
        return true;
    }
    private static void receive(Minecraft client, Mx5State state) {
        observeLevel(client);
        if (apply(client, state)) return;
        var key = new Key(state.dimension(), state.entityId(), state.uuid());
        var previous = PENDING.get(key);
        if (previous != null && previous.state().epoch() == state.epoch() && previous.state().sequence() >= state.sequence()) return;
        PENDING.remove(key);
        PENDING.put(key, new Pending(state, client.level, System.nanoTime()));
        while (PENDING.size() > MAX_PENDING) { var it = PENDING.keySet().iterator(); it.next(); it.remove(); }
    }
    private static void flush(Minecraft client) {
        observeLevel(client);
        var handler = client.getConnection();
        if (owner == null || handler == null || transport(handler) != owner || !current(client, handler, owner)) return;
        long now = System.nanoTime(); var it = PENDING.values().iterator();
        while (it.hasNext()) {
            var pending = it.next();
            if (now - pending.received() >= TTL || apply(client, pending.state())) it.remove();
        }
    }
    public static void initialize() {
        ClientConfigurationConnectionEvents.START.register((handler, client) -> {
            var connection = ((ClientContentConnectionAccess)(Object)handler).automobility$getContentConnection();
            client.execute(() -> {
                if (!connection.isConnected() || connection.getPacketListener() != handler) return;
                PENDING.clear(); owner = connection; seenLevel = client.level;
            });
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var connection = transport(handler);
            client.execute(() -> {
                if (!current(client, handler, connection)) return;
                if (owner != connection) PENDING.clear();
                owner = connection; observeLevel(client); flush(client);
            });
        });
        ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) -> {
            var connection = ((ClientContentConnectionAccess)(Object)handler).automobility$getContentConnection();
            client.execute(() -> clear(connection));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            var connection = transport(handler); client.execute(() -> clear(connection));
        });
        ClientPlayNetworking.registerGlobalReceiver(Mx5State.TYPE, (state, context) -> {
            var client = context.client(); var handler = client.getConnection(); if (handler == null) return;
            var connection = transport(handler);
            client.execute(() -> {
                if (!current(client, handler, connection)) return;
                if (owner != connection) { PENDING.clear(); seenLevel = null; owner = connection; }
                receive(client, state);
            });
        });
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            var client = Minecraft.getInstance(); if (client.level == level) flush(client);
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) ->
                PENDING.entrySet().removeIf(e -> e.getKey().dimension().equals(level.dimension().identifier())
                        && e.getKey().id() == entity.getId() && e.getKey().uuid().equals(entity.getUUID())
                        && (e.getValue().level() == null || e.getValue().level() == level)));
        ClientTickEvents.END_CLIENT_TICK.register(FabricMx5Client::flush);
        Mx5MovingPartsSync.clientRequest = action -> {
            var client = Minecraft.getInstance(); var handler = client.getConnection(); if (handler == null) return;
            var connection = transport(handler);
            client.execute(() -> {
                if (!current(client, handler, connection) || !ClientPlayNetworking.canSend(Mx5Action.TYPE)
                        || client.level == null || !action.dimension().equals(client.level.dimension().identifier())) return;
                if (!(client.level.getEntity(action.entityId()) instanceof AutomobileEntity car) || car.isRemoved()
                        || !car.getUUID().equals(action.uuid()) || !car.hasOpeningHood()
                        || !car.mx5State().remoteBound() || car.mx5State().epoch() != action.epoch()) return;
                ClientPlayNetworking.send(action);
            });
        };
    }
}
