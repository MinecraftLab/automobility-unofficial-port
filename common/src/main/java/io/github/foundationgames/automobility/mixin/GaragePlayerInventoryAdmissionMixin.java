package io.github.foundationgames.automobility.mixin;
import io.github.foundationgames.automobility.entity.GarageTransferRuntime;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerPlayer.class)
public abstract class GaragePlayerInventoryAdmissionMixin {
    @Inject(method="initInventoryMenu",at=@At("HEAD"),cancellable=true)
    private void automobility$admitInventory(CallbackInfo ci){
        if(!GarageTransferRuntime.admitPlayerInventory((ServerPlayer)(Object)this))ci.cancel();
    }
}
