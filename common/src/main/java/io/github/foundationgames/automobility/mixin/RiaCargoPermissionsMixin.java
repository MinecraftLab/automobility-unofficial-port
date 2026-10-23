package io.github.foundationgames.automobility.mixin;

import io.github.foundationgames.automobility.automobile.access.VehicleInventoryAccess;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.util.duck.VehicleCargoAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional RIA boundary: no addon classes, dependency, reflection or storage replacement. */
@Pseudo
@Mixin(targets="com.sshakusora.riautomobility.content.LorryCargoContainer", remap=false)
public abstract class RiaCargoPermissionsMixin implements VehicleCargoAccess {
    @Shadow @Final private AutomobileEntity owner;

    @Override public boolean automobility$mayUseCargo(Player player) {
        return VehicleInventoryAccess.mayUse(owner, player) && owner.underlyingContainer() == (Object)this;
    }

    @Inject(method="stillValid", at=@At("HEAD"), cancellable=true)
    private void automobility$denyCargo(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!automobility$mayUseCargo(player)) cir.setReturnValue(false);
    }

    @Inject(method="createMenu", at=@At("HEAD"), cancellable=true)
    private void automobility$denyMenu(int id, Inventory inventory, Player player,
                                      CallbackInfoReturnable<AbstractContainerMenu> cir) {
        if (!automobility$mayUseCargo(player)) cir.setReturnValue(null);
    }
}
