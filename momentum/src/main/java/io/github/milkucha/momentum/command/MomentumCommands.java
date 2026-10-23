package io.github.milkucha.momentum.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.milkucha.momentum.MomentumDrivingModel;
import io.github.milkucha.momentum.network.GameplayConfigSync;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Runtime-safe server administration for Momentum's authoritative gameplay settings. */
public final class MomentumCommands {
    private MomentumCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            dispatcher.register(Commands.literal("momentum")
                    .executes(MomentumCommands::status)
                    .then(Commands.literal("status")
                            .executes(MomentumCommands::status))
                    .then(Commands.literal("reload")
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .executes(MomentumCommands::reload))
                    .then(Commands.literal("sync")
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .executes(MomentumCommands::sync))
                    .then(Commands.literal("driving")
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .then(Commands.literal("original").executes(c -> driving(c, false)))
                            .then(Commands.literal("extended").executes(c -> driving(c, true))))
                    .then(Commands.literal("preset")
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .then(preset("touring", MomentumConfig.DrivingPreset.TOURING))
                            .then(preset("balanced", MomentumConfig.DrivingPreset.BALANCED))
                            .then(preset("sport", MomentumConfig.DrivingPreset.SPORT))));
        });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> preset(
            String literal,
            MomentumConfig.DrivingPreset preset) {
        return Commands.literal(literal).executes(context -> applyPreset(context, preset));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        MomentumConfig config = MomentumConfig.get();
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.momentum.status",
                toggle(config.enabled),
                Component.translatable(presetKey(config.drivingPreset)),
                toggle(config.cruise.enabled)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        var result = MomentumDrivingModel.reload(context.getSource().getServer(), true);
        int recipients = result.recipients();
        if (result.modelChangeBlocked())
            context.getSource().sendFailure(Component.translatableWithFallback(
                    "command.momentum.reload.model_blocked",
                    "Other settings reloaded; driving model unchanged. Park all loaded cars before changing it."));
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.momentum.reload.success", recipients), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int sync(CommandContext<CommandSourceStack> context) {
        int recipients = GameplayConfigSync.sendToAll(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.momentum.sync.success", recipients), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int driving(CommandContext<CommandSourceStack> context, boolean extended) {
        if (!MomentumDrivingModel.change(context.getSource().getServer(), extended)) {
            context.getSource().sendFailure(Component.translatableWithFallback(
                    "command.momentum.driving.moving", "Park all loaded cars before changing the driving model."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatableWithFallback(
                "command.momentum.driving.success", "Server driving model: %s",
                Component.translatableWithFallback("vehicle.automobility.driving.model."
                        + (extended ? "extended" : "original"), extended ? "Extended" : "Original")), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int applyPreset(CommandContext<CommandSourceStack> context,
                                   MomentumConfig.DrivingPreset preset) {
        MomentumConfig config = MomentumConfig.get();
        config.applyDrivingPreset(preset);
        config.save();
        int recipients = GameplayConfigSync.sendToAll(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.momentum.preset.success",
                Component.translatable(presetKey(preset)),
                recipients), false);
        return Command.SINGLE_SUCCESS;
    }

    private static Component toggle(boolean enabled) {
        return Component.translatable(enabled
                ? "config.momentum.value.enabled"
                : "config.momentum.value.disabled");
    }

    private static String presetKey(MomentumConfig.DrivingPreset preset) {
        return "config.momentum.driving_preset."
                + preset.name().toLowerCase(Locale.ROOT);
    }
}
