package io.github.foundationgames.automobility.entity.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.foundationgames.automobility.automobile.render.AutomobileModels;
import io.github.foundationgames.automobility.automobile.render.AutomobileRenderer;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;

public class AutomobileEntityRenderer extends EntityRenderer<AutomobileEntity, AutomobileEntityRenderer.State> {
    public AutomobileEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        AutomobileModels.setModelProvider(ctx);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AutomobileEntity entity, State state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        state.automobile = entity;
        state.tickDelta = tickDelta;
        state.yaw = entity.getAutomobileYaw(tickDelta);
        state.verticalOffset = entity.getDisplacement().getVerticalOffset(tickDelta, entity);
        entity.getDisplacement().getAngular(tickDelta, state.angularRotation.identity());
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector submitter, CameraRenderState cameraState) {
        super.submit(state, pose, submitter, cameraState);

        if (state.isInvisible) {
            return;
        }

        if (state.automobile == null) {
            return;
        }

        pose.pushPose();
        pose.translate(0, state.verticalOffset, 0);
        pose.mulPose(state.angularRotation);
        AutomobileRenderer.render(pose, submitter, state.lightCoords, OverlayTexture.NO_OVERLAY, state.tickDelta, state.automobile);

        pose.popPose();
    }

    public static class State extends EntityRenderState {
        public final Quaternionf angularRotation = new Quaternionf();
        public AutomobileEntity automobile;
        public float tickDelta;
        public float yaw;
        public float verticalOffset;
    }
}
