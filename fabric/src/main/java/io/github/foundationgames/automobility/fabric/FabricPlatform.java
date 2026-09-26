package io.github.foundationgames.automobility.fabric;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.controller.AutomobileController;
import io.github.foundationgames.automobility.item.CreativeTabQueue;
import io.github.foundationgames.automobility.platform.Platform;
import io.github.foundationgames.automobility.util.AUtils;
import io.github.foundationgames.automobility.util.TriFunc;
import io.github.foundationgames.automobility.util.network.AutomobilityPacketPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityDataRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FabricPlatform implements Platform {
    private static final FabricPlatform INSTANCE = new FabricPlatform();

    private AutomobileController automobileController = null;

    public static void init() {
        Platform.init(INSTANCE);
    }

    @Override
    public CreativeModeTab creativeTab(Identifier rl, Supplier<ItemStack> icon, CreativeTabQueue displayItemsGenerator) {
        return FabricCreativeModeTab.builder()
                .icon(icon)
                .title(Component.translatable("itemGroup." + rl.getNamespace() + "." + rl.getPath()))
                .displayItems((params, output) -> displayItemsGenerator.accept(output::accept, params.holders()))
                .build();
    }

    @Override
    public <T extends AbstractContainerMenu> MenuType<T> menuType(BiFunction<Integer, Inventory, T> factory) {
        return new MenuType<>(factory::apply, FeatureFlags.DEFAULT_FLAGS);
    }

    @Override
    public <T extends AbstractContainerMenu, D> MenuType<T> extendedMenuType(
            TriFunc<Integer, Inventory, D, T> factory,
            StreamCodec<? super RegistryFriendlyByteBuf, D> streamCodec) {
        return new ExtendedMenuType<>((syncId, inventory, data) -> factory.apply(syncId, inventory, data), streamCodec);
    }

    @Override
    public <D> MenuProvider extendedMenuProvider(
            Component title,
            TriFunc<Integer, Inventory, Player, AbstractContainerMenu> factory,
            Function<ServerPlayer, D> openingData) {
        return new ExtendedMenuProvider<>() {
            @Override
            public D getScreenOpeningData(ServerPlayer player) {
                return openingData.apply(player);
            }

            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
                return factory.apply(syncId, inventory, player);
            }
        };
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> blockEntity(BiFunction<BlockPos, BlockState, T> factory, Block... blocks) {
        return FabricBlockEntityTypeBuilder.create(factory::apply, blocks).build();
    }

    @Override
    public void serverSendPacket(ServerPlayer player, Identifier rl, FriendlyByteBuf buf) {
        ServerPlayNetworking.send(player, new AutomobilityPacketPayload(rl, AUtils.arrayOf(buf)));
    }

    @Override
    public void clientSendPacket(Identifier rl, FriendlyByteBuf buf) {
        ClientPlayNetworking.send(new AutomobilityPacketPayload(rl, AUtils.arrayOf(buf)));
    }

    @Override
    public void forEachTrackingPlayer(Entity entity, Consumer<ServerPlayer> action) {
        if (entity.level() instanceof ServerLevel) {
            PlayerLookup.tracking(entity).forEach(action);
        }
    }

    @Override
    public <T extends Entity> EntityType<T> entityType(MobCategory category, BiFunction<EntityType<?>, Level, T> factory, EntityDimensions size, int updateRate, int updateRange, boolean internal, String key) {
        var builder = EntityType.Builder.of(factory::apply, category).sized(size.width(), size.height()).eyeHeight(size.eyeHeight()).updateInterval(updateRate).clientTrackingRange(updateRange);
        if (internal) {
            builder.noSave().noSummon();
        }
        return builder.build(ResourceKey.create(Registries.ENTITY_TYPE, Automobility.rl(key)));
    }

    @Override
    public void registerDataSerializer(Identifier id, EntityDataSerializer<?> serializer) {
        FabricEntityDataRegistry.register(id, serializer);
    }

    @Override
    public SimpleParticleType simpleParticleType(boolean z) {
        return FabricParticleTypes.simple(z);
    }

    @Override
    public AutomobileController controller() {
        if (automobileController == null) {
            automobileController = FabricLoader.getInstance().isModLoaded("controlify")
                    ? createControlifyController()
                    : AutomobileController.INCOMPATIBLE;
        }

        return automobileController;
    }

    private AutomobileController createControlifyController() {
        if (io.github.foundationgames.automobility.fabric.controller.controlify.ControlifyApiFamily.detect(getClass().getClassLoader())
                == io.github.foundationgames.automobility.fabric.controller.controlify.ControlifyApiFamily.UNSUPPORTED) {
            Automobility.LOG.warn("Unsupported Controlify public API; retaining keyboard automobile controls.");
            return AutomobileController.INCOMPATIBLE;
        }
        try {
            return (AutomobileController) Class.forName("io.github.foundationgames.automobility.fabric.controller.controlify.ControlifyController")
                    .getConstructor()
                    .newInstance();
        } catch (ReflectiveOperationException | LinkageError ex) {
            Automobility.LOG.warn("Controlify is loaded, but Automobility could not initialize Controlify support", ex);
            return AutomobileController.INCOMPATIBLE;
        }
    }

    @Override
    public Path getGameDir() {
        return FabricLoader.getInstance().getGameDir();
    }
}
