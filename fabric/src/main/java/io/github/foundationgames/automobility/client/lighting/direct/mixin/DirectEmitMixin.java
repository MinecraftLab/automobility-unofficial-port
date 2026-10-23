package io.github.foundationgames.automobility.client.lighting.direct.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import io.github.foundationgames.automobility.client.lighting.direct.DirectFeature;

@Mixin(targets="io.github.foundationgames.automobility.client.lighting.VehicleHeadlightRenderer",remap=false)
public abstract class DirectEmitMixin {
    @WrapMethod(method="emit")
    private static void emit(@Coerce Object batch,Vec3 origin,PoseStack.Pose pose,VertexConsumer buffer,Operation<Void> original){
        if(DirectFeature.enabled()&&DirectFeature.renderBatch(batch,buffer,out->original.call(batch,origin,pose,out)))return;
        original.call(batch,origin,pose,buffer);
    }
}
