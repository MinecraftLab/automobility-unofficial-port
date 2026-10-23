package io.github.milkucha.momentum.network;

import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.milkucha.momentum.mixin.ServerConnectionAccessor;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-connection declarations accepted by the server. All access occurs on the
 * server thread. A matching declaration is a compatibility agreement, not an
 * attestation that a client is unmodified or obeys the configured physics.
 */
public final class ServerPhysicsSessions {
    private static final int WARNING_INTERVAL_TICKS = 40;
    private record Session(int revision, long nonce) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Set<UUID> VERIFIED = new HashSet<>();
    private static final Map<UUID, Integer> LAST_WARNINGS = new HashMap<>();

    private ServerPhysicsSessions() {
    }

    public static void reset(ServerPlayer player) {
        remove(player.getUUID());
    }

    public static void remove(UUID playerId) {
        SESSIONS.remove(playerId);
        VERIFIED.remove(playerId);
        LAST_WARNINGS.remove(playerId);
        ServerKeyState.remove(playerId);
    }

    public static void clearAll() {
        for (UUID playerId : SESSIONS.keySet()) {
            ServerKeyState.remove(playerId);
        }
        for (UUID playerId : LAST_WARNINGS.keySet()) {
            ServerKeyState.remove(playerId);
        }
        SESSIONS.clear();
        VERIFIED.clear();
        LAST_WARNINGS.clear();
    }

    /** Optional assistance always requires the agreement, also in Original. */
    public static boolean mayUseCruiseMotor(ServerPlayer player) {
        return player.connection != null && (((ServerConnectionAccessor) player.connection)
                .momentum$getConnection().isMemoryConnection() || VERIFIED.contains(player.getUUID()));
    }

    public static boolean canDrive(ServerPlayer player) {
        return drivingAllowed(MomentumConfig.get().enabled,
                player.connection != null
                        && ((ServerConnectionAccessor) player.connection).momentum$getConnection().isMemoryConnection(),
                VERIFIED.contains(player.getUUID()));
    }

    static boolean drivingAllowed(boolean enabled, boolean memoryConnection, boolean verified) {
        return !enabled || memoryConnection || verified;
    }

    public static boolean accept(ServerPlayer player, int revision, long nonce) {
        SESSIONS.put(player.getUUID(), new Session(revision, nonce));
        boolean accepted = PhysicsContract.accepts(revision, nonce,
                ServerPlayNetworking.canSend(player, PhysicsStatusPacket.TYPE),
                ServerPlayNetworking.canSend(player, GameplayConfigPacket.TYPE));
        if (accepted) {
            VERIFIED.add(player.getUUID());
            LAST_WARNINGS.remove(player.getUUID());
        } else {
            VERIFIED.remove(player.getUUID());
            ServerKeyState.remove(player.getUUID());
        }
        GameplayConfigSync.sendTo(player);
        return accepted;
    }

    public static void sendStatus(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, PhysicsStatusPacket.TYPE)) {
            Session session = SESSIONS.get(player.getUUID());
            ServerPlayNetworking.send(player, new PhysicsStatusPacket(
                    PhysicsContract.REVISION, session == null ? 0 : session.nonce(),
                    VERIFIED.contains(player.getUUID())));
        }
    }

    public static void warn(ServerPlayer player) {
        // A departing old actor may remain in its vehicle until the next ordinary
        // tick. Never recreate its warning entry or touch a replacement actor's state.
        if (player.level().getServer().getPlayerList().getPlayer(player.getUUID()) != player) {
            return;
        }
        // Vanilla teardown can still list this actor after its connection closes.
        if (player.connection == null || !player.connection.isAcceptingMessages()) {
            return;
        }
        int now = player.level().getServer().getTickCount();
        Integer last = LAST_WARNINGS.get(player.getUUID());
        if (last != null && now >= last && (long) now - last < WARNING_INTERVAL_TICKS) {
            return;
        }
        LAST_WARNINGS.put(player.getUUID(), now);
        player.sendSystemMessage(Component.translatableWithFallback(
                "momentum.compatibility.driver_required",
                "Driving here requires a compatible Momentum client."), true);
    }
}
