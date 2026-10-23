package io.github.milkucha.momentum.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Single authoritative path for sending Momentum's live gameplay configuration.
 *
 * <p>Keeping join-time and command-triggered synchronization here prevents the
 * two paths from drifting apart as the protocol evolves.</p>
 */
public final class GameplayConfigSync {
    private GameplayConfigSync() {
    }

    public static boolean sendTo(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, GameplayConfigPacket.TYPE)) {
            return false;
        }
        ServerPlayNetworking.send(player, GameplayConfigPacket.current());
        ServerPhysicsSessions.sendStatus(player);
        return true;
    }

    public static int sendToAll(MinecraftServer server) {
        GameplayConfigPacket packet = GameplayConfigPacket.current();
        int recipients = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, GameplayConfigPacket.TYPE)) {
                ServerPlayNetworking.send(player, packet);
                ServerPhysicsSessions.sendStatus(player);
                recipients++;
            }
        }
        return recipients;
    }
}
