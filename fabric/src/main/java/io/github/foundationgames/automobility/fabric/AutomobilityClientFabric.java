package io.github.foundationgames.automobility.fabric;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.render.AutomobileItemSpecialRenderer;
import io.github.foundationgames.automobility.automobile.render.AutomobileModels;
import io.github.foundationgames.automobility.block.AutomobilityBlocks;
import io.github.foundationgames.automobility.block.entity.render.AutomobileAssemblerBlockEntityRenderer;
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
import io.github.foundationgames.automobility.screen.AutomobileHud;
import io.github.foundationgames.automobility.screen.SingleSlotScreen;
import io.github.foundationgames.automobility.sound.AutomobileSoundInstance;
import io.github.foundationgames.automobility.sound.SlicedLoopingAutomobileSoundInstance;
import io.github.foundationgames.automobility.util.network.AutomobilityPacketPayload;
import io.github.foundationgames.automobility.util.network.ClientPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.block.Block;

import java.io.IOException;

public class AutomobilityClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricPlatform.init();
        AutomobileModels.init();
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
        initHud();
        initParticles();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registries) -> dispatcher.register(ClientCommands.literal("automobilityc")
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
