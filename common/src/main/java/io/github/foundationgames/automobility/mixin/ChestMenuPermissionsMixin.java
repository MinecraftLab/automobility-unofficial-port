package io.github.foundationgames.automobility.mixin;

import io.github.foundationgames.automobility.util.duck.VehicleCargoAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChestMenu.class)
public abstract class ChestMenuPermissionsMixin {
    @Inject(method="quickMoveStack", at=@At("HEAD"), cancellable=true)
    private void automobility$denyVehicleQuickMove(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (((ChestMenu)(Object)this).getContainer() instanceof VehicleCargoAccess cargo
                && !cargo.automobility$mayUseCargo(player)) cir.setReturnValue(ItemStack.EMPTY);
    }
}
