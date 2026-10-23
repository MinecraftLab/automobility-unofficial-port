package io.github.foundationgames.automobility.mixin;
import io.github.foundationgames.automobility.entity.*;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Entity.class)
public abstract class GarageFencedRemovalMixin {
 @Inject(method="setRemoved",at=@At("HEAD"),cancellable=true)
 private void automobility$garageFencedRemoval(Entity.RemovalReason reason,CallbackInfo ci){if((Object)this instanceof AutomobileEntity car&&!GarageTransferRuntime.mayRemove(car,reason))ci.cancel();}
 @Inject(method="setRemoved",at=@At("TAIL"))
 private void automobility$garageReleaseRemovedBinding(Entity.RemovalReason reason,CallbackInfo ci){if((Object)this instanceof AutomobileEntity car&&car.isRemoved())GarageTransferRuntime.unload(car);}
}
