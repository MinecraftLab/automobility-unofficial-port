package io.github.foundationgames.automobility.platform;

import io.github.foundationgames.automobility.controller.AutomobileController;
import io.github.foundationgames.automobility.item.CreativeTabQueue;
import io.github.foundationgames.automobility.util.TriFunc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
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

public interface Platform {
    static void init(Platform instance) {
        GlobalPlatformInstance.INSTANCE = instance;
    }

    static Platform get() {
        if (GlobalPlatformInstance.INSTANCE == null) {
            throw new RuntimeException("Automobility's load order was disrupted!");
        }

        return GlobalPlatformInstance.INSTANCE;
    }

    CreativeModeTab creativeTab(Identifier rl, Supplier<ItemStack> icon, CreativeTabQueue displayItemsGenerator);

    <T extends AbstractContainerMenu> MenuType<T> menuType(BiFunction<Integer, Inventory, T> factory);

    <T extends AbstractContainerMenu, D> MenuType<T> extendedMenuType(
            TriFunc<Integer, Inventory, D, T> factory,
            StreamCodec<? super RegistryFriendlyByteBuf, D> streamCodec);

    <D> MenuProvider extendedMenuProvider(
            Component title,
            TriFunc<Integer, Inventory, Player, AbstractContainerMenu> factory,
            Function<ServerPlayer, D> openingData);

    <T extends BlockEntity> BlockEntityType<T> blockEntity(BiFunction<BlockPos, BlockState, T> factory, Block... blocks);

    void serverSendPacket(ServerPlayer player, Identifier rl, FriendlyByteBuf buf);

    void clientSendPacket(Identifier rl, FriendlyByteBuf buf);

    default void forEachTrackingPlayer(Entity entity, Consumer<ServerPlayer> action) {
        if (entity.level() instanceof ServerLevel level) {
            var chunkPos = ChunkPos.containing(entity.blockPosition());
            level.getPlayers(player -> player.getChunkTrackingView().contains(chunkPos)).forEach(action);
        }
    }

    <T extends Entity> EntityType<T> entityType(MobCategory category, BiFunction<EntityType<?>, Level, T> factory, EntityDimensions size, int updateRate, int updateRange, boolean internal, String key);

    void registerDataSerializer(Identifier id, EntityDataSerializer<?> serializer);

    SimpleParticleType simpleParticleType(boolean z);

    AutomobileController controller();

    Path getGameDir();
}
