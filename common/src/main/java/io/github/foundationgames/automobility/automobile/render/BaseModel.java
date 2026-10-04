package io.github.foundationgames.automobility.automobile.render;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.attachment.front.FrontAttachment;
import io.github.foundationgames.automobility.automobile.attachment.rear.RearAttachment;
import io.github.foundationgames.automobility.automobile.model.ModelDefinition;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class BaseModel extends Model<BaseModel.RenderState> {
    protected final ModelPart main;
    protected final Vector3f translation;
    protected final Vector3f rotation;
    protected final Vector3f scale;

    public static final ModelPart PART_EMPTY = new ModelPart(List.of(), Map.of());
    public static final Identifier TEXTURE_SOLID = Automobility.rl("textures/solid.png");

    public BaseModel(EntityRendererProvider.Context ctx,
                     ModelDefinition.RenderMaterial material,
                     ModelLayerLocation layer,
                     Vector3f translation, Vector3f rotation, Vector3f scale) {
        super(bakeRoot(ctx, layer), material.renderType);
        this.translation = translation;
        this.rotation = rotation;
        this.scale = scale;
        var main = getChildSafe(this.root, "main");
        this.main = main == PART_EMPTY ? this.root : main;
    }

    private static ModelPart bakeRoot(EntityRendererProvider.Context ctx, ModelLayerLocation layer) {
        return JsonEntityModelLoader.bakeLayer(layer).orElseGet(() -> ctx.bakeLayer(layer));
    }

    protected static ModelPart getChildSafe(ModelPart parent, String child) {
        try {
            return parent.getChild(child);
        } catch (NoSuchElementException ex) {
            return PART_EMPTY;
        }
    }

    @Override
    public void setupAnim(RenderState state) {
        this.resetPose();
        var renderState = state == null ? new RenderState(0) : state;
        this.applyBaseTransform();
        this.setDefaultState(renderState.tickDelta);
        this.applyState(renderState);
    }

    protected void applyBaseTransform() {
        this.root.x += translation.x() * 16;
        this.root.y += translation.y() * 16;
        this.root.z += translation.z() * 16;
        this.root.xRot += (float)Math.toRadians(rotation.x());
        this.root.yRot += (float)Math.toRadians(rotation.y());
        this.root.zRot += (float)Math.toRadians(rotation.z());
        this.root.xScale *= scale.x();
        this.root.yScale *= scale.y();
        this.root.zScale *= scale.z();
    }

    protected void applyState(RenderState state) {
    }

    public void setDefaultState(float tickDelta) {
    }

    public RenderState createDefaultState(float tickDelta) {
        return new RenderState(tickDelta);
    }

    public static class RenderState {
        public final float tickDelta;
        public @Nullable RearAttachment rearAttachment;
        public @Nullable FrontAttachment frontAttachment;
        public float wheelAngle;
        public float groundHeight;

        public RenderState(float tickDelta) {
            this.tickDelta = tickDelta;
        }

        public RenderState rear(@Nullable RearAttachment attachment, float wheelAngle) {
            this.rearAttachment = attachment;
            this.wheelAngle = wheelAngle;
            return this;
        }

        public RenderState front(@Nullable FrontAttachment attachment, float groundHeight) {
            this.frontAttachment = attachment;
            this.groundHeight = groundHeight;
            return this;
        }
    }
}
