package io.github.foundationgames.automobility.mixin;
import io.github.foundationgames.automobility.entity.GarageFailedOrigins;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.entity.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PersistentEntitySectionManager.class)
public abstract class GarageEntityManagerMixin<T extends EntityAccess> {
 @Shadow @Final private EntityPersistentStorage<T> permanentStorage;
 @Inject(method="getAllChunksToSave",at=@At("RETURN"),cancellable=true)
 private void automobility$garageBoundedFailedSaves(CallbackInfoReturnable<LongSet> cir){cir.setReturnValue(GarageFailedOrigins.filter(permanentStorage,cir.getReturnValue()));}
 @Inject(method="lambda$saveAll$0",at=@At("HEAD"),cancellable=true)
 private void automobility$garageFailureDuringFlush(long chunk,CallbackInfoReturnable<Boolean> cir){if(GarageFailedOrigins.failed(permanentStorage,chunk))cir.setReturnValue(true);}
}
