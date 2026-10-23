package io.github.milkucha.momentum.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.milkucha.momentum.network.ClientPhysicsSession;
import io.github.milkucha.momentum.network.ServerPhysicsSessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Admission is independent of Core's driver identity and normal dismount cleanup. */
@Mixin(value = AutomobileEntity.class, remap = false, priority = 1100)
public abstract class AutomobileSessionMixin {
    @Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
    private void momentum$checkDriverAdmission(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
        var car = (AutomobileEntity) (Object) this;
        if (car.level().isClientSide() || !(passenger instanceof ServerPlayer player)
                || ServerPhysicsSessions.canDrive(player)) return;
        // A player already in the front cannot be kicked out by normal Core boarding.
        // Use the actual first seat (RIA may leave it empty with occupied rear seats).
        if (!(car.getFirstPassenger() instanceof Player)) {
            ServerPhysicsSessions.warn(player);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canKickPassengerOut", at = @At("HEAD"), cancellable = true)
    private void momentum$denyUnverifiedDriverReplacement(Entity passenger, Player incoming,
            CallbackInfoReturnable<Boolean> cir) {
        if (incoming instanceof ServerPlayer player && !ServerPhysicsSessions.canDrive(player)) {
            ServerPhysicsSessions.warn(player);
            cir.setReturnValue(false);
        }
    }

    @ModifyReturnValue(method = "getControllingPassenger", at = @At("RETURN"))
    private LivingEntity momentum$denyUnverifiedVehicleMovement(LivingEntity original) {
        // Vanilla vehicle-move packets check this too. Do not change isDriving:
        // removePassenger still needs the true driver identity for Core cleanup.
        return original instanceof ServerPlayer player && !ServerPhysicsSessions.canDrive(player)
                ? null : original;
    }

    @Inject(method = "applyClientInputState", at = @At("HEAD"), cancellable = true)
    private void momentum$denyUnverifiedInput(boolean forward, boolean backward, float side,
            boolean drift, boolean horn, CallbackInfoReturnable<Boolean> cir) {
        var car = (AutomobileEntity) (Object) this;
        if (!car.level().isClientSide() && car.getFirstPassenger() instanceof ServerPlayer player
                && !ServerPhysicsSessions.canDrive(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lio/github/foundationgames/automobility/entity/AutomobileEntity;expireStaleClientInput()V",
            shift = At.Shift.BEFORE), require = 1)
    private void momentum$recheckActualDriverBeforePhysics(CallbackInfo ci) {
        var car = (AutomobileEntity) (Object) this;
        if (!car.level().isClientSide() && car.getFirstPassenger() instanceof ServerPlayer player
                && car.isDriving(player) && !ServerPhysicsSessions.canDrive(player)) {
            // After all RIA HEAD reconciliation and after startRiding has returned.
            // Never roll back inside addPassenger: startRiding still dereferences vehicle.
            ServerPhysicsSessions.warn(player);
            // RIA may clear the driver seat lookup before Core removePassenger runs.
            // Neutralize propulsion here, before this same ordinary physics tick continues.
            car.input.clearInputs();
            car.markDirty();
            player.stopRiding();
        }
    }

    @ModifyArgs(method = "provideClientInput", at = @At(value = "INVOKE",
            target = "Lio/github/foundationgames/automobility/entity/AutomobileEntity$Input;setDigitalInputs(ZZZZZZ)Z"),
            require = 1)
    private void momentum$neutralizeUnknownPhysicsPeer(Args args) {
        if (!MomentumConfig.clientDrivingBlocked()) return;
        // Let Core capture the release edge and send a neutral input normally.
        for (int i = 0; i < 6; i++) args.set(i, false);
    }
}
