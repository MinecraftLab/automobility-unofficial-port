package io.github.foundationgames.automobility.mixin.dashboard;

import com.mojang.serialization.*;
import io.github.foundationgames.automobility.api.dashboard.*;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.world.level.storage.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Quiet save copies the actual raw Tag, including absence and future/foreign data. */
@Mixin(AutomobileEntity.class)
public abstract class CabinPreferencesPersistenceMixin022 implements PersistentCabinPreferences022 {
    @Unique private Tag cabin$preferences;
    @Unique private UUID cabin$epoch=UUID.randomUUID();
    @Override public Tag cabin$raw(){return cabin$preferences==null?null:cabin$preferences.copy();}
    @Override public UUID cabin$incarnation(){return cabin$epoch;}
    @Override public void cabin$replace(CabinPreferences022.Settings settings,CabinSettingsServer022.Permit permit){
        byte[] encoded=CabinPreferences022.encode(settings);permit.require((AutomobileEntity)(Object)this,settings);
        cabin$preferences=new ByteArrayTag(encoded);
    }
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"),require=1)
    private void cabin$readRaw(ValueInput input,CallbackInfo ci){var raw=input.read(KEY,Codec.PASSTHROUGH).orElse(null);cabin$preferences=raw==null?null:raw.convert(NbtOps.INSTANCE).getValue().copy();cabin$epoch=UUID.randomUUID();}
    @Inject(method="addAdditionalSaveData",at=@At("TAIL"),require=1)
    private void cabin$saveRaw(ValueOutput output,CallbackInfo ci){if(cabin$preferences!=null)output.store(KEY,Codec.PASSTHROUGH,new com.mojang.serialization.Dynamic<>(NbtOps.INSTANCE,cabin$preferences.copy()));}
}
