package io.github.foundationgames.automobility.entity.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.foundationgames.automobility.automobile.render.AutomobileModels;
import io.github.foundationgames.automobility.automobile.render.AutomobileRenderer;
import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;
import io.github.foundationgames.automobility.automobile.light.Mx5VisualPose;
import io.github.foundationgames.automobility.automobile.light.Mx5HoodGeometry;
import io.github.foundationgames.automobility.client.lighting.HeadlightLampGeometry;
import io.github.foundationgames.automobility.client.lighting.VehicleHeadlightRenderer;
import io.github.foundationgames.automobility.client.lighting.VehicleHeadlightLampRenderer;
import io.github.foundationgames.automobility.client.lighting.VehicleLampSnapshot;
import io.github.foundationgames.automobility.client.lighting.VehicleSmallLampRenderer;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.client.dashboard.VehicleCabinDashboard;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
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
    protected AABB getBoundingBoxForCulling(AutomobileEntity entity, float tickDelta) {
        var bounds = VehicleSmallLampRenderer.cullingBounds(entity, tickDelta, VehicleHeadlightRenderer.cullingBounds(entity, tickDelta));
        if (!entity.hasOpeningHood()) return bounds;
        var state = headlightCullingState(entity, tickDelta);
        if (state == null) return bounds.minmax(entity.getBoundingBoxForCulling().inflate(3));
        var auto = state.automobile;
        float shake = auto.engineRunning() ? (float)(Math.cos((auto.getTime() + tickDelta) * 2.7) / 156) : 0;
        var chassis = HeadlightLampGeometry.chassis(state.verticalOffset, state.angularRotation, state.yaw,
                auto.getWheels().model().radius(), auto.getSuspensionBounce(tickDelta) * .048f, shake);
        return bounds.minmax(Mx5HoodGeometry.cullingBounds(entity.getBoundingBoxForCulling(), chassis,
                new net.minecraft.world.phys.Vec3(state.x, state.y, state.z)));
    }

    @Override
    public boolean shouldRender(AutomobileEntity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ, float tickDelta) {
        if ((entity.getProjectionLightMode() != VehicleLightMode.OFF || entity.isHoodOpen() || VehicleSmallLampRenderer.hasPotentialLight(entity))
                && !entity.getFrame().isEmpty()) {
            // Entity.shouldRender uses the body's size for distance; distant headlights need their own bounds.
            return entity.distanceToSqr(cameraX, cameraY, cameraZ) <= 96 * 96
                    && frustum.isVisible(getBoundingBoxForCulling(entity, tickDelta).inflate(0.5));
        }
        return super.shouldRender(entity, frustum, cameraX, cameraY, cameraZ, tickDelta);
    }

    @Override
    public void extractRenderState(AutomobileEntity entity, State state, float tickDelta) {
        super.extractRenderState(entity, state, tickDelta);
        extractChassisState(entity, state, tickDelta);
        state.cabinDashboard = VehicleCabinDashboard.capture(entity, state);
    }

    /**
     * Minimal exact render pose for headlight culling. Passenger interpolation has additional
     * renderer offsets; keep the old orientation-independent bound for that uncommon case.
     */
    public static State headlightCullingState(AutomobileEntity entity, float tickDelta) {
        if (entity.isPassenger() || entity.getFrame().isEmpty()
                || !Float.isFinite(tickDelta) || tickDelta < 0 || tickDelta > 1) return null;
        State state = new State();
        state.x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
        state.y = Mth.lerp(tickDelta, entity.yOld, entity.getY());
        state.z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
        extractChassisState(entity, state, tickDelta);
        if (!Double.isFinite(state.x) || !Double.isFinite(state.y) || !Double.isFinite(state.z)
                || !Float.isFinite(state.yaw) || !Float.isFinite(state.verticalOffset)
                || !Float.isFinite(state.angularRotation.x()) || !Float.isFinite(state.angularRotation.y())
                || !Float.isFinite(state.angularRotation.z()) || !Float.isFinite(state.angularRotation.w())) return null;
        return state;
    }

    private static void extractChassisState(AutomobileEntity entity, State state, float tickDelta) {
        state.automobile = entity;
        state.tickDelta = tickDelta;
        state.yaw = entity.getAutomobileYaw(tickDelta);
        state.mx5VisualPose = entity.getMx5VisualPose(tickDelta);
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

        VehicleHeadlightRenderer.submit(state, pose, submitter);
        VehicleLampSnapshot lamps = VehicleLampSnapshot.capture(state.automobile);
        VehicleHeadlightLampRenderer.submit(state, pose, submitter, lamps);
        VehicleSmallLampRenderer.submit(state, pose, submitter, lamps);
        VehicleCabinDashboard.submit(state.cabinDashboard, pose, submitter);

        pose.pushPose();
        pose.translate(0, state.verticalOffset, 0);
        pose.rotate(state.angularRotation);
        AutomobileRenderer.render(pose, submitter, state.lightCoords, OverlayTexture.NO_OVERLAY, state.tickDelta, state.automobile);

        pose.popPose();
    }

    public static class State extends EntityRenderState {
        public VehicleCabinDashboard.Captured cabinDashboard;
        public final Quaternionf angularRotation = new Quaternionf();
        public AutomobileEntity automobile;
        public float tickDelta;
        public float yaw;
        public float verticalOffset;
        public Mx5VisualPose mx5VisualPose = Mx5VisualPose.CLOSED;
    }
}
