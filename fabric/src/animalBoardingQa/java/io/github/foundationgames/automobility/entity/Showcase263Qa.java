package io.github.foundationgames.automobility.entity;

import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import io.github.foundationgames.automobility.automobile.AutomobileWheel;
import io.github.foundationgames.automobility.block.AutomobilityBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Opt-in disposable manual-QA stage. Not an automated client test or production feature. */
final class Showcase263Qa {
    private static final String PLAYER = "Auto263QA";
    private static final BlockPos MARKER = new BlockPos(0, 198, 0);

    static void initialize() {
        Set<UUID> pendingPlayers = new LinkedHashSet<>();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                if (!FabricLoader.getInstance().isModLoaded("momentum")
                        || !FabricLoader.getInstance().isModLoaded("riautomobility")) {
                    throw new IllegalStateException("The 26.3 showcase requires real Momentum and RIAutomobility JARs");
                }
                var level = server.overworld();
                // Load the entire platform, including its persistent setup marker, before inspecting it.
                for (int x = -2; x <= 1; x++) {
                    for (int z = -1; z <= 2; z++) level.getChunk(x, z);
                }
                if (!level.getBlockState(MARKER).is(Blocks.GOLD_BLOCK)) {
                    prepareStage(level);
                    level.setBlockAndUpdate(MARKER, Blocks.GOLD_BLOCK.defaultBlockState());
                }
                // Vanilla reports already-at-target as a command error on a resumed QA world.
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set day");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
                command(server, "setworldspawn 0 200 20");
                LoggerFactory.getLogger("Showcase263Qa").info(
                        "AUTO263_SHOWCASE_READY manual QA only: player={} view=(0.5,200,20.5) cars z=5.5 x=-12,-6,0,6,12",
                        PLAYER);
            } catch (Throwable failure) {
                LoggerFactory.getLogger("Showcase263Qa").error("AUTO263_SHOWCASE_FAILED", failure);
                server.halt(false);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!handler.getPlayer().getGameProfile().name().equals(PLAYER)) return;
            // JOIN fires before 26.3 has inserted the player into PlayerList. Commands cannot
            // resolve the name yet; process once at tick end after the real player is visible.
            pendingPlayers.add(handler.getPlayer().getUUID());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                pendingPlayers.remove(handler.getPlayer().getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> pendingPlayers.clear());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var pending = pendingPlayers.iterator();
            while (pending.hasNext()) {
                var player = server.getPlayerList().getPlayer(pending.next());
                if (player == null) continue;
                pending.remove();
                try {
                    // Already creative is a valid state, not a failed gamemode command.
                    player.setGameMode(GameType.CREATIVE);
                    command(server, "tp " + PLAYER + " 0.5 200 20.5 180 12");
                    // Persist the tag only after every supply command succeeds.
                    if (!player.entityTags().contains("automobility_showcase263_supplied")) {
                        for (String item : new String[]{"automobility:crowbar", "automobility:auto_mechanic_table",
                                "automobility:automobile_assembler", "minecraft:iron_ingot 64",
                                "minecraft:oak_planks 64", "minecraft:redstone 64", "minecraft:diamond 64"}) {
                            command(server, "give " + PLAYER + " " + item);
                        }
                        player.addTag("automobility_showcase263_supplied");
                    }
                    player.sendSystemMessage(Component.literal(
                            "Disposable 26.3 QA: 5 cars ahead; mechanic table (-5,200,14), assembler (5,200,14). No gameplay acceptance is inferred."));
                    LoggerFactory.getLogger("Showcase263Qa").info("AUTO263_SHOWCASE_PLAYER_READY player={}", PLAYER);
                } catch (Exception failure) {
                    LoggerFactory.getLogger("Showcase263Qa").error("AUTO263_SHOWCASE_PLAYER_SETUP_FAILED", failure);
                }
            }
        });
    }

    private static void prepareStage(ServerLevel level) {
        for (int x = -24; x <= 24; x++) {
            for (int z = -12; z <= 32; z++) {
                boolean lamp = Math.floorMod(x, 8) == 0 && Math.floorMod(z, 8) == 0;
                level.setBlockAndUpdate(new BlockPos(x, 199, z),
                        (lamp ? Blocks.SEA_LANTERN : Blocks.STONE).defaultBlockState());
                if (x == -24 || x == 24 || z == -12 || z == 32) {
                    level.setBlockAndUpdate(new BlockPos(x, 200, z), Blocks.STONE_BRICK_WALL.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(new BlockPos(-5, 200, 14), AutomobilityBlocks.AUTO_MECHANIC_TABLE.require().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(5, 200, 14), AutomobilityBlocks.AUTOMOBILE_ASSEMBLER.require().defaultBlockState());
        create(level, AutomobileFrame.STANDARD_RED, -12, "Automobility - red standard");
        create(level, riaFrame("wooden_quadmotorcar"), -6, "RIA - wooden quad (4 seats)");
        create(level, riaFrame("lorry"), 0, "RIA - lorry (54 cargo slots)");
        create(level, riaFrame("dmc12"), 6, "RIA - DMC12");
        create(level, riaFrame("standard_formula"), 12, "RIA - standard formula");
    }

    private static ResourceKey<AutomobileFrame> riaFrame(String id) {
        return ResourceKey.create(AutomobileFrame.REGISTRY, Identifier.fromNamespaceAndPath("riautomobility", id));
    }

    private static void create(ServerLevel level, ResourceKey<AutomobileFrame> frame, int x, String label) {
        var car = new AutomobileEntity(level);
        car.setComponents(level.registryAccess().lookupOrThrow(AutomobileFrame.REGISTRY).getOrThrow(frame),
                level.registryAccess().lookupOrThrow(AutomobileWheel.REGISTRY).getOrThrow(AutomobileWheel.STANDARD),
                level.registryAccess().lookupOrThrow(AutomobileEngine.REGISTRY).getOrThrow(AutomobileEngine.IRON));
        car.setPos(x + 0.5, 200, 5.5);
        car.setYRot(0);
        car.setCustomName(Component.literal(label));
        car.setCustomNameVisible(true);
        if (!level.addFreshEntity(car)) throw new IllegalStateException("Could not add showroom car " + label);
    }

    private static void command(MinecraftServer server, String command) throws Exception {
        server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack());
    }
}
