package io.github.foundationgames.automobility.automobile.render.attachment.front;

import io.github.foundationgames.automobility.automobile.attachment.front.FrontAttachment;
import io.github.foundationgames.automobility.automobile.model.ModelDefinition;
import io.github.foundationgames.automobility.automobile.render.BaseModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class FrontAttachmentRenderModel extends BaseModel {
    protected final ModelPart ground;

    public FrontAttachmentRenderModel(EntityRendererProvider.Context ctx,
                                      ModelDefinition.RenderMaterial material,
                                      ModelLayerLocation layer,
                                      Vector3f translation, Vector3f rotation, Vector3f scale) {
        super(ctx, material, layer, translation, rotation, scale);
        this.ground = getChildSafe(this.root, "ground");
    }

    @Override
    public void setDefaultState(float tickDelta) {
        super.setDefaultState(tickDelta);
    }

    public void setRenderState(@Nullable FrontAttachment attachment, float groundHeight, float tickDelta) {
        if (this.ground != PART_EMPTY) {
            this.ground.y += groundHeight * 16;
        }
    }

    @Override
    protected void applyState(RenderState state) {
        this.setRenderState(state.frontAttachment, state.groundHeight, state.tickDelta);
    }
}
