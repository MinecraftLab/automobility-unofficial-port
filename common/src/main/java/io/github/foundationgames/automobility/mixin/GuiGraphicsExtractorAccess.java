package io.github.foundationgames.automobility.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Narrow access for submitting the garage's immutable picture-in-picture state. */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccess {
    @Accessor("guiRenderState")
    GuiRenderState automobility$garageRenderState();
}
