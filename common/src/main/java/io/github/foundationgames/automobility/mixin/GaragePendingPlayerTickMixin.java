package io.github.foundationgames.automobility.mixin;
import io.github.foundationgames.automobility.entity.GarageTransferRuntime;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Only unresolved item transactions pause this actor, never the connection or other players. */
@Mixin(ServerPlayer.class)
public abstract class GaragePendingPlayerTickMixin {
    private boolean automobility$inventoryPending(){var actor=(ServerPlayer)(Object)this;return GarageTransferRuntime.actorPending(actor.level().getServer(),actor.getUUID());}
    @Inject(method={"tick","doTick"},at=@At("HEAD"),cancellable=true)
    private void automobility$pendingTick(CallbackInfo ci){if(automobility$inventoryPending())ci.cancel();}
    @Inject(method="hurtServer",at=@At("HEAD"),cancellable=true)
    private void automobility$pendingDamage(ServerLevel level,DamageSource source,float amount,CallbackInfoReturnable<Boolean> cir){if(automobility$inventoryPending())cir.setReturnValue(false);}
}
