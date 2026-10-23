package io.github.milkucha.momentum;

import io.github.foundationgames.automobility.automobile.driving.VehicleDrivingMode;
import io.github.foundationgames.automobility.automobile.driving.VehicleDrivingModeSupport;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.config.MomentumConfig;

import java.util.Locale;
import java.util.Objects;

/** Per-car chassis values; neither a selection nor resolution mutates the gameplay configuration. */
public final class MomentumVehicleDrivingModes {
    // Resolve named tables once from the existing preset definition, never from the shared singleton.
    private static final Settings TOURING = named(MomentumConfig.DrivingPreset.TOURING);
    private static final Settings BALANCED = named(MomentumConfig.DrivingPreset.BALANCED);
    private static final Settings SPORT = named(MomentumConfig.DrivingPreset.SPORT);
    private static final ThreadLocal<Baseline> INHERITED = ThreadLocal.withInitial(Baseline::new);

    private MomentumVehicleDrivingModes() {}

    /** Only the eight numbers owned by a preset. Feature toggles and all other groups remain global. */
    public record Settings(float coastDecay, float accelerationScale, float brakeDecay,
                           float comfortableSpeedMultiplier, float rampRate, float centerRate,
                           float understeer, float understeerCurve) {
        private static Settings capture(MomentumConfig config) {
            return new Settings(config.movement.coastDecay, config.movement.accelerationScale,
                    config.movement.brakeDecay, config.movement.comfortableSpeedMultiplier,
                    config.steering.rampRate, config.steering.centerRate,
                    config.steering.understeer, config.steering.understeerCurve);
        }

        private boolean matches(MomentumConfig config) {
            return same(coastDecay, config.movement.coastDecay)
                    && same(accelerationScale, config.movement.accelerationScale)
                    && same(brakeDecay, config.movement.brakeDecay)
                    && same(comfortableSpeedMultiplier, config.movement.comfortableSpeedMultiplier)
                    && same(rampRate, config.steering.rampRate)
                    && same(centerRate, config.steering.centerRate)
                    && same(understeer, config.steering.understeer)
                    && same(understeerCurve, config.steering.understeerCurve);
        }

        private static boolean same(float first, float second) {
            return Float.floatToIntBits(first) == Float.floatToIntBits(second);
        }
    }

    /** Server physics reads its own policy/config; a remote client reads the received gameplay snapshot. */
    public static MomentumConfig baselineFor(AutomobileEntity automobile) {
        return automobile.level().isClientSide() ? MomentumConfig.gameplay() : MomentumConfig.get();
    }

    public static Settings settingsFor(AutomobileEntity automobile) {
        var config = baselineFor(automobile);
        return resolve(config, automobile.getVehicleDrivingMode(), policyAllows(config,
                automobile.level().isClientSide(), automobile.getVehicleDrivingModeCapabilities()));
    }

    /** Public optional-integration probe. Legacy Momentum lacks this API and keeps its global behavior. */
    public static boolean supportsVehiclePresets() { return MomentumConfig.gameplay().enabled; }

    public static String effectivePreset(AutomobileEntity automobile) {
        var config = baselineFor(automobile);
        return effectivePreset(config, automobile.getVehicleDrivingMode(), policyAllows(config,
                automobile.level().isClientSide(), automobile.getVehicleDrivingModeCapabilities()));
    }

    static boolean policyAllows(MomentumConfig config, boolean clientSide, int capabilities) {
        // An entity advertisement is the live client policy, even if the config JSON is older.
        // The server reads its own current policy instead of trusting an advertisement.
        return clientSide
                ? (capabilities
                    & (VehicleDrivingModeSupport.SUPPORTED | VehicleDrivingModeSupport.ALLOWED))
                    == (VehicleDrivingModeSupport.SUPPORTED | VehicleDrivingModeSupport.ALLOWED)
                : config.enabled && config.allowVehiclePresets;
    }

    static Settings resolve(MomentumConfig config, VehicleDrivingMode selected, boolean serverAllows) {
        Objects.requireNonNull(config);
        if (serverAllows && config.enabled) {
            if (selected == VehicleDrivingMode.TOURING) return TOURING;
            if (selected == VehicleDrivingMode.BALANCED) return BALANCED;
            if (selected == VehicleDrivingMode.SPORT) return SPORT;
        }
        return inherited(config);
    }

    static String effectivePreset(MomentumConfig config, VehicleDrivingMode selected, boolean serverAllows) {
        if (serverAllows && config.enabled
                && selected != null && selected != VehicleDrivingMode.INHERIT) {
            return selected.name().toLowerCase(Locale.ROOT);
        }
        return config.drivingPreset == null ? "unknown" : config.drivingPreset.name().toLowerCase(Locale.ROOT);
    }

    /** Preserves the existing getter multiplier and its enabled gates for owned and unowned stats. */
    public static float scaleComfortableSpeed(float original, MomentumConfig config, Settings settings) {
        return config.enabled && config.movement.enabled ? original * settings.comfortableSpeedMultiplier() : original;
    }

    public static Settings inherited(MomentumConfig config) {
        return INHERITED.get().snapshot(Objects.requireNonNull(config));
    }

    private static Settings named(MomentumConfig.DrivingPreset preset) {
        var isolated = new MomentumConfig();
        isolated.applyDrivingPreset(preset);
        return Settings.capture(isolated);
    }

    /** One immutable baseline per logical thread, reused until any of the actual eight values changes. */
    private static final class Baseline {
        private MomentumConfig config;
        private Settings settings;

        private Settings snapshot(MomentumConfig current) {
            if (config != current || settings == null || !settings.matches(current)) {
                settings = Settings.capture(current);
                config = current;
            }
            return settings;
        }
    }
}
