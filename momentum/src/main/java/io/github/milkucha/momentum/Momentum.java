package io.github.milkucha.momentum;

import io.github.milkucha.momentum.command.MomentumCommands;
import io.github.milkucha.momentum.network.KeyStatePacket;
import io.github.milkucha.momentum.network.GameplayConfigPacket;
import io.github.milkucha.momentum.network.GameplayConfigSync;
import io.github.milkucha.momentum.network.VehicleInputPacket;
import io.github.milkucha.momentum.network.ServerKeyState;
import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (both-sides) initializer for Momentum.
 *
 * Registers the C2S key-state packet receiver so the server knows which
 * Momentum keys each player is holding, enabling correct movement on
 * dedicated multiplayer servers.
 *
 * Also cleans up ServerKeyState when a player disconnects to avoid stale
 * entries causing phantom braking or drifting on respawn.
 */
public class Momentum implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Momentum");

    @Override
    public void onInitialize() {
        // Register the payload type for the C2S key-state packet.
        PayloadTypeRegistry.serverboundPlay().register(KeyStatePacket.TYPE, KeyStatePacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GameplayConfigPacket.TYPE, GameplayConfigPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(VehicleInputPacket.TYPE, VehicleInputPacket.CODEC);
        MomentumCommands.register();

        // Observe every accepted brake edge on the server thread. Sampling only once
        // per vehicle tick would lose a release followed by a press in that same tick.
        ServerPlayNetworking.registerGlobalReceiver(KeyStatePacket.TYPE, (payload, context) ->
            context.server().execute(() -> {
                var player = context.player();
                if (context.server().getPlayerList().getPlayer(player.getUUID()) != player) return;
                ServerKeyState.set(player.getUUID(), payload.brake(), payload.drift());
                if (player.getVehicle() instanceof AutomobileEntity automobile && !automobile.isRemoved()
                        && automobile.level() == player.level() && automobile.isDriving(player)) {
                    var config = MomentumConfig.gameplay();
                    automobile.setAdditionalBrakeInput(
                            config.enabled && config.movement.enabled && payload.brake());
                }
            }));

        // Clear state on disconnect so a reconnecting player starts clean.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            ServerKeyState.remove(handler.player.getUUID()));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                GameplayConfigSync.sendTo(handler.player));
    }
}
