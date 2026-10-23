package io.github.foundationgames.automobility.client.lighting.direct.bootstrap;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.objectweb.asm.tree.ClassNode;
import java.util.List;
import java.util.Set;
public final class DirectMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage){}
    public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String target,String mixin){return DirectGate.installation().allowed()&&
        (mixin.equals("io.github.foundationgames.automobility.client.lighting.direct.mixin.DirectBufferAccess")||mixin.equals("io.github.foundationgames.automobility.client.lighting.direct.mixin.DirectEmitMixin"));}
    public void acceptTargets(Set<String> mine,Set<String> others){}
    public List<String> getMixins(){return DirectGate.installation().allowed()?List.of("DirectBufferAccess","DirectEmitMixin"):List.of();}
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
