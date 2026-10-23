package io.github.foundationgames.automobility.automobile.render.obj;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.foundationgames.automobility.automobile.model.ModelDefinition;
import io.github.foundationgames.automobility.automobile.render.BaseModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector3f;

import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

public class ObjModel extends Model<BaseModel.RenderState> {
    private final Vector3f translation;
    private final Vector3f rotation;
    private final Vector3f scale;

    private final Supplier<BakedObj> obj;
    private final ObjDrawPlan.Cache drawPlans = new ObjDrawPlan.Cache();

    public ObjModel(EntityRendererProvider.Context ctx,
                     ModelDefinition.RenderMaterial material,
                     ModelLayerLocation layer,
                     Vector3f translation, Vector3f rotation, Vector3f scale) {
        super(BaseModel.PART_EMPTY, material.renderType);
        this.translation = translation;
        this.rotation = rotation;
        this.scale = scale;

        this.obj = ObjLoader.INSTANCE.getObj(layer);
    }

    public void submit(PoseStack poseStack, SubmitNodeCollector submitter, RenderType renderType, int packedLight, int packedOverlay, int color) {
        submit(poseStack, submitter, renderType, packedLight, packedOverlay, color, false);
    }

    public void submit(PoseStack poseStack, SubmitNodeCollector submitter, RenderType renderType,
                       int packedLight, int packedOverlay, int color, boolean allowRetained) {
        var obj = this.obj.get();
        if (obj == null) {
            return;
        }

        poseStack.pushPose();

        poseStack.translate(translation.x(), translation.y(), translation.z());
        poseStack.mulPose(Axis.ZP.rotationDegrees(rotation.z()));
        poseStack.mulPose(Axis.XP.rotationDegrees(rotation.x()));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation.y()));
        poseStack.scale(scale.x(), scale.y(), scale.z());
        if (allowRetained && RetainedBodyScope.identityDefinition(translation, rotation, scale)
                && RetainedObjSubmission.enabled() && RetainedObjSubmission.trySubmit(obj, poseStack.last(), submitter,
                renderType, packedLight, packedOverlay, color, renderer(obj, packedLight, packedOverlay, color))) {
            poseStack.popPose();
            return;
        }
        if (ObjDrawCompatibility.permits(renderType)) {
            var plan = drawPlans.forBake(obj);
            submitter.submitCustomGeometry(poseStack, renderType,
                    renderer(obj, plan, () -> ObjDrawCompatibility.permits(renderType), packedLight, packedOverlay, color));
        } else {
            // No first-draw plan compilation for shader/custom-pipeline fallback.
            submitter.submitCustomGeometry(poseStack, renderType, renderer(obj, packedLight, packedOverlay, color));
        }
        poseStack.popPose();
    }

    static SubmitNodeCollector.CustomGeometryRenderer renderer(BakedObj obj, int light, int overlay, int color) {
        return (pose, buffer) -> renderObj(obj, pose, buffer, light, overlay, color);
    }

    static SubmitNodeCollector.CustomGeometryRenderer renderer(BakedObj obj, ObjDrawPlan plan,
            BooleanSupplier compatible, int light, int overlay, int color) {
        return (pose, buffer) -> renderObj(obj, plan.hasPairs() && compatible.getAsBoolean() ? plan : null,
                pose, buffer, light, overlay, color);
    }

    static void renderObj(BakedObj obj, PoseStack.Pose pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        renderObj(obj, null, pose, buffer, packedLight, packedOverlay, color);
    }

    private static void renderObj(BakedObj obj, ObjDrawPlan plan, PoseStack.Pose pose, VertexConsumer buffer,
                                  int packedLight, int packedOverlay, int color) {
        // Scratch belongs to this callback, never to the shared model or another submission.
        var vtx = new Vector3f();
        var nml = new Vector3f();

        int fi = 0;
        int count = plan == null ? obj.idVertex().length : plan.cornerCount();
        for (int step = 0; step < count; step++) {
            int i = plan == null ? step : plan.corner(step);
            int iv = obj.idVertex()[i] * 3;
            int in = obj.idNormal()[i] * 3;
            int iu = obj.idUv()[i] * 2;

            pose.pose().transformPosition(obj.vertex()[iv], obj.vertex()[iv + 1], obj.vertex()[iv + 2], vtx);
            // Retain Pose's trusted-normal/normalization policy, including mirrored scales.
            pose.transformNormal(obj.normal()[in], obj.normal()[in + 1], obj.normal()[in + 2], nml);
            float u = (float)(double)obj.uv()[iu];
            float v = (float)(1.0 - obj.uv()[iu + 1]);

            int r = 1;
            if (plan == null && ++fi >= 3) {
                r = 2;
                fi = 0;
            }

            for (int j = 0; j < r; j++) {
                // Fallback retains the exact old stream. Compatible paired quads retain the
                // same oriented triangles, with cyclic vertex order and fewer duplicate writes.
                buffer.addVertex(vtx.x(), vtx.y(), vtx.z(), color, u, v,
                        packedOverlay, packedLight, nml.x(), nml.y(), nml.z());
            }
        }
    }
}
