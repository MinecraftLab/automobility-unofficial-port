package io.github.foundationgames.automobility.fabric.network;

import io.github.foundationgames.automobility.fabric.mixin.ClientContentConnectionAccess;
import io.github.foundationgames.automobility.item.NewVehicleContent;
import io.github.foundationgames.automobility.util.network.content.CoreClientContentSession;
import io.github.foundationgames.automobility.util.network.content.CoreContentChallenge;
import io.github.foundationgames.automobility.util.network.content.CoreContentProtocol;
import io.github.foundationgames.automobility.util.network.content.CoreContentReply;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;

/** New client -> old server is a permitted legacy session, never a blind disconnect. */
public final class FabricCoreContentClient {
    private FabricCoreContentClient() { }
    private static Connection connection(ClientCommonPacketListenerImpl listener) {
        return ((ClientContentConnectionAccess)(Object)listener).automobility$getContentConnection();
    }

    public static void initialize() {
        NewVehicleContent.setClientAvailability(() -> CoreClientContentSession.supports(CoreContentProtocol.TRIO_CONTENT));
        ClientConfigurationConnectionEvents.START.register((listener, client) -> CoreClientContentSession.begin(connection(listener)));
        ClientConfigurationConnectionEvents.COMPLETE.register((listener, client) -> CoreClientContentSession.complete(connection(listener)));
        ClientConfigurationConnectionEvents.DISCONNECT.register((listener, client) -> {
            var connection = connection(listener);
            client.execute(() -> CoreClientContentSession.clear(connection));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
            var connection = connection(listener);
            client.execute(() -> CoreClientContentSession.clear(connection));
        });
        ClientConfigurationNetworking.registerGlobalReceiver(CoreContentChallenge.TYPE, (challenge, context) -> {
            var listener = context.packetListener();
            var connection = connection(listener);
            var sender = context.responseSender();
            context.client().execute(() -> {
                if (!CoreClientContentSession.isCurrent(connection) || !connection.isConnected()
                        || connection.getPacketListener() != listener) return;
                if (!CoreContentProtocol.accepts(challenge.revision(), challenge.nonce(),
                        CoreContentProtocol.SUPPORTED, challenge.required())) {
                    CoreClientContentSession.clear(connection);
                    sender.disconnect(Component.literal("This server requires newer Automobility vehicle content. Please update Automobility on your client."));
                    return;
                }
                if (CoreClientContentSession.accepts(connection, challenge)) {
                    sender.sendPacket(new CoreContentReply(CoreContentProtocol.REVISION, challenge.nonce(), CoreContentProtocol.SUPPORTED));
                    CoreClientContentSession.sentReply(connection, challenge);
                }
            });
        });
    }
}
