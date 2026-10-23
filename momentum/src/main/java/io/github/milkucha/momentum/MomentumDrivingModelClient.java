package io.github.milkucha.momentum;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Native screen bridge; remote clients can only request the permission-filtered server command. */
public final class MomentumDrivingModelClient {
    private MomentumDrivingModelClient() {}

    public static void reloadSettings() {
        var client = Minecraft.getInstance();
        var server = client.getSingleplayerServer();
        if (server != null) server.execute(() -> {
            MomentumDrivingModel.reload(server, false);
            client.execute(() -> { if (client.getSingleplayerServer() == server && client.player != null)
                client.player.sendOverlayMessage(Component.translatable("message.momentum.config_reloaded")); });
        });
        else {
            io.github.milkucha.momentum.config.MomentumConfig.reloadPreservingModel();
            if (client.player != null) client.player.sendOverlayMessage(Component.translatable("message.momentum.config_reloaded"));
        }
    }

    public static void saveSettings(io.github.milkucha.momentum.config.MomentumConfig config, boolean syncGameplay) {
        editSettings(config, () -> {}, syncGameplay, null);
    }

    /** Validate the captured menu instance before its immediate profile mutation and disk write. */
    public static void editSettings(io.github.milkucha.momentum.config.MomentumConfig config, Runnable edit,
                                    boolean syncGameplay, Runnable afterSave) {
        var client = Minecraft.getInstance();
        var server = client.getSingleplayerServer();
        var originalScreen = client.gui.screen();
        var originalConnection = client.getConnection();
        Runnable save = () -> {
            if (config != io.github.milkucha.momentum.config.MomentumConfig.get()) {
                client.execute(() -> { if (client.player != null)
                    client.player.sendOverlayMessage(Component.translatableWithFallback(
                        "message.momentum.config_stale", "Configuration changed; reopen settings before saving.")); });
                return;
            }
            edit.run();
            if (!io.github.milkucha.momentum.config.MomentumConfig.saveIfCurrent(config)) return;
            if (server != null && syncGameplay)
                io.github.milkucha.momentum.network.GameplayConfigSync.sendToAll(server);
            if (afterSave != null) client.execute(() -> {
                if (client.getSingleplayerServer() == server && client.getConnection() == originalConnection
                        && client.gui.screen() == originalScreen) afterSave.run();
            });
        };
        if (server != null) server.execute(save); else save.run();
    }

    public static boolean canRequest() {
        var client = Minecraft.getInstance();
        if (client.getSingleplayerServer() != null) return true;
        if (client.player == null) return false;
        var command = client.player.connection.getCommands().getRoot().getChild("momentum");
        return command != null && command.getChild("driving") != null;
    }

    public static void request(boolean extended) {
        var client = Minecraft.getInstance();
        var server = client.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                boolean changed = MomentumDrivingModel.change(server, extended);
                client.execute(() -> {
                    if (client.getSingleplayerServer() == server && client.player != null)
                        client.player.sendSystemMessage(Component.translatableWithFallback(
                            changed ? "command.momentum.driving.success" : "command.momentum.driving.moving",
                            changed ? "Server driving model: %s" : "Park all loaded cars before changing the driving model.",
                            Component.translatableWithFallback("vehicle.automobility.driving.model."
                                    + (extended ? "extended" : "original"), extended ? "Extended" : "Original")));
                });
            });
        } else if (canRequest()) {
            client.player.connection.sendCommand("momentum driving " + (extended ? "extended" : "original"));
        }
    }
}
