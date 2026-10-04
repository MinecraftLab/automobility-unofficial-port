package io.github.foundationgames.automobility.automobile.render.attachment.front;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.attachment.front.AutopilotFrontAttachment;
import io.github.foundationgames.automobility.automobile.attachment.front.FrontAttachment;
import io.github.foundationgames.automobility.automobile.model.ModelDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class AutopilotFrontAttachmentModel extends FrontAttachmentRenderModel {
    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(Automobility.rl("automobile/front_attachment/autopilot"), "main");

    public static final int LIGHT_OFF = 0x0a0600;
    public static final int GLOW_OFF = 0x000000;

    private final ModelPart light;
    private final ModelPart glow;

    private int lightColor = LIGHT_OFF;
    private int glowColor = GLOW_OFF;
    private boolean on = false;

    public AutopilotFrontAttachmentModel(EntityRendererProvider.Context ctx,
                                         ModelDefinition.RenderMaterial material,
                                         ModelLayerLocation layer,
                                         Vector3f translation, Vector3f rotation, Vector3f scale) {
        super(ctx, material, layer, translation, rotation, scale);
        this.light = getChildSafe(this.main, "light");
        this.glow = getChildSafe(this.main, "glow");
    }

    @Override
    public void setDefaultState(float tickDelta) {
        super.setDefaultState(tickDelta);

        this.lightColor = LIGHT_OFF;
        this.glowColor = GLOW_OFF;
        this.on = false;

        if (this.light != PART_EMPTY) {
            this.light.visible = true;
        }
        if (this.glow != PART_EMPTY) {
            this.glow.visible = false;
        }
    }

    @Override
    public void setRenderState(@Nullable FrontAttachment attachment, float groundHeight, float tickDelta) {
        super.setRenderState(attachment, groundHeight, tickDelta);

        if (this.light != PART_EMPTY) {
            this.light.visible = true;
        }
        if (this.glow != PART_EMPTY) {
            this.glow.visible = false;
        }
        if (attachment instanceof AutopilotFrontAttachment att) {
            var state = att.getState();

            on = false;
            if (state.flashPeriod > 0) {
                int flashTime = att.getAnimationTimer() % (state.flashPeriod * 2);

                if ((flashTime / state.flashPeriod) == 0) {
                    on = true;
                    if (state.flashSubPeriod > 0) {
                        int subFlashTime = att.getAnimationTimer() % (state.flashSubPeriod * 2);

                        if ((subFlashTime / state.flashSubPeriod) == 1) {
                            on = false;
                        }
                    }
                }
            } else {
                on = true;
            }

            if (on) {
                this.lightColor = state.lightColor;
                this.glowColor = state.glowColor;
            } else {
                this.lightColor = LIGHT_OFF;
                this.glowColor = GLOW_OFF;
            }
        } else {
            this.on = false;
        }
    }
}
