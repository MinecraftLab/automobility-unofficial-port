package io.github.foundationgames.automobility.client.lighting.direct.mixin;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value=BufferBuilder.class,remap=false)
public interface DirectBufferAccess {
    @Accessor("building") boolean automobility$directBuilding();
    @Accessor("format") VertexFormat automobility$directFormat();
    @Accessor("primitiveTopology") PrimitiveTopology automobility$directTopology();
    @Accessor("vertices") int automobility$directVertices();
    @Invoker("endLastVertex") void automobility$directFinishVertex();
}
