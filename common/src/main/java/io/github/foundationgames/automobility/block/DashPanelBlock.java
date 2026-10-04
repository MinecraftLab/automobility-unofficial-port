package io.github.foundationgames.automobility.block;

import com.mojang.serialization.MapCodec;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.entity.AutomobilityEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

public class DashPanelBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public static final VoxelShape SHAPE = box(0, 0, 0, 16, 1, 16);

    public static final MapCodec<DashPanelBlock> CODEC = Block.simpleCodec(DashPanelBlock::new);

    public DashPanelBlock(Properties settings) {
        super(settings);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH)
                .setValue(LEFT, false).setValue(RIGHT, false)
                .setValue(POWERED, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, LEFT, RIGHT, POWERED, WATERLOGGED);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return super.getStateForPlacement(ctx).setValue(FACING, ctx.getHorizontalDirection()).setValue(WATERLOGGED, ctx.getLevel().getBlockState(ctx.getClickedPos()).is(Blocks.WATER));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        var lState = level.getBlockState(pos.relative(state.getValue(FACING).getCounterClockWise(Direction.Axis.Y)));
        var rState = level.getBlockState(pos.relative(state.getValue(FACING).getClockWise(Direction.Axis.Y)));
        boolean left = lState.is(this) && (lState.getValue(POWERED) == state.getValue(POWERED));
        boolean right = rState.is(this) && (rState.getValue(POWERED) == state.getValue(POWERED));

        return state.setValue(LEFT, left).setValue(RIGHT, right);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean notify) {
        boolean levelPwr = level.hasNeighborSignal(pos);
        boolean selfPwr = state.getValue(POWERED);

        if (levelPwr != selfPwr) {
            level.setBlockAndUpdate(pos, state.setValue(POWERED, levelPwr));
        }

        if (!canSurvive(state, level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected void entityInside(BlockState state, Level world, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isInside) {
        super.entityInside(state, world, pos, entity, effectApplier, isInside);
        onCollideWithDashPanel(state, entity);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public static void onCollideWithDashPanel(@Nullable BlockState panelState, Entity entity) {
        if (panelState != null && panelState.getValue(POWERED)) {
            return;
        }

        if (entity instanceof AutomobileEntity auto) {
            auto.boost(0.45f, 50);
        } else if (entity.getType().builtInRegistryHolder().is(AutomobilityEntities.DASH_PANEL_BOOSTABLES)) {
            if (entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 6, true, false, false));
            }
            double yaw = Math.toRadians(-entity.getYRot());
            var vel = new Vec3(Math.sin(yaw), 0, Math.cos(yaw));
            entity.push(vel.x, vel.y, vel.z);
        }
    }
}
