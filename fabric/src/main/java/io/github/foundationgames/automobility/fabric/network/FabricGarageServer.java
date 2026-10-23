package io.github.foundationgames.automobility.fabric.network;

import io.github.foundationgames.automobility.fabric.mixin.ServerContentConnectionAccess;
import io.github.foundationgames.automobility.screen.GarageScreenHandler;
import io.github.foundationgames.automobility.entity.GarageTestDriveService;
import io.github.foundationgames.automobility.util.network.GarageTestDrivePackets;
import io.github.foundationgames.automobility.util.network.content.CoreContentProtocol;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** The new menu is sent only to the exact connection that negotiated its codec. */
public final class FabricGarageServer {
    private FabricGarageServer() { }

    public static void initialize() {
        GarageScreenHandler.setClientCompatibility(FabricGarageServer::compatible);
        PayloadTypeRegistry.serverboundPlay().register(GarageTestDrivePackets.Return.TYPE, GarageTestDrivePackets.Return.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GarageTestDrivePackets.State.TYPE, GarageTestDrivePackets.State.CODEC);
        GarageTestDriveService.setStateSender((player, state) -> {
            if (compatible(player) && ServerPlayNetworking.canSend(player, GarageTestDrivePackets.State.TYPE))
                ServerPlayNetworking.send(player, state);
        });
        GarageTestDriveService.setMenuOpener(GarageScreenHandler::openTestDriveReturn);
        ServerPlayNetworking.registerGlobalReceiver(GarageTestDrivePackets.Return.TYPE, (request, context) -> {
            var server = context.server(); var player = context.player(); var handler = player.connection;
            if (handler == null) return;
            var connection = ((ServerContentConnectionAccess)(Object)handler).automobility$getContentConnection();
            server.execute(() -> {
                if (player.connection != handler || connection.getPacketListener() != handler
                        || !connection.isConnected() || server.getPlayerList().getPlayer(player.getUUID()) != player
                        || !player.isAlive() || !compatible(player)) return;
                GarageTestDriveService.returnToGarage(player, request.nonce());
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                server.execute(() -> GarageTestDriveService.disconnect(handler.player)));
        ServerTickEvents.END_SERVER_TICK.register(GarageTestDriveService::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(GarageTestDriveService::stop);
    }

    public static boolean compatible(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        var server = serverPlayer.level().getServer();
        var handler = serverPlayer.connection;
        if (server == null || handler == null || !handler.isAcceptingMessages()) return false;
        var connection = ((ServerContentConnectionAccess)(Object)handler).automobility$getContentConnection();
        return connection.isConnected() && connection.getPacketListener() == handler
                && FabricCoreContentServer.supports(server, connection, CoreContentProtocol.GARAGE_TEST_DRIVE);
    }
}
