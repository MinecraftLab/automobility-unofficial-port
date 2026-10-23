package io.github.foundationgames.automobility.automobile.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.automobile.WheelBase;
import io.github.foundationgames.automobility.automobile.light.Mx5VisualPose;
import io.github.foundationgames.automobility.automobile.light.Mx5PopupGeometry;
import io.github.foundationgames.automobility.automobile.light.Mx5HoodGeometry;
import io.github.foundationgames.automobility.automobile.render.obj.ObjModel;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.util.AUtils;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public enum AutomobileRenderer {;
    private static final boolean RETAINED_WHEELS = Boolean.parseBoolean(System.getProperty("automobility.experimental.retainedWheels", "true"));
    private static final Identifier MOTORCAR_FRAME_MODEL = Automobility.rl("frame/motorcar");
    private static final Identifier MOTORCAR_WINDOW_MODEL = Automobility.rl("frame/motorcar_window");

    public static void render(
            PoseStack pose, SubmitNodeCollector submitter, int light, int overlay,
            float tickDelta, RenderableAutomobile automobile
    ) {
        // Keep the original entry and getEngine invocation sites for RIA's redirect.
        // Hinge sampling is read-only at the same partial tick used by the lamp snapshot.
        var visual = automobile instanceof AutomobileEntity entity
                ? entity.getMx5VisualPose(tickDelta) : Mx5VisualPose.CLOSED;
        var frame = automobile.getFrame();
        var wheels = automobile.getWheels();
        var engine = automobile.getEngine();

        var skidEffectModel = AutomobileModels.getSkidEffectModel();
        var exhaustFumesModel = AutomobileModels.getExhaustFumesModel();

        pose.pushPose();

        pose.rotate(Axis.ZP.rotationDegrees(180));
        pose.rotate(Axis.YP.rotationDegrees(automobile.getAutomobileYaw(tickDelta) + 180));

        float chassisRaise = wheels.model().radius() / 16;
        float bounce = automobile.getSuspensionBounce(tickDelta) * 0.048f;

        var frameModel = AutomobileModels.getModel(automobile.getFrame().model().modelId());
        var wheelModel = AutomobileModels.getModel(automobile.getWheels().model().modelId());
        var engineModel = AutomobileModels.getModel(automobile.getEngine().model().modelId());
        var rearAttachmentModel = AutomobileModels.getModel(automobile.getRearAttachmentType().model().modelId());
        var frontAttachmentModel = AutomobileModels.getModel(automobile.getFrontAttachmentType().model().modelId());

        pose.translate(0, -chassisRaise, 0);

        // Frame, engine, exhaust
        pose.pushPose();

        pose.translate(0, bounce + (automobile.engineRunning() ? (Math.cos((automobile.getTime() + tickDelta) * 2.7) / 156) : 0), 0);
        var frameTexture = frame.model().texture();
        var engineTexture = engine.model().texture();
        if (!frame.isEmpty() && frameModel != null) {
            if (automobile instanceof AutomobileEntity
                    && io.github.foundationgames.automobility.automobile.render.obj.RetainedBodyScope.supports(frame.model().modelId())
                    && frameModel instanceof ObjModel obj) {
                obj.submit(pose, submitter, obj.renderType(frameTexture), light, overlay, 0xFFFFFFFF, true);
            } else {
                submit(frameModel, state(tickDelta), pose, submitter, frameTexture, light, overlay, 0xFFFFFFFF);
            }
            if (MOTORCAR_FRAME_MODEL.equals(frame.model().modelId())) {
                var windowModel = AutomobileModels.getModel(MOTORCAR_WINDOW_MODEL);
                if (windowModel != null) {
                    submit(windowModel, state(tickDelta), pose, submitter, frameTexture, light, overlay, 0xFFFFFFFF);
                }
            }
        }

        if (Mx5PopupGeometry.appliesTo(frame.model().modelId())) {
            hingedPart(Mx5PopupGeometry.LEFT_MODEL, Mx5PopupGeometry.PIVOT,
                    Mx5PopupGeometry.angleDegrees(visual.leftPopup()), pose, submitter, frameTexture, light, overlay, tickDelta);
            hingedPart(Mx5PopupGeometry.RIGHT_MODEL, Mx5PopupGeometry.PIVOT,
                    Mx5PopupGeometry.angleDegrees(visual.rightPopup()), pose, submitter, frameTexture, light, overlay, tickDelta);
            hingedPart(Mx5HoodGeometry.MODEL, Mx5HoodGeometry.PIVOT,
                    Mx5HoodGeometry.angleDegrees(visual.hood()), pose, submitter, frameTexture, light, overlay, tickDelta);
        }

        // An electric/addon engine with no exhaust must not inherit combustion effects.
        var frameExhaust = NewVehicleExhausts.frameOutlet(frame.model().modelId(), !engine.model().exhausts().isEmpty());
        if (frameExhaust != null) pose.pushPose();
        var ePos = frame.model().enginePos().scale(1.0 / 16);
        pose.translate(ePos.x(), -ePos.y(), -ePos.z());
        pose.rotate(Axis.YP.rotationDegrees(180));
        if (!engine.isEmpty() && engineModel != null) {
            pose.pushPose();
            float scale = NewVehicleExhausts.engineScale(frame.model().modelId());
            pose.scale(scale, scale, scale);
            submit(engineModel, state(tickDelta), pose, submitter, engineTexture, light, overlay, 0xFFFFFFFF);
            pose.popPose();
        }
        if (frameExhaust != null) pose.popPose();

        Identifier[] exhaustTexes;
        RenderType exhaustRenderType = null;
        if (automobile.getBoostTimer() > 0) {
            exhaustTexes = ExhaustFumesModel.FLAME_TEXTURES;
            int index = (int)(automobile.getTime() % exhaustTexes.length);
            exhaustRenderType = RenderTypes.eyes(exhaustTexes[index]);
        } else if (automobile.engineRunning() && io.github.foundationgames.automobility.client.lighting.VehicleLightingView
                .graphics(net.minecraft.client.Minecraft.getInstance()).exhaustSmoke()) {
            exhaustTexes = ExhaustFumesModel.SMOKE_TEXTURES;
            int index = (int)Math.floor(((automobile.getTime() + tickDelta) / 1.5f) % exhaustTexes.length);
            exhaustRenderType = RenderTypes.entityTranslucent(exhaustTexes[index]);
        }
        if (exhaustRenderType != null) {
            if (frameExhaust != null) exhaust(frameExhaust, exhaustFumesModel, pose, submitter, exhaustRenderType, light, overlay, tickDelta);
            else for (AutomobileEngine.ExhaustPos exhaust : engine.model().exhausts())
                exhaust(exhaust, exhaustFumesModel, pose, submitter, exhaustRenderType, light, overlay, tickDelta);
        }
        pose.popPose();

        // Wheels
        var wPoses = frame.model().wheelBase().wheels();

        if (!wheels.isEmpty() && wheelModel != null) {
            float wheelAngle = automobile.getWheelAngle(tickDelta);
            int wheelCount = automobile.getWheelCount();

            for (var pos : wPoses) {
                if (wheelCount <= 0) {
                    break;
                }

                float scale = pos.scale();
                float wheelRadius = wheels.model().radius() - (wheels.model().radius() * (scale - 1));
                pose.pushPose();

                pose.translate(pos.right() / 16, wheelRadius / 16, -pos.forward() / 16);

                if (pos.end() == WheelBase.WheelEnd.FRONT) {
                    pose.rotate(Axis.YP.rotationDegrees(automobile.getSteering(tickDelta) * 27));
                }
                pose.translate(0, -chassisRaise, 0);
                pose.rotate(Axis.XP.rotationDegrees(wheelAngle));
                pose.scale(scale, scale, scale);

                pose.rotate(Axis.YP.rotationDegrees(180 + pos.yaw()));

                if (RETAINED_WHEELS && automobile instanceof AutomobileEntity
                        && io.github.foundationgames.automobility.automobile.render.obj.RetainedWheelScope.supports(wheels.model().modelId(), scale)
                        && wheelModel instanceof ObjModel obj) {
                    obj.submit(pose, submitter, obj.renderType(wheels.model().texture()), light, overlay, 0xFFFFFFFF, true);
                } else {
                    submit(wheelModel, state(tickDelta), pose, submitter, wheels.model().texture(), light, overlay, 0xFFFFFFFF);
                }

                pose.popPose();

                wheelCount--;
            }
        }

        // Rear attachment
        var rearAtt = automobile.getRearAttachmentType();
        if (!rearAtt.isEmpty() && rearAttachmentModel != null) {
            pose.pushPose();
            pose.translate(0, chassisRaise, frame.model().rearAttachmentPos() / 16);
            pose.rotate(Axis.YN.rotationDegrees(automobile.getAutomobileYaw(tickDelta) - automobile.getRearAttachmentYaw(tickDelta)));

            pose.translate(0, 0, rearAtt.model().pivotDistPx() / 16);
            submit(
                    rearAttachmentModel,
                    state(tickDelta).rear(automobile.getRearAttachment(), (float)Math.toRadians(automobile.getWheelAngle(tickDelta))),
                    pose, submitter, rearAtt.model().texture(), light, overlay, 0xFFFFFFFF
            );
            pose.popPose();
        }

        // Front attachment
        var frontAtt = automobile.getFrontAttachmentType();
        if (!frontAtt.isEmpty() && frontAttachmentModel != null) {
            pose.pushPose();
            pose.translate(0, 0, frame.model().frontAttachmentPos() / -16);

            submit(
                    frontAttachmentModel,
                    state(tickDelta).front(automobile.getFrontAttachment(), chassisRaise),
                    pose, submitter, frontAtt.model().texture(), light, overlay, 0xFFFFFFFF
            );
            pose.popPose();
        }

        // Skid effects
        if ((automobile.getTurboCharge() > AutomobileEntity.SMALL_TURBO_TIME || automobile.debris()) && automobile.automobileOnGround()) {
            var skidTexes = SkidEffectModel.COOL_SPARK_TEXTURES;
            boolean bright = true;
            float r = 1;
            float g = 1;
            float b = 1;
            if (automobile.getTurboCharge() > AutomobileEntity.LARGE_TURBO_TIME) {
                skidTexes = SkidEffectModel.FLAME_TEXTURES;
            } else if (automobile.getTurboCharge() > AutomobileEntity.MEDIUM_TURBO_TIME) {
                skidTexes = SkidEffectModel.HOT_SPARK_TEXTURES;
            } else if (automobile.debris()) {
                skidTexes = SkidEffectModel.DEBRIS_TEXTURES;
                var c = automobile.debrisColor();
                r = c.x() * 0.85f;
                g = c.y() * 0.85f;
                b = c.z() * 0.85f;
                bright = false;
            }
            int index = (int)Math.floor(((automobile.getTime() + tickDelta) / 1.5f) % skidTexes.length);
            var skidEffectRenderType = bright ? RenderTypes.eyes(skidTexes[index]) : RenderTypes.entityCutout(skidTexes[index]);

            for (var pos : wPoses) {
                if (pos.end() == WheelBase.WheelEnd.BACK) {
                    float scale = pos.scale();
                    float heightOffset = wheels.model().radius();
                    float wheelRadius = wheels.model().radius() * scale;
                    float wheelWidth =  (wheels.model().width() / 16) * scale;
                    float back = (wheelRadius > 2) ? (float)(Math.sqrt((wheelRadius * wheelRadius) - Math.pow(wheelRadius - 2, 2)) - 0.85) / 16 : 0.08f;
                    pose.pushPose();

                    float[] skids;
                    switch (pos.side()) {
                        case RIGHT -> skids = new float[] {1};
                        case LEFT -> skids = new float[] {-1};
                        default -> skids = new float[] {-1, 1};
                    }

                    for (float s : skids) {
                        pose.pushPose();
                        pose.translate((pos.right() / 16) + (wheelWidth * s), heightOffset / 16, (-pos.forward() / 16) + back);
                        pose.scale(s, 1, -1);
                        submit(skidEffectModel, state(tickDelta), pose, submitter, skidEffectRenderType, light, overlay, AUtils.colorToInt(0.6f, r, g, b));
                        pose.popPose();
                    }
                    pose.popPose();
                }
            }
        }

        pose.popPose();
    }

    private static BaseModel.RenderState state(float tickDelta) {
        return new BaseModel.RenderState(tickDelta);
    }

    private static void hingedPart(Identifier id, net.minecraft.world.phys.Vec3 pivot, float angle,
                                   PoseStack pose, SubmitNodeCollector submitter, Identifier texture,
                                   int light, int overlay, float tickDelta) {
        var model = AutomobileModels.getModelOrNull(id);
        if (model == null) return;
        pose.pushPose();
        pose.translate(pivot.x, pivot.y, pivot.z);
        pose.rotate(Axis.XP.rotationDegrees(angle));
        pose.translate(-pivot.x, -pivot.y, -pivot.z);
        submit(model, state(tickDelta), pose, submitter, texture, light, overlay, 0xFFFFFFFF);
        pose.popPose();
    }

    private static void exhaust(AutomobileEngine.ExhaustPos outlet, Model<?> model, PoseStack pose,
                                SubmitNodeCollector submitter, RenderType type, int light, int overlay, float delta) {
        pose.pushPose();
        pose.translate(outlet.x() / 16, -outlet.y() / 16, outlet.z() / 16);
        pose.rotate(Axis.YP.rotationDegrees(outlet.yaw()));
        pose.rotate(Axis.XP.rotationDegrees(outlet.pitch()));
        submit(model, state(delta), pose, submitter, type, light, overlay, 0xFFFFFFFF);
        pose.popPose();
    }

    private static void submit(Model<?> model, BaseModel.RenderState state, PoseStack pose, SubmitNodeCollector submitter,
                               Identifier texture, int light, int overlay, int color) {
        if (model instanceof BaseModel base) {
            submitter.submitModel(base, state, pose, base.renderType(texture), light, overlay, color, null, 0);
        } else if (model instanceof ObjModel obj) {
            obj.submit(pose, submitter, obj.renderType(texture), light, overlay, color);
        }
    }

    private static void submit(Model<?> model, BaseModel.RenderState state, PoseStack pose, SubmitNodeCollector submitter,
                               RenderType renderType, int light, int overlay, int color) {
        if (model instanceof BaseModel base) {
            submitter.submitModel(base, state, pose, renderType, light, overlay, color, null, 0);
        } else if (model instanceof ObjModel obj) {
            obj.submit(pose, submitter, renderType, light, overlay, color);
        }
    }
}
