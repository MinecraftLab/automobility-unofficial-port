package io.github.foundationgames.automobility.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.redstone.Orientation;

public class SlopeWithDashPanelBlock extends SlopeBlock {
    public static final MapCodec<SlopeWithDashPanelBlock> CODEC = Block.simpleCodec(SlopeWithDashPanelBlock::new);

    public SlopeWithDashPanelBlock(Properties settings) {
        super(settings);

        registerDefaultState(defaultBlockState().setValue(DashPanelBlock.POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DashPanelBlock.POWERED);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean notify) {
        boolean levelPwr = level.hasNeighborSignal(pos);
        boolean selfPwr = state.getValue(DashPanelBlock.POWERED);

        if (levelPwr != selfPwr) {
            level.setBlockAndUpdate(pos, state.setValue(DashPanelBlock.POWERED, levelPwr));
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(AutomobilityBlocks.DASH_PANEL.require());
    }

    @Override
    protected void entityInside(BlockState state, Level world, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isInside) {
        super.entityInside(state, world, pos, entity, effectApplier, isInside);
        DashPanelBlock.onCollideWithDashPanel(state, entity);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
}
