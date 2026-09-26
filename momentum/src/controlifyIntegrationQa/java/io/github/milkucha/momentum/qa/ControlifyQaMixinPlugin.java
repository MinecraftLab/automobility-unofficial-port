package io.github.milkucha.momentum.qa;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Avoid even targeting modern Controlify classes in absent/legacy matrix cells. */
public final class ControlifyQaMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return modernEnabled();
    }
    private boolean modernEnabled() {
        if (!(Boolean.getBoolean("automobility.qa.controlify") || "1".equals(System.getenv("MOMENTUM_CONTROLIFY_QA")))
                || !FabricLoader.getInstance().isModLoaded("controlify")) return false;
        // Presence detection without prematurely loading external classes during Mixin setup.
        return getClass().getClassLoader().getResource("dev/isxander/controlify/api/contextual/ContextualDomainRegistry.class") != null;
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return modernEnabled() ? List.of("RadialIconManagerAccessor") : List.of(); }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
