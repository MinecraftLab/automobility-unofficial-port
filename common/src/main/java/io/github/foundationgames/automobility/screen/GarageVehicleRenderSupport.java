package io.github.foundationgames.automobility.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.light.Mx5HoodGeometry;
import io.github.foundationgames.automobility.automobile.light.Mx5PopupGeometry;
import io.github.foundationgames.automobility.automobile.render.AutomobileModels;
import io.github.foundationgames.automobility.automobile.render.AutomobileRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** One production draw path and a bounded CPU geometry measurement shared by both GUI uses. */
final class GarageVehicleRenderSupport {
    private GarageVehiclePreviewState.Parts measuredParts;
    private List<Model<?>> measuredModels;
    private GarageVehiclePreviewFit.Bounds measuredBounds;

    static void render(GarageVehiclePreviewState.Parts parts, PoseStack pose, SubmitNodeCollector submitter,
                       float scale, float yawDegrees, float originX, float originY, float originZ) {
        Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        pose.pushPose();
        try {
            pose.scale(scale, scale, scale);
            // PiP's positive screen Y and inverted Z require a negative pitch to see the roof.
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            pose.mulPose(Axis.XP.rotationDegrees(GarageVehiclePreviewFit.PITCH_DEGREES));
            pose.mulPose(Axis.YP.rotationDegrees(yawDegrees));
            pose.translate(-originX, -originY, -originZ);
            AutomobileRenderer.render(pose, submitter, LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, 0, parts);
        } finally {
            pose.popPose();
        }
    }

    /** Model identities change on resource bake; no stale texture or GPU resource is retained here. */
    static List<Model<?>> bakedModels(GarageVehiclePreviewState.Parts parts) {
        return Collections.unmodifiableList(Arrays.<Model<?>>asList(
                AutomobileModels.getModel(parts.frame().model().modelId()),
                AutomobileModels.getModel(parts.wheel().model().modelId()),
                AutomobileModels.getModel(parts.engine().model().modelId()),
                AutomobileModels.getModel(parts.rearType().model().modelId()),
                AutomobileModels.getModel(parts.frontType().model().modelId()),
                AutomobileModels.getModel(Mx5PopupGeometry.LEFT_MODEL),
                AutomobileModels.getModel(Mx5PopupGeometry.RIGHT_MODEL),
                AutomobileModels.getModel(Mx5HoodGeometry.MODEL),
                AutomobileModels.getModel(Automobility.rl("frame/motorcar_window"))));
    }

    GarageVehiclePreviewFit.Bounds measure(GarageVehiclePreviewState.Parts parts) {
        return measure(parts, bakedModels(parts));
    }

    GarageVehiclePreviewFit.Bounds measure(GarageVehiclePreviewState.Parts parts, List<Model<?>> models) {
        if (measuredBounds == null || !parts.equals(measuredParts) || !models.equals(measuredModels)) {
            var collector = new BoundsCollector();
            AutomobileRenderer.render(new PoseStack(), collector, LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, 0, parts);
            var result = collector.vertices.bounds();
            if (!result.valid()) return result;
            measuredBounds = result;
            measuredParts = parts;
            measuredModels = models;
        }
        return measuredBounds;
    }

    /** Only the production renderer decides part placement and model transforms. */
    private static final class BoundsCollector extends SubmitNodeStorage {
        private final BoundsVertices vertices = new BoundsVertices();

        @Override
        public <S> void submitModel(Model<? super S> model, S state, PoseStack pose, RenderType type,
                                    int light, int overlay, int color, @Nullable TextureAtlasSprite sprite,
                                    int outlineColor, @Nullable ModelFeatureRenderer.CrumblingOverlay crumbling) {
            model.setupAnim(state);
            model.renderToBuffer(pose, vertices, light, overlay, color);
        }

        @Override
        public void submitCustomGeometry(PoseStack pose, RenderType type,
                                         SubmitNodeCollector.CustomGeometryRenderer renderer) {
            renderer.render(pose.last(), vertices);
        }
    }

    private static final class BoundsVertices implements VertexConsumer {
        private float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

        GarageVehiclePreviewFit.Bounds bounds() {
            return new GarageVehiclePreviewFit.Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)) {
                minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            }
            return this;
        }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) { return this; }
        @Override public VertexConsumer setColor(int color) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
}
