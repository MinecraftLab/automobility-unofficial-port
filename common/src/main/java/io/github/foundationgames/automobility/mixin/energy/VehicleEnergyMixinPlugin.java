package io.github.foundationgames.automobility.mixin.energy;

import io.github.foundationgames.automobility.client.vehicle.energy.EnergyCompatibilityGate;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.objectweb.asm.tree.ClassNode;
import java.util.List;
import java.util.Set;

/** Loader-only plugin: the optional target is selected before loading any producer class. */
public final class VehicleEnergyMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String name) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target,String mixin) {
        return mixin.endsWith(".VehicleEnergyOverlayMixin")
                || mixin.endsWith(".PumpsFuelGaugeMixin") && EnergyCompatibilityGate.pumps();
    }
    public void acceptTargets(Set<String> own,Set<String> others) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
}
