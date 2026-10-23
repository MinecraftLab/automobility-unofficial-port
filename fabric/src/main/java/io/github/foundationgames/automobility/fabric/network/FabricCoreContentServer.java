package io.github.foundationgames.automobility.fabric.network;

import io.github.foundationgames.automobility.fabric.mixin.ServerContentConnectionAccess;
import io.github.foundationgames.automobility.util.network.content.CoreContentChallenge;
import io.github.foundationgames.automobility.util.network.content.CoreContentProtocol;
import io.github.foundationgames.automobility.util.network.content.CoreContentReply;
import io.github.foundationgames.automobility.util.network.content.PendingCoreContentGate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationPacketListenerImpl;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import java.util.IdentityHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Configuration barrier; no entity metadata slots or Momentum physics revisions change. */
public final class FabricCoreContentServer {
    private static final ConfigurationTask.Type TASK = new ConfigurationTask.Type("automobility:content_contract_v1");
    private static final int MAX_PENDING = 1024, MAX_CONNECTIONS = 10000;
    private static final Map<MinecraftServer, ServerState> SERVERS = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Component UPDATE = Component.literal(
            "This server needs a compatible Automobility Unofficial Port client (new vehicle content). Please update Automobility on your client.");
    private static final Component TIMEOUT = Component.literal("Automobility vehicle-content negotiation timed out. Please reconnect with a compatible client.");

    private FabricCoreContentServer() { }

    public static void initialize() {
        PayloadTypeRegistry.clientboundConfiguration().register(CoreContentChallenge.TYPE, CoreContentChallenge.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(CoreContentReply.TYPE, CoreContentReply.CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register(FabricCoreContentServer::configure);
        ServerConfigurationNetworking.registerGlobalReceiver(CoreContentReply.TYPE, (reply, context) -> {
            var server = context.server();
            var handler = context.packetListener();
            server.execute(() -> receive(server, handler, reply));
        });
        ServerConfigurationConnectionEvents.DISCONNECT.register((handler, server) -> {
            var connection = connection(handler);
            server.execute(() -> remove(server, connection));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var connection = ((ServerContentConnectionAccess)(Object)handler).automobility$getContentConnection();
            server.execute(() -> remove(server, connection));
        });
        ServerTickEvents.END_SERVER_TICK.register(FabricCoreContentServer::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(SERVERS::remove);
    }

    private static Connection connection(ServerConfigurationPacketListenerImpl handler) {
        return ((ServerContentConnectionAccess)(Object)handler).automobility$getContentConnection();
    }

    private static void configure(ServerConfigurationPacketListenerImpl handler, MinecraftServer server) {
        var connection = connection(handler);
        var state = SERVERS.computeIfAbsent(server, ignored -> new ServerState());
        // A reconfiguration on the same transport must negotiate again; previous success is not enough.
        state.pending.remove(connection);
        state.accepted.remove(connection);
        if (!ServerConfigurationNetworking.canSend(handler, CoreContentChallenge.TYPE)) {
            handler.disconnect(UPDATE);
            return;
        }
        if (state.pending.size() >= MAX_PENDING || state.pending.size() + state.accepted.size() >= MAX_CONNECTIONS) {
            handler.disconnect(Component.literal("Too many pending Automobility connections. Please try again later."));
            return;
        }
        var pending = new Pending(handler, connection);
        state.pending.put(connection, pending);
        ((FabricServerConfigurationPacketListenerImpl)(Object)handler).addTask(new ConfigurationTask() {
            @Override public void start(Consumer<Packet<?>> sender) {
                if (!current(state, pending)) return;
                long nonce;
                do { nonce = ThreadLocalRandom.current().nextLong(); } while (nonce == 0);
                pending.gate = new PendingCoreContentGate(nonce, CoreContentProtocol.REQUIRED, System.nanoTime());
                sender.accept(ServerConfigurationNetworking.createClientboundPacket(pending.gate.challenge()));
            }
            @Override public Type type() { return TASK; }
            // Default tick() stays false. Timeout disconnects; it never completes the barrier.
        });
    }

    private static boolean current(ServerState state, Pending pending) {
        return state.pending.get(pending.connection) == pending && pending.connection.isConnected()
                && pending.connection.getPacketListener() == pending.handler;
    }

    private static void receive(MinecraftServer server, ServerConfigurationPacketListenerImpl handler, CoreContentReply reply) {
        var state = SERVERS.get(server);
        if (state == null) return;
        var connection = connection(handler);
        var pending = state.pending.get(connection);
        if (pending == null || pending.handler != handler || pending.gate == null || !current(state, pending)) return;
        switch (pending.gate.reply(reply.revision(), reply.nonce(), reply.supported(), System.nanoTime())) {
            case ACCEPTED -> {
                state.pending.remove(connection);
                state.accepted.put(connection, reply.supported() & CoreContentProtocol.SUPPORTED);
                ((FabricServerConfigurationPacketListenerImpl)(Object)handler).completeTask(TASK);
            }
            case INCOMPATIBLE, EXPIRED -> {
                state.pending.remove(connection);
                handler.disconnect(UPDATE);
            }
            case STALE, CLOSED -> { } // Cannot acknowledge or complete any other task.
        }
    }

    private static void tick(MinecraftServer server) {
        var state = SERVERS.get(server);
        if (state == null || state.pending.isEmpty()) return;
        long now = System.nanoTime();
        var entries = state.pending.values().iterator();
        while (entries.hasNext()) {
            var pending = entries.next();
            if (!current(state, pending)) { entries.remove(); continue; }
            if (pending.gate != null && pending.gate.expired(now)) {
                entries.remove();
                pending.handler.disconnect(TIMEOUT);
            }
        }
    }

    private static void remove(MinecraftServer server, Connection connection) {
        var state = SERVERS.get(server);
        if (state != null) { state.pending.remove(connection); state.accepted.remove(connection); }
    }

    /** Must be queried on the owning server thread, just like entity action validation. */
    public static boolean supports(MinecraftServer server, Connection connection, int required) {
        var state = SERVERS.get(server);
        int offered = state == null ? 0 : state.accepted.getOrDefault(connection, 0);
        return required > 0 && connection.isConnected() && (offered & required) == required;
    }

    private static final class ServerState {
        final IdentityHashMap<Connection, Pending> pending = new IdentityHashMap<>();
        final IdentityHashMap<Connection, Integer> accepted = new IdentityHashMap<>();
    }
    private static final class Pending {
        final ServerConfigurationPacketListenerImpl handler;
        final Connection connection;
        PendingCoreContentGate gate;
        Pending(ServerConfigurationPacketListenerImpl handler, Connection connection) {
            this.handler = handler; this.connection = connection;
        }
    }
}
