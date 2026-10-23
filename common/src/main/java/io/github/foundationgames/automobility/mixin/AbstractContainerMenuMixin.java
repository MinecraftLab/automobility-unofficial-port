package io.github.foundationgames.automobility.mixin;

import io.github.foundationgames.automobility.screen.AutomobileContainerLevelAccess;
import io.github.foundationgames.automobility.util.duck.VehicleCargoAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public class AbstractContainerMenuMixin {
    @Inject(method="clicked", at=@At("HEAD"), cancellable=true)
    private void automobility$denyVehicleCargoClick(int slot, int button, ContainerInput input,
                                                   Player player, CallbackInfo ci) {
        if ((Object)this instanceof ChestMenu chest && chest.getContainer() instanceof VehicleCargoAccess cargo
                && !cargo.automobility$mayUseCargo(player)) ci.cancel();
    }
    @Inject(
            method = "stillValid(Lnet/minecraft/world/inventory/ContainerLevelAccess;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/block/Block;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void automobility$spoofAutomobileAttachmentScreens(ContainerLevelAccess context, Player player, Block block, CallbackInfoReturnable<Boolean> cir) {
        if (context instanceof AutomobileContainerLevelAccess ctx) {
            cir.setReturnValue(ctx.isValidFor(player) && ctx.getAttachmentBlockState().is(block));
        }
    }
}
