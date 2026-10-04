package io.github.foundationgames.automobility.block;

import io.github.foundationgames.automobility.screen.AutoMechanicTableScreenHandler;
import io.github.foundationgames.automobility.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class AutoMechanicTableBlock extends Block {
    public static final Component UI_TITLE = Component.translatable("container.automobility.auto_mechanic_table");

    public AutoMechanicTableBlock(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        } else {
            player.openMenu(state.getMenuProvider(world, pos));
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public @Nullable MenuProvider getMenuProvider(BlockState state, Level world, BlockPos pos) {
        return mechanicMenuProvider(ContainerLevelAccess.create(world, pos));
    }

    public static MenuProvider mechanicMenuProvider(ContainerLevelAccess access) {
        return Platform.get().extendedMenuProvider(
                UI_TITLE,
                (syncId, playerInventory, player) -> new AutoMechanicTableScreenHandler(syncId, playerInventory, access),
                AutoMechanicTableScreenHandler.OpeningData::fromPlayer);
    }
}
