package io.github.foundationgames.automobility.fabric.block.render;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.block.DashPanelBlock;
import io.github.foundationgames.automobility.block.SlopeBlock;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class FabricSlopeBlockStateModel implements BlockStateModel, FabricBlockStateModel {
    private static final Identifier TEX_FRAME = Automobility.rl("block/slope_frame");
    private static final Identifier TEX_DASH_PANEL = Automobility.rl("block/dash_panel");
    private static final Identifier TEX_DASH_PANEL_OFF = Automobility.rl("block/dash_panel_off");
    private static final Identifier TEX_DASH_PANEL_FRAME = Automobility.rl("block/dash_panel_frame");

    private final Kind kind;
    private final Type type;
    private final Direction facing;
    private final boolean powered;
    private final Material.Baked frame;
    private final Material.Baked dashPanel;
    private final Material.Baked dashPanelOff;
    private final Material.Baked dashPanelFrame;
    private final Map<BlockState, Material.Baked> frameOverrides;

    public FabricSlopeBlockStateModel(Kind kind, Type type, Direction facing, boolean powered, Material.Baked frame,
                                      Material.Baked dashPanel, Material.Baked dashPanelOff,
                                      Material.Baked dashPanelFrame, Map<BlockState, Material.Baked> frameOverrides) {
        this.kind = kind;
        this.type = type;
        this.facing = facing;
        this.powered = powered;
        this.frame = frame;
        this.dashPanel = dashPanel;
        this.dashPanelOff = dashPanelOff;
        this.dashPanelFrame = dashPanelFrame;
        this.frameOverrides = frameOverrides;
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        var geo = new FabricGeometryBuilder(emitter, rotationFor(facing));
        var frameMaterial = getFrameMaterial(level, pos);
        int frameColor = getFrameColor(level, pos);
        boolean borderedLeft = level.getBlockState(pos.relative(facing.getCounterClockWise(Direction.Axis.Y))).equals(state);
        boolean borderedRight = level.getBlockState(pos.relative(facing.getClockWise(Direction.Axis.Y))).equals(state);

        buildSlopeGeometry(frameMaterial, geo, frameColor, borderedLeft, borderedRight);
    }

    @Override
    public void collectParts(RandomSource random, List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts) {
    }

    @Override
    public Material.Baked particleMaterial() {
        return frame;
    }

    @Override
    public int materialFlags() {
        return 0;
    }

    private Material.Baked getFrameMaterial(BlockAndTintGetter level, BlockPos pos) {
        var belowPos = pos.below();
        var blockBelow = level.getBlockState(belowPos);
        var override = frameOverrides.get(blockBelow);

        if (override != null) {
            return override;
        }
        if (!blockBelow.isAir() && blockBelow.isCollisionShapeFullBlock(level, belowPos)) {
            return Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(blockBelow);
        }

        return frame;
    }

    private int getFrameColor(BlockAndTintGetter level, BlockPos pos) {
        var belowPos = pos.below();
        var blockBelow = level.getBlockState(belowPos);
        var tint = Minecraft.getInstance().getBlockColors().getTintSource(blockBelow, 0);

        if (tint != null) {
            return tint.colorInWorld(blockBelow, level, belowPos) | 0xFF000000;
        }

        return 0xFFFFFFFF;
    }

    private void buildSlopeGeometry(Material.Baked material, FabricGeometryBuilder geo, int frameColor,
                                    boolean borderedLeft, boolean borderedRight) {
        boolean steep = type == Type.STEEP;
        float height = steep ? 1 : 0.5f;
        float rise = steep ? 0 : (type == Type.TOP ? 0.5f : 0);
        boolean top = true;

        if (kind.hasDashPanel()) {
            top = false;
            plate(height, rise, !borderedLeft, !borderedRight, powered ? dashPanelOff : dashPanel, dashPanelFrame, geo);
        }

        rightTriPrism(height, rise, frameColor, top, material, geo);

        float invRH = 1 - (rise + height);
        geo
                .vertex(0, rise + height, 1, Direction.SOUTH, 0, 0, 1, material, 0, invRH, frameColor)
                .vertex(0, 0, 1, Direction.SOUTH, 0, 0, 1, material, 0, 1, frameColor)
                .vertex(1, 0, 1, Direction.SOUTH, 0, 0, 1, material, 1, 1, frameColor)
                .vertex(1, rise + height, 1, Direction.SOUTH, 0, 0, 1, material, 1, invRH, frameColor)

                .vertex(0, 0, 1, Direction.DOWN, 0, -1, 0, material, 0, 0, frameColor)
                .vertex(0, 0, 0, Direction.DOWN, 0, -1, 0, material, 0, 1, frameColor)
                .vertex(1, 0, 0, Direction.DOWN, 0, -1, 0, material, 1, 1, frameColor)
                .vertex(1, 0, 1, Direction.DOWN, 0, -1, 0, material, 1, 0, frameColor);

        if (rise > 0) {
            float invR = 1 - rise;
            geo
                    .vertex(0, rise, 0, Direction.WEST, -1, 0, 0, material, 0, invR, frameColor)
                    .vertex(0, 0, 0, Direction.WEST, -1, 0, 0, material, 0, 1, frameColor)
                    .vertex(0, 0, 1, Direction.WEST, -1, 0, 0, material, 1, 1, frameColor)
                    .vertex(0, rise, 1, Direction.WEST, -1, 0, 0, material, 1, invR, frameColor)

                    .vertex(1, rise, 1, Direction.EAST, 1, 0, 0, material, 1, invR, frameColor)
                    .vertex(1, 0, 1, Direction.EAST, 1, 0, 0, material, 1, 1, frameColor)
                    .vertex(1, 0, 0, Direction.EAST, 1, 0, 0, material, 0, 1, frameColor)
                    .vertex(1, rise, 0, Direction.EAST, 1, 0, 0, material, 0, invR, frameColor)

                    .vertex(1, rise, 0, Direction.NORTH, 0, 0, 1, material, 1, invR, frameColor)
                    .vertex(1, 0, 0, Direction.NORTH, 0, 0, 1, material, 1, 1, frameColor)
                    .vertex(0, 0, 0, Direction.NORTH, 0, 0, 1, material, 0, 1, frameColor)
                    .vertex(0, rise, 0, Direction.NORTH, 0, 0, 1, material, 0, invR, frameColor);
        }
    }

    private void rightTriPrism(float height, float rise, int color, boolean top, Material.Baked material,
                               FabricGeometryBuilder geo) {
        float invR = 1 - rise;
        float invRH = 1 - (rise + height);
        var topNormal = new Vector3f(0, 1, -height);
        topNormal.normalize();

        geo
                .vertex(0, rise + height, 1, Direction.WEST, -1, 0, 0, material, 1, invRH, color)
                .vertex(0, rise, 0, Direction.WEST, -1, 0, 0, material, 0, invR, color)
                .vertex(0, rise, 1, Direction.WEST, -1, 0, 0, material, 1, invR, color)
                .vertex(0, rise + height, 1, Direction.WEST, -1, 0, 0, material, 1, invRH, color)

                .vertex(1, rise + height, 1, Direction.EAST, 1, 0, 0, material, 0, invRH, color)
                .vertex(1, rise + height, 1, Direction.EAST, 1, 0, 0, material, 0, invRH, color)
                .vertex(1, rise, 1, Direction.EAST, 1, 0, 0, material, 0, invR, color)
                .vertex(1, rise, 0, Direction.EAST, 1, 0, 0, material, 1, invR, color);

        if (top) {
            geo
                    .vertex(0, rise, 0, null, topNormal.x(), topNormal.y(), topNormal.z(), material, 1, 1, color)
                    .vertex(0, rise + height, 1, null, topNormal.x(), topNormal.y(), topNormal.z(), material, 1, 0, color)
                    .vertex(1, rise + height, 1, null, topNormal.x(), topNormal.y(), topNormal.z(), material, 0, 0, color)
                    .vertex(1, rise, 0, null, topNormal.x(), topNormal.y(), topNormal.z(), material, 0, 1, color);
        }
    }

    private void plate(float height, float rise, boolean left, boolean right, Material.Baked plateInner,
                       Material.Baked plateOuter, FabricGeometryBuilder geo) {
        var topNormal = new Vector3f(0, 1, -height);
        topNormal.normalize();
        var northNormal = new Vector3f(0, -height, 1);
        northNormal.normalize();
        var southNormal = new Vector3f(0, height, 1);
        southNormal.normalize();

        var topFaceOffset = new Vector3f(topNormal);
        topFaceOffset.mul(0.0625f);
        var onePxUp = new Vector3f(southNormal);
        onePxUp.normalize();
        onePxUp.mul(0.0625f);

        geo
                .vertex(right ? 0.9375f : 1, 0.001f + rise + topFaceOffset.y() + onePxUp.y(), topFaceOffset.z() + onePxUp.z(), null, topNormal.x(), topNormal.y(), topNormal.z(), plateInner, right ? 0.0625f : 0, 0.9375f)
                .vertex(left ? 0.0625f : 0, 0.001f + rise + topFaceOffset.y() + onePxUp.y(), topFaceOffset.z() + onePxUp.z(), null, topNormal.x(), topNormal.y(), topNormal.z(), plateInner, left ? 0.9375f : 1, 0.9375f)
                .vertex(left ? 0.0625f : 0, (0.001f + rise + height + topFaceOffset.y()) - onePxUp.y(), (1 + topFaceOffset.z()) - onePxUp.z(), null, topNormal.x(), topNormal.y(), topNormal.z(), plateInner, left ? 0.9375f : 1, 0.0625f)
                .vertex(right ? 0.9375f : 1, (0.001f + rise + height + topFaceOffset.y()) - onePxUp.y(), (1 + topFaceOffset.z()) - onePxUp.z(), null, topNormal.x(), topNormal.y(), topNormal.z(), plateInner, right ? 0.0625f : 0, 0.0625f)

                .vertex(1, rise + topFaceOffset.y(), topFaceOffset.z(), null, topNormal.x(), topNormal.y(), topNormal.z(), plateOuter, 0, 1)
                .vertex(0, rise + topFaceOffset.y(), topFaceOffset.z(), null, topNormal.x(), topNormal.y(), topNormal.z(), plateOuter, 1, 1)
                .vertex(0, rise + height + topFaceOffset.y(), 1 + topFaceOffset.z(), null, topNormal.x(), topNormal.y(), topNormal.z(), plateOuter, 1, 0)
                .vertex(1, rise + height + topFaceOffset.y(), 1 + topFaceOffset.z(), null, topNormal.x(), topNormal.y(), topNormal.z(), plateOuter, 0, 0)

                .vertex(1, rise, 0, null, northNormal.x(), northNormal.y(), northNormal.z(), plateOuter, 0, 1)
                .vertex(0, rise, 0, null, northNormal.x(), northNormal.y(), northNormal.z(), plateOuter, 1, 1)
                .vertex(0, rise + topFaceOffset.y(), topFaceOffset.z(), null, northNormal.x(), northNormal.y(), northNormal.z(), plateOuter, 1, 0.9375f)
                .vertex(1, rise + topFaceOffset.y(), topFaceOffset.z(), null, northNormal.x(), northNormal.y(), northNormal.z(), plateOuter, 0, 0.9375f)

                .vertex(1, rise + height + topFaceOffset.y(), 1 + topFaceOffset.z(), null, southNormal.x(), southNormal.y(), southNormal.z(), plateOuter, 0, 0)
                .vertex(0, rise + height + topFaceOffset.y(), 1 + topFaceOffset.z(), null, southNormal.x(), southNormal.y(), southNormal.z(), plateOuter, 1, 0)
                .vertex(0, rise + height, 1, null, southNormal.x(), southNormal.y(), southNormal.z(), plateOuter, 1, 0.0625f)
                .vertex(1, rise + height, 1, null, southNormal.x(), southNormal.y(), southNormal.z(), plateOuter, 0, 0.0625f)

                .vertex(1, rise + height, 1, null, 1, 0, 0, plateOuter, 0, 0)
                .vertex(1, rise, 0, null, 1, 0, 0, plateOuter, 0, 1)
                .vertex(1, rise + topFaceOffset.y(), topFaceOffset.z(), null, 1, 0, 0, plateOuter, 0.0625f, 1)
                .vertex(1, rise + height + topFaceOffset.y(), 1 + topFaceOffset.z(), null, 1, 0, 0, plateOuter, 0.0625f, 0)

                .vertex(0, rise + height + topFaceOffset.y(), 1 + topFaceOffset.z(), null, -1, 0, 0, plateOuter, 1, 0)
                .vertex(0, rise + topFaceOffset.y(), topFaceOffset.z(), null, -1, 0, 0, plateOuter, 1, 1)
                .vertex(0, rise, 0, null, -1, 0, 0, plateOuter, 0.9375f, 1)
                .vertex(0, rise + height, 1, null, -1, 0, 0, plateOuter, 0.9375f, 0);
    }

    private static Matrix4f rotationFor(Direction facing) {
        return new Matrix4f().rotateY((float) Math.toRadians(-switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        }));
    }

    private static Material.Baked material(ModelBaker baker, Identifier texture) {
        return baker.materials().get(new Material(texture), () -> texture.toString());
    }

    private static Map<BlockState, Material.Baked> createFrameOverrides(ModelBaker baker) {
        return Map.of(
                Blocks.GRASS_BLOCK.defaultBlockState(), material(baker, Identifier.fromNamespaceAndPath("minecraft", "block/grass_block_top")),
                Blocks.PODZOL.defaultBlockState(), material(baker, Identifier.fromNamespaceAndPath("minecraft", "block/podzol_top")),
                Blocks.MYCELIUM.defaultBlockState(), material(baker, Identifier.fromNamespaceAndPath("minecraft", "block/mycelium_top")),
                Blocks.CRIMSON_NYLIUM.defaultBlockState(), material(baker, Identifier.fromNamespaceAndPath("minecraft", "block/crimson_nylium")),
                Blocks.WARPED_NYLIUM.defaultBlockState(), material(baker, Identifier.fromNamespaceAndPath("minecraft", "block/warped_nylium"))
        );
    }

    private static Type typeFor(Kind kind, BlockState state) {
        if (kind.isSteep()) {
            return Type.STEEP;
        }

        return state.getValue(SlopeBlock.HALF) == Half.TOP ? Type.TOP : Type.BOTTOM;
    }

    private static boolean poweredFor(Kind kind, BlockState state) {
        return kind.hasDashPanel() && state.getValue(DashPanelBlock.POWERED);
    }

    private enum Type {
        BOTTOM, TOP, STEEP
    }

    public enum Kind {
        SLOPE,
        STEEP_SLOPE,
        SLOPE_DASH_PANEL,
        STEEP_SLOPE_DASH_PANEL;

        boolean isSteep() {
            return this == STEEP_SLOPE || this == STEEP_SLOPE_DASH_PANEL;
        }

        boolean hasDashPanel() {
            return this == SLOPE_DASH_PANEL || this == STEEP_SLOPE_DASH_PANEL;
        }
    }

    public static class UnbakedRoot implements BlockStateModel.UnbakedRoot {
        private final Kind kind;

        public UnbakedRoot(Kind kind) {
            this.kind = kind;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
        }

        @Override
        public BlockStateModel bake(BlockState state, ModelBaker baker) {
            return new FabricSlopeBlockStateModel(kind, typeFor(kind, state), state.getValue(HorizontalDirectionalBlock.FACING),
                    poweredFor(kind, state), material(baker, TEX_FRAME), material(baker, TEX_DASH_PANEL),
                    material(baker, TEX_DASH_PANEL_OFF), material(baker, TEX_DASH_PANEL_FRAME),
                    createFrameOverrides(baker));
        }

        @Override
        public Object visualEqualityGroup(BlockState state) {
            return state;
        }
    }
}
