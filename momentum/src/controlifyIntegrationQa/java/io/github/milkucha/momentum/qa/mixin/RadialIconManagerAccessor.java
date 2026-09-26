package io.github.milkucha.momentum.qa.mixin;

import dev.isxander.controlify.api.bind.RadialIcon;
import dev.isxander.controlify.bindings.RadialIconManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Read-only accessor, present exclusively in the opt-in QA artifact. */
@Mixin(value = RadialIconManager.class, remap = false)
public interface RadialIconManagerAccessor {
    @Accessor("iconsByBinding")
    Map<Identifier, RadialIcon> momentumQa$appliedIcons();
}
