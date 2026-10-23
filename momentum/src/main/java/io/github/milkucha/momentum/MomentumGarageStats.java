package io.github.milkucha.momentum;

import io.github.foundationgames.automobility.automobile.driving.GarageDrivingStats;
import io.github.foundationgames.automobility.automobile.driving.VehicleDrivingMode;
import io.github.milkucha.momentum.config.MomentumConfig;
import net.minecraft.resources.Identifier;

/** Nominal built-in forward braking, not a measurement or an external engine owner override. */
public final class MomentumGarageStats {
    private MomentumGarageStats() {}

    public static GarageDrivingStats.Snapshot snapshot(GarageDrivingStats.Snapshot original,
            float rawThreshold, Identifier frameId, VehicleDrivingMode mode, MomentumConfig config) {
        var tune = MomentumVehicleDrivingModes.resolve(config, mode,
                config.enabled && config.allowVehiclePresets);
        float speed = MomentumVehicleDrivingModes.scaleComfortableSpeed(
                rawThreshold, config, tune) * 20;
        float braking = config.enabled ? original.braking() : OriginalBrakePolicy.ORDINARY_DECAY;
        if (config.enabled && config.movement.enabled) {
            braking = PedalBrakePolicy.decay(1f, tune.brakeDecay(), config.movement.forwardPedalScale,
                    config.movement.vehiclePedalBalance,
                    frameId == null ? "" : frameId.getNamespace(),
                    frameId == null ? "" : frameId.getPath());
        }
        return new GarageDrivingStats.Snapshot(original.acceleration(), speed, original.handling(),
                original.grip(), braking, config.enabled);
    }
}
