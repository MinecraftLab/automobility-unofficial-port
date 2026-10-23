package io.github.foundationgames.automobility.fabric;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.render.AutomobileItemSpecialRenderer;
import io.github.foundationgames.automobility.automobile.render.AutomobileModels;
import io.github.foundationgames.automobility.block.AutomobilityBlocks;
import io.github.foundationgames.automobility.block.entity.render.AutomobileAssemblerBlockEntityRenderer;
import io.github.foundationgames.automobility.client.vehicle.VehicleClientControls;
import io.github.foundationgames.automobility.client.vehicle.GarageTestDriveClient;
import io.github.foundationgames.automobility.client.vehicle.VehicleStatusHud;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.entity.AutomobilityEntities;
import io.github.foundationgames.automobility.entity.render.AutomobileEntityRenderer;
import io.github.foundationgames.automobility.fabric.block.render.FabricSlopeBlockStateModel;
import io.github.foundationgames.automobility.fabric.resource.FabricAutomobileModels;
import io.github.foundationgames.automobility.fabric.resource.FabricJsonEntityModelLoader;
import io.github.foundationgames.automobility.fabric.resource.FabricObjLoader;
import io.github.foundationgames.automobility.particle.AutomobilityParticles;
import io.github.foundationgames.automobility.particle.DriftSmokeParticle;
import io.github.foundationgames.automobility.screen.AutoMechanicTableScreen;
import io.github.foundationgames.automobility.screen.GarageScreen;
import io.github.foundationgames.automobility.screen.GarageVehiclePreviewRenderer;
import io.github.foundationgames.automobility.screen.GarageVehicleThumbnailRenderer;
import io.github.foundationgames.automobility.screen.AutomobileHud;
import io.github.foundationgames.automobility.screen.SingleSlotScreen;
import io.github.foundationgames.automobility.sound.AutomobileSoundInstance;
import io.github.foundationgames.automobility.sound.SlicedLoopingAutomobileSoundInstance;
import io.github.foundationgames.automobility.util.network.AutomobilityPacketPayload;
import io.github.foundationgames.automobility.util.network.ClientPackets;
import io.github.foundationgames.automobility.util.network.GarageTestDrivePackets;
import io.github.foundationgames.automobility.fabric.network.FabricCoreContentClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.block.Block;

import java.io.IOException;
import org.lwjgl.glfw.GLFW;

public class AutomobilityClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        io.github.foundationgames.automobility.client.vehicle.VehicleUiState.preserveBeforeBootstrap(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
        FabricPlatform.init();
        io.github.foundationgames.automobility.client.dashboard.VehicleDashboardClient.init();
        AutomobileModels.init();
        PictureInPictureRendererRegistry.register(context -> new GarageVehiclePreviewRenderer());
        PictureInPictureRendererRegistry.register(context -> new GarageVehicleThumbnailRenderer());
        io.github.foundationgames.automobility.fabric.render.RetainedT3Feature.initialize();
        io.github.foundationgames.automobility.client.lighting.NewVehicleLights.register();
        SpecialModelRenderers.ID_MAPPER.put(Automobility.rl("automobile_item"), AutomobileItemSpecialRenderer.Unbaked.CODEC);
        initSlopeModels();

        var clientResources = ResourceManagerHelper.get(PackType.CLIENT_RESOURCES);
        clientResources.registerReloadListener(FabricJsonEntityModelLoader.INSTANCE);
        clientResources.registerReloadListener(FabricObjLoader.INSTANCE);
        clientResources.registerReloadListener(FabricAutomobileModels.INSTANCE);

        MenuScreens.register(Automobility.AUTO_MECHANIC_SCREEN.require(), AutoMechanicTableScreen::new);
        MenuScreens.register(Automobility.GARAGE_SCREEN.require(), GarageScreen::new);
        MenuScreens.register(Automobility.SINGLE_SLOT_SCREEN.require(), SingleSlotScreen::new);
        BlockEntityRenderers.register(AutomobilityBlocks.AUTOMOBILE_ASSEMBLER_ENTITY.require(), AutomobileAssemblerBlockEntityRenderer::new);
        EntityRendererRegistry.register(AutomobilityEntities.AUTOMOBILE.require(), AutomobileEntityRenderer::new);
        EntityRendererRegistry.register(AutomobilityEntities.HITBOX.require(), NoopRenderer::new);
        initAutomobileSounds();
        initVehicleControls();
        initHud();
        initParticles();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registries) -> dispatcher.register(ClientCommands.literal("automobilityc")
                .then(ClientCommands.literal("vehicle").executes(ctx -> {
                    if (VehicleClientControls.queueSettings(Minecraft.getInstance())) return 1;
                    ctx.getSource().sendError(Component.translatable("vehicle.automobility.driver_required"));
                    return 0;
                }))
                .then(ClientCommands.literal("settings").executes(ctx ->
                        VehicleClientControls.queueEffectsSettings(Minecraft.getInstance()) ? 1 : 0))
                .then(ClientCommands.literal("dump").executes(ctx -> {
                    try {
                        AutomobileModels.dump();
                        Automobility.dumpDynamicRegistries(registries);
                        ctx.getSource().sendFeedback(Component.literal("Dumped all resources to .minecraft/automobility_dump/"));
                        return 1;
                    } catch (IOException ex) {
                        Automobility.LOG.error("Error dumping Automobility resources", ex);
                        ctx.getSource().sendError(Component.literal("Error dumping resources! See game log for details."));
                        return 0;
                    }
                }))));

        ClientPackets.initClient();
        FabricCoreContentClient.initialize();
        io.github.foundationgames.automobility.fabric.network.FabricMx5Client.initialize();
        initTestDriveNetwork();
        ClientPlayNetworking.registerGlobalReceiver(AutomobilityPacketPayload.TYPE, (payload, context) ->
                ClientPackets.CLIENTBOUND_HANDLERS.getOrDefault(payload.id(), (x, y) -> {}).accept(context.client(), payload.buf()));
    }

    private static void initSlopeModels() {
        ModelLoadingPlugin.register(context -> {
            registerSlopeModel(context, AutomobilityBlocks.SLOPE.require(), FabricSlopeBlockStateModel.Kind.SLOPE);
            registerSlopeModel(context, AutomobilityBlocks.STEEP_SLOPE.require(), FabricSlopeBlockStateModel.Kind.STEEP_SLOPE);
            registerSlopeModel(context, AutomobilityBlocks.SLOPE_WITH_DASH_PANEL.require(), FabricSlopeBlockStateModel.Kind.SLOPE_DASH_PANEL);
            registerSlopeModel(context, AutomobilityBlocks.STEEP_SLOPE_WITH_DASH_PANEL.require(), FabricSlopeBlockStateModel.Kind.STEEP_SLOPE_DASH_PANEL);
        });
    }

    private static void initTestDriveNetwork() {
        ClientPlayNetworking.registerGlobalReceiver(GarageTestDrivePackets.State.TYPE, (state, context) -> {
            var client = context.client(); var connection = client.getConnection();
            var level = client.level; var player = client.player;
            if (connection == null || level == null || player == null) return;
            client.execute(() -> {
                if (client.getConnection() == connection && client.level == level && client.player == player)
                    GarageTestDriveClient.receive(state);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((connection, client) ->
                client.execute(() -> GarageTestDriveClient.disconnected(connection)));
    }

    private static void registerSlopeModel(ModelLoadingPlugin.Context context, Block block, FabricSlopeBlockStateModel.Kind kind) {
        var root = new FabricSlopeBlockStateModel.UnbakedRoot(kind);
        context.registerBlockStateResolver(block, resolver ->
                block.getStateDefinition().getPossibleStates().forEach(state -> resolver.setModel(state, root)));
    }

    private static void initHud() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Automobility.rl("automobile_hud"), (graphics, tickDelta) -> {
            var player = Minecraft.getInstance().player;
            if (player != null && player.getVehicle() instanceof AutomobileEntity auto) {
                AutomobileHud.render(graphics, player, auto, tickDelta);
            }
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Automobility.rl("vehicle_status_hud"),
                (graphics, tickDelta) -> VehicleStatusHud.render(graphics));
    }

    private static void initVehicleControls() {
        var category = KeyMapping.Category.register(Automobility.rl("vehicle"));
        KeyMapping settings = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.automobility.vehicle_settings",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, category));
        // L belongs to vanilla advancements. Leave on/off unbound until the player chooses a free key.
        KeyMapping lights = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.automobility.toggle_vehicle_lights",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        KeyMapping highBeam = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.automobility.toggle_vehicle_high_beams",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, category));
        KeyMapping engine = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.automobility.toggle_vehicle_engine",
                InputConstants.Type.KEYSYM, InputConstants.KEY_Y, category));
        VehicleClientControls.initialize(settings, lights, highBeam, engine);
        KeyMapping testDriveReturn = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.automobility.garage_test_drive_return",
                InputConstants.Type.KEYSYM, net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")
                        ? GLFW.GLFW_KEY_UNKNOWN : GLFW.GLFW_KEY_R, category));
        GarageTestDriveClient.initialize(testDriveReturn, request -> {
            if (Minecraft.getInstance().getConnection() == null || !ClientPlayNetworking.canSend(GarageTestDrivePackets.Return.TYPE)) return false;
            ClientPlayNetworking.send(request); return true;
        });
        KeyMapping mx5Dance = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.automobility.mx5_popup_wink",
                InputConstants.UNKNOWN.getType(), InputConstants.UNKNOWN.getValue(), category));
        VehicleClientControls.initializeMx5HeadlightDance(mx5Dance);
        KeyMapping signalLeft = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.automobility.toggle_vehicle_left_signal",
                InputConstants.Type.KEYSYM, InputConstants.KEY_LEFT, category));
        KeyMapping signalRight = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.automobility.toggle_vehicle_right_signal",
                InputConstants.Type.KEYSYM, InputConstants.KEY_RIGHT, category));
        KeyMapping hazards = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.automobility.toggle_vehicle_hazards",
                InputConstants.Type.KEYSYM, InputConstants.KEY_U, category));
        VehicleClientControls.initializeSignals(signalLeft, signalRight, hazards);
        KeyMapping comfortLeft = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.automobility.vehicle_three_left_signal",
                InputConstants.Type.KEYSYM, InputConstants.KEY_COMMA, category));
        KeyMapping comfortRight = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.automobility.vehicle_three_right_signal",
                InputConstants.Type.KEYSYM, InputConstants.KEY_PERIOD, category));
        VehicleClientControls.initializeComfortSignals(comfortLeft, comfortRight);
        KeyMapping lock = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.automobility.toggle_vehicle_lock",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        VehicleClientControls.initializeLock(lock);
        ClientTickEvents.END_CLIENT_TICK.register(VehicleClientControls::tick);
    }

    private static void initParticles() {
        ParticleProviderRegistry.getInstance().register(AutomobilityParticles.DRIFT_SMOKE.require(), DriftSmokeParticle.Factory::new);
    }

    private static void initAutomobileSounds() {
        AutomobileEntity.engineSound = auto -> {
            if (auto.getEngine().isEmpty()) {
                return;
            }

            var client = Minecraft.getInstance();
            client.getSoundManager().play(new AutomobileSoundInstance.EngineSound(client, auto));
        };
        AutomobileEntity.skidSound = auto -> {
            var client = Minecraft.getInstance();
            client.getSoundManager().play(new AutomobileSoundInstance.SkiddingSound(client, auto));
        };
        AutomobileEntity.hornSound = auto -> {
            var client = Minecraft.getInstance();
            var horn = auto.getFrame().horn();
            for (float pitch : horn.pitches()) {
                client.getSoundManager().play(new SlicedLoopingAutomobileSoundInstance.HornSound(client, auto, horn, pitch));
            }
        };
    }
}
