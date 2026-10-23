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
public abstract class CabinTuningPersistenceMixin027 implements PersistentCabinTuning027 {
    @Unique private Tag cabinTuning$preferences;
    @Unique private UUID cabinTuning$epoch=UUID.randomUUID();
    @Override public Tag cabinTuning$raw(){return cabinTuning$preferences==null?null:cabinTuning$preferences.copy();}
    @Override public UUID cabinTuning$incarnation(){return cabinTuning$epoch;}
    @Override public void cabinTuning$replace(CabinTuning027.Settings settings,CabinTuningServer027.Permit permit){
        byte[] encoded=CabinTuning027.encode(settings);permit.require((AutomobileEntity)(Object)this,settings);
        cabinTuning$preferences=new ByteArrayTag(encoded);
    }
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"),require=1)
    private void cabinTuning$readRaw(ValueInput input,CallbackInfo ci){var raw=input.read(KEY,Codec.PASSTHROUGH).orElse(null);cabinTuning$preferences=raw==null?null:raw.convert(NbtOps.INSTANCE).getValue().copy();cabinTuning$epoch=UUID.randomUUID();}
    @Inject(method="addAdditionalSaveData",at=@At("TAIL"),require=1)
    private void cabinTuning$saveRaw(ValueOutput output,CallbackInfo ci){if(cabinTuning$preferences!=null)output.store(KEY,Codec.PASSTHROUGH,new com.mojang.serialization.Dynamic<>(NbtOps.INSTANCE,cabinTuning$preferences.copy()));}
}
