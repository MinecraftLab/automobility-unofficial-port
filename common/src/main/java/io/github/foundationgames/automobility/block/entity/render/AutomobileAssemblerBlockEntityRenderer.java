package io.github.foundationgames.automobility.block.entity.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.foundationgames.automobility.automobile.render.AutomobileRenderer;
import io.github.foundationgames.automobility.block.entity.AutomobileAssemblerBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class AutomobileAssemblerBlockEntityRenderer implements BlockEntityRenderer<AutomobileAssemblerBlockEntity, AutomobileAssemblerBlockEntityRenderer.State> {
    private final Font textRenderer;

    public AutomobileAssemblerBlockEntityRenderer(BlockEntityRendererProvider.Context blockEntityCtx) {
        this.textRenderer = blockEntityCtx.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AutomobileAssemblerBlockEntity entity, State state, float tickDelta, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderState.extractBase(entity, state, crumblingOverlay);
        state.entity = entity;
        state.tickDelta = tickDelta;
        state.label = List.copyOf(entity.label);
    }

    @Override
    public void submit(State state, PoseStack matrices, SubmitNodeCollector submitter, CameraRenderState cameraState) {
        if (state.entity == null) {
            return;
        }

        matrices.pushPose();
        matrices.translate(0.5, 0.75 - (state.entity.getWheels().model().radius() / 16), 0.5);
        AutomobileRenderer.render(matrices, submitter, state.lightCoords, OverlayTexture.NO_OVERLAY, state.tickDelta, state.entity);
        matrices.popPose();

        matrices.pushPose();
        matrices.translate(0.5, 0, 0.5);
        matrices.rotate(Axis.YP.rotationDegrees(-state.entity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot()));
        matrices.translate(0, 0.372, 0.501);
        matrices.scale(0.008f, -0.008f, 0.008f);

        for (var text : state.label) {
            matrices.pushPose();
            matrices.translate(-0.5 * textRenderer.width(text), 0, 0);
            submitter.submitText(matrices, 0f, 0f, text.getVisualOrderText(), true, Font.DisplayMode.POLYGON_OFFSET,
                    state.lightCoords, 0xFFFFFFFF, 0, 0);
            matrices.popPose();
            matrices.translate(0, 12, 0);
        }

        matrices.popPose();
    }

    public static class State extends BlockEntityRenderState {
        public AutomobileAssemblerBlockEntity entity;
        public float tickDelta;
        public List<Component> label = List.of();
    }
}
