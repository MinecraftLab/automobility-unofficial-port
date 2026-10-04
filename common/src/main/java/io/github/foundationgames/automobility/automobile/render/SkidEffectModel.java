package io.github.foundationgames.automobility.automobile.render;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.model.ModelDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

public class SkidEffectModel extends BaseModel {
    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(Automobility.rl("automobile_skid_effect"), "main");

    public static final Identifier[] COOL_SPARK_TEXTURES = new Identifier[] {
            Automobility.rl("textures/entity/automobile/skid_effect/skid_cool_sparks_0.png"),
            Automobility.rl("textures/entity/automobile/skid_effect/skid_cool_sparks_1.png"),
            Automobility.rl("textures/entity/automobile/skid_effect/skid_cool_sparks_2.png")
    };
    public static final Identifier[] HOT_SPARK_TEXTURES = new Identifier[] {
            Automobility.rl("textures/entity/automobile/skid_effect/skid_hot_sparks_0.png"),
            Automobility.rl("textures/entity/automobile/skid_effect/skid_hot_sparks_1.png"),
            Automobility.rl("textures/entity/automobile/skid_effect/skid_hot_sparks_2.png")
    };
    public static final Identifier[] FLAME_TEXTURES = new Identifier[] {
            Automobility.rl("textures/entity/automobile/skid_effect/skid_flames_0.png"),
            Automobility.rl("textures/entity/automobile/skid_effect/skid_flames_1.png"),
            Automobility.rl("textures/entity/automobile/skid_effect/skid_flames_2.png")
    };
    public static final Identifier[] DEBRIS_TEXTURES = new Identifier[] {
            Automobility.rl("textures/entity/automobile/skid_effect/skid_debris_0.png"),
            Automobility.rl("textures/entity/automobile/skid_effect/skid_debris_1.png"),
            Automobility.rl("textures/entity/automobile/skid_effect/skid_debris_2.png")
    };

    public SkidEffectModel(EntityRendererProvider.Context ctx) {
        super(ctx, ModelDefinition.RenderMaterial.CUTOUT, MODEL_LAYER,
                new Vector3f(), new Vector3f(), new Vector3f(1));
    }
}
