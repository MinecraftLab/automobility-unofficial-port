package io.github.milkucha.momentum.mixin;

import io.github.foundationgames.automobility.automobile.AutomobileStats;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.MomentumVehicleDrivingModes;
import io.github.milkucha.momentum.accessor.VehicleDrivingStatsAccess;
import io.github.milkucha.momentum.config.MomentumConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.lang.ref.WeakReference;

/**
 * AutomobileStatsMixin - scales the comfortable speed threshold.
 *
 * Automobility's comfortableSpeed is computed in AutomobileStats.from() from
 * engine/frame/wheel stats. It acts as a soft speed cap: above it, acceleration
 * drops to 25% of normal (see movementTick line ~794). The threshold also affects
 * boost top-up speed and off-road speed caps.
 *
 * Owner context covers movement, boost and visual wheelie call sites without changing
 * the base stat or multiplying it twice. Standalone garage stats keep the global tune.
 *
 * A multiplier of 1.0 = vanilla. 1.5 = threshold 50% higher (e.g. 60 km/h → 90 km/h).
 */
@Mixin(value = AutomobileStats.class, remap = false)
public class AutomobileStatsMixin implements VehicleDrivingStatsAccess {
    @Unique private WeakReference<AutomobileEntity> momentum$drivingOwner;

    @Override
    public void momentum$bindDrivingOwner(AutomobileEntity automobile) {
        momentum$drivingOwner = new WeakReference<>(automobile);
    }

    @Inject(method = "getComfortableSpeed", at = @At("RETURN"), cancellable = true)
    private void momentum$scaleComfortableSpeed(CallbackInfoReturnable<Float> cir) {
        AutomobileEntity owner = momentum$drivingOwner == null ? null : momentum$drivingOwner.get();
        MomentumConfig config = owner == null ? MomentumConfig.gameplay() : MomentumVehicleDrivingModes.baselineFor(owner);
        if (!config.enabled || !config.movement.enabled) return;
        var settings = owner == null ? MomentumVehicleDrivingModes.inherited(config)
                : MomentumVehicleDrivingModes.settingsFor(owner);
        cir.setReturnValue(MomentumVehicleDrivingModes.scaleComfortableSpeed(cir.getReturnValue(), config, settings));
    }
}
