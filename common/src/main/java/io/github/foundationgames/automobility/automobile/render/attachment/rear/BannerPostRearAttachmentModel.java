package io.github.foundationgames.automobility.automobile.render.attachment.rear;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.attachment.rear.RearAttachment;
import io.github.foundationgames.automobility.automobile.model.ModelDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class BannerPostRearAttachmentModel extends RearAttachmentRenderModel {
    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(Automobility.rl("automobile/rear_attachment/banner_post"), "main");

    private final ModelPart fakePole;
    private final ModelPart pole;
    private final ModelPart bar;

    private final ModelPart flagPole;
    private final ModelPart flagBar;
    private final ModelPart flag;

    public BannerPostRearAttachmentModel(EntityRendererProvider.Context ctx,
                                         ModelDefinition.RenderMaterial material,
                                         ModelLayerLocation layer,
                                         Vector3f translation, Vector3f rotation, Vector3f scale) {
        super(ctx, material, layer, translation, rotation, scale);

        this.fakePole = getChildSafe(this.main, "fake_pole");
        this.pole = getChildSafe(this.main, "pole");
        this.bar = getChildSafe(this.pole, "bar");

        this.flagPole = getChildSafe(this.main, "flag_pole");
        this.flagBar = getChildSafe(this.flagPole, "flag_bar");
        this.flag = getChildSafe(this.flagBar, "flag");

        if (this.flagPole != PART_EMPTY) {
            this.flagPole.visible = false;
        }
        if (this.pole != PART_EMPTY) {
            this.pole.visible = false;
        }
    }

    @Override
    public void setDefaultState(float tickDelta) {
        super.setDefaultState(tickDelta);

        if (this.fakePole != PART_EMPTY) {
            this.fakePole.visible = true;
        }
        if (this.pole != PART_EMPTY) {
            this.pole.visible = false;
        }
        if (this.flagPole != PART_EMPTY) {
            this.flagPole.visible = false;
        }
    }

    @Override
    public void setRenderState(@Nullable RearAttachment attachment, float wheelAngle, float tickDelta) {
        super.setRenderState(attachment, wheelAngle, tickDelta);

        float push = attachment == null ? 0 : (float) Math.pow(Math.max(0, attachment.automobile().getHSpeed() * 0.368f), 2);
        if (this.pole != PART_EMPTY) {
            this.pole.xRot = -push;
        }
        if (this.bar != PART_EMPTY) {
            this.bar.xRot = push;
        }
        if (this.flagPole != PART_EMPTY) {
            this.flagPole.xRot = -push;
        }
        if (this.flagBar != PART_EMPTY) {
            this.flagBar.xRot = push;
        }

        if (attachment != null && this.flag != PART_EMPTY) {
            this.flag.setRotation(push, this.flag.yRot, 0.05f * (float)Math.sin((attachment.automobile().getTime() + tickDelta) / 20));
        }

        if (this.fakePole != PART_EMPTY) {
            this.fakePole.visible = attachment == null;
        }
        if (this.pole != PART_EMPTY) {
            this.pole.visible = attachment != null;
        }
        if (this.flagPole != PART_EMPTY) {
            this.flagPole.visible = false;
        }
    }
}
