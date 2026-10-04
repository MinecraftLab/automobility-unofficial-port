package io.github.foundationgames.automobility.fabric;

import com.mojang.serialization.Codec;
import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.util.DefaultRegistrar;
import io.github.foundationgames.automobility.util.RegistryQueue;
import io.github.foundationgames.automobility.util.network.AutomobilityPacketPayload;
import io.github.foundationgames.automobility.util.network.CommonPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import io.github.foundationgames.automobility.entity.GarageService;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.event.registry.DynamicRegistrySetupCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;

public class AutomobilityFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricPlatform.init();
        GarageService.setEngineIntegrationPresent(FabricLoader.getInstance().isModLoaded("pumps_and_pistons_automobility")
                || FabricLoader.getInstance().isModLoaded("powerworks"));
        Automobility.initDynamicRegistries(new Automobility.DynamicRegistryRegistrar() {
            @Override
            public <T> void accept(ResourceKey<Registry<T>> key, Codec<T> codec, DefaultRegistrar<T> defaults) {
                DynamicRegistries.registerSynced(key, codec, codec);
                DynamicRegistrySetupCallback.EVENT.register(reg -> reg.getOptional(key).ifPresent(r ->
                        defaults.bootstrap((k, v) -> Registry.register(r, k, v))));
            }
        });

        Automobility.init();

        PayloadTypeRegistry.serverboundPlay().register(AutomobilityPacketPayload.TYPE, AutomobilityPacketPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AutomobilityPacketPayload.TYPE, AutomobilityPacketPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AutomobilityPacketPayload.TYPE, (payload, context) ->
                CommonPackets.SERVERBOUND_HANDLERS.getOrDefault(payload.id(), (x,y,z)->{}).accept(context.server(), context.player(), payload.buf()));

        register(BuiltInRegistries.BLOCK);
        register(BuiltInRegistries.BLOCK_ENTITY_TYPE);
        register(BuiltInRegistries.DATA_COMPONENT_TYPE);
        register(BuiltInRegistries.ITEM);
        register(BuiltInRegistries.ENTITY_TYPE);
        register(BuiltInRegistries.PARTICLE_TYPE);
        register(BuiltInRegistries.SOUND_EVENT);
        register(BuiltInRegistries.MENU);
        register(BuiltInRegistries.RECIPE_TYPE);
        register(BuiltInRegistries.RECIPE_SERIALIZER);
        register(BuiltInRegistries.CREATIVE_MODE_TAB);
    }

    public static <V> void register(Registry<V> registry) {
        RegistryQueue.getQueue(registry).forEach(e -> Registry.register(registry, e.rl(), e.entry().create()));
    }
}
