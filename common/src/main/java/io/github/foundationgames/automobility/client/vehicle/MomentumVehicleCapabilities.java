package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Optional;

/**
 * A small optional boundary to Momentum's existing public client controls.
 * No Momentum class appears in signatures or class constants, and presets are never mutated.
 * An incompatible addon disables its panel without affecting base vehicle controls.
 */
public final class MomentumVehicleCapabilities {
    private static boolean attempted;
    private static Adapter adapter;
    private static boolean drivingAttempted;
    private static DrivingAdapter drivingAdapter;

    private MomentumVehicleCapabilities() {}

    public static Optional<Snapshot> snapshot(AutomobileEntity automobile) {
        Adapter current = adapter();
        if (current == null) return Optional.empty();
        try {
            return Optional.of(current.snapshot(automobile));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            disable(error);
            return Optional.empty();
        }
    }

    /** The companion API and the authoritative server must both support vehicle presets. */
    public static boolean drivingModeSupported(AutomobileEntity automobile) {
        if (automobile == null || automobile.isRemoved()
                || (automobile.getVehicleDrivingModeCapabilities() & 1) == 0) return false;
        DrivingAdapter current = drivingAdapter();
        if (current == null) return false;
        try {
            return (boolean) current.supports.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            disableDriving(error);
            return false;
        }
    }

    /** Legacy Momentum keeps its existing read-only global tune and cruise controls. */
    public static String effectivePreset(AutomobileEntity automobile) {
        if (drivingModeSupported(automobile)) {
            try {
                return presetName(drivingAdapter.effective.invoke(null, automobile));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                disableDriving(error);
            }
        }
        return snapshot(automobile).map(Snapshot::preset).orElse("unknown");
    }

    private static DrivingAdapter drivingAdapter() {
        if (drivingAttempted) return drivingAdapter;
        drivingAttempted = true;
        // Momentum ships in Automobility itself; no separate loader identity is required.
        try {
            drivingAdapter = new DrivingAdapter();
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            // Older companions still support the independent legacy cruise adapter.
            drivingAdapter = null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            disableDriving(error);
        }
        return drivingAdapter;
    }

    private static void disableDriving(Throwable error) {
        drivingAdapter = null;
        Automobility.LOG.warn("Momentum vehicle presets are unavailable; existing cruise controls remain usable", error);
    }

    private static String presetName(Object value) {
        String name = value instanceof Enum<?> preset ? preset.name().toLowerCase(Locale.ROOT)
                : value instanceof String text ? text.toLowerCase(Locale.ROOT) : "unknown";
        return name.equals("touring") || name.equals("balanced") || name.equals("sport") || name.equals("custom")
                ? name : "unknown";
    }

    private static final class DrivingAdapter {
        private final Method supports;
        private final Method effective;

        private DrivingAdapter() throws ReflectiveOperationException {
            Class<?> modes = Class.forName("io.github.milkucha.momentum.MomentumVehicleDrivingModes", false,
                    MomentumVehicleCapabilities.class.getClassLoader());
            supports = modes.getMethod("supportsVehiclePresets");
            effective = modes.getMethod("effectivePreset", AutomobileEntity.class);
            if (supports.getReturnType() != boolean.class || effective.getReturnType() != String.class
                    || !Modifier.isStatic(supports.getModifiers()) || !Modifier.isStatic(effective.getModifiers()))
                throw new NoSuchMethodException("Incompatible Momentum vehicle preset API");
        }
    }

    public static Component perform(AutomobileEntity automobile, CruiseAction action) {
        var client = Minecraft.getInstance();
        if (!VehicleClientControls.isDriver(client, automobile) || !automobile.engineRunning()) return Component.translatable("message.momentum.cruise_unavailable");
        Adapter current = adapter();
        if (current == null) return Component.translatable("message.momentum.cruise_unavailable");
        try {
            return current.perform(automobile, action);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            disable(error);
            return Component.translatable("message.momentum.cruise_unavailable");
        }
    }

    /** Stop cruise immediately on the driver's ignition action, before server acknowledgement. */
    public static void deactivateCruise(AutomobileEntity automobile) {
        Adapter current = adapter();
        if (current == null) return;
        try {
            if ((boolean) current.activeFor.invoke(null, automobile)) current.deactivate.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            disable(error);
        }
    }

    private static Adapter adapter() {
        if (attempted) return adapter;
        attempted = true;
        // The packaged implementation is still isolated behind this narrow adapter.
        try {
            adapter = new Adapter();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            disable(error);
        }
        return adapter;
    }

    private static void disable(Throwable error) {
        adapter = null;
        Automobility.LOG.warn("Momentum vehicle settings integration is unavailable; built-in lights remain usable", error);
    }

    public enum CruiseAction { TOGGLE, INCREASE, DECREASE, RESUME }

    /** The host bridge or the server's permission-filtered command tree is authoritative. */
    public static boolean canChangeDrivingModel() {
        Adapter current = adapter();
        if (current == null) return false;
        try {
            return (boolean) current.canChangeModel.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            disable(error);
            return false;
        }
    }

    public static void requestDrivingModel(boolean extended) {
        Adapter current = adapter();
        if (current == null) return;
        try {
            current.requestModel.invoke(null, extended);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            disable(error);
        }
    }

    public record Snapshot(String preset, boolean serverControlled, boolean enabled,
                           boolean cruiseEnabled, boolean cruiseActive, boolean canResume, int targetKmh) {}

    private static final class Adapter {
        private final Method gameplay;
        private final Method serverControlled;
        private final Field preset;
        private final Field enabled;
        private final Field cruise;
        private final Method assistanceEnabled;
        private final Method activeFor;
        private final Method canResumeFor;
        private final Method target;
        private final Method toggle;
        private final Method deactivate;
        private final Method adjust;
        private final Method resume;
        private final Method resultAction;
        private final Method resultTarget;
        private final Method resultMinimum;
        private final Method canChangeModel;
        private final Method requestModel;

        private Adapter() throws ReflectiveOperationException {
            ClassLoader loader = MomentumVehicleCapabilities.class.getClassLoader();
            Class<?> configClass = Class.forName("io.github.milkucha.momentum.config.MomentumConfig", false, loader);
            Class<?> controlsClass = Class.forName("io.github.milkucha.momentum.MomentumCruiseControl", false, loader);
            Class<?> modelClass = Class.forName("io.github.milkucha.momentum.MomentumDrivingModelClient", false, loader);
            canChangeModel = modelClass.getMethod("canRequest");
            requestModel = modelClass.getMethod("request", boolean.class);
            gameplay = configClass.getMethod("gameplay");
            serverControlled = configClass.getMethod("hasServerGameplayConfig");
            preset = configClass.getField("drivingPreset");
            enabled = configClass.getField("enabled");
            cruise = configClass.getField("cruise");
            assistanceEnabled = controlsClass.getMethod("isEnabled");
            activeFor = controlsClass.getMethod("isActiveFor", AutomobileEntity.class);
            canResumeFor = controlsClass.getMethod("canResumeFor", AutomobileEntity.class);
            target = controlsClass.getMethod("getTargetKmh");
            toggle = controlsClass.getMethod("toggle", AutomobileEntity.class);
            deactivate = controlsClass.getMethod("deactivate");
            adjust = controlsClass.getMethod("adjustTarget", AutomobileEntity.class, int.class);
            resume = controlsClass.getMethod("resume", AutomobileEntity.class);
            Class<?> result = toggle.getReturnType();
            resultAction = result.getMethod("action");
            resultTarget = result.getMethod("targetKmh");
            resultMinimum = result.getMethod("minimumKmh");
        }

        private Snapshot snapshot(AutomobileEntity automobile) throws ReflectiveOperationException {
            Object config = gameplay.invoke(null);
            Object selected = preset.get(config);
            String name = presetName(selected);
            boolean addonEnabled = enabled.getBoolean(config);
            boolean active = (boolean) activeFor.invoke(null, automobile);
            float targetSpeed = ((Number) target.invoke(null)).floatValue();
            return new Snapshot(name, (boolean) serverControlled.invoke(null), addonEnabled,
                    automobile.engineRunning() && (boolean) assistanceEnabled.invoke(null), active,
                    (boolean) canResumeFor.invoke(null, automobile),
                    Float.isFinite(targetSpeed) ? Math.clamp(Math.round(targetSpeed), 0, 999) : 0);
        }

        private Component perform(AutomobileEntity automobile, CruiseAction action) throws ReflectiveOperationException {
            Object result = switch (action) {
                case TOGGLE -> toggle.invoke(null, automobile);
                case INCREASE -> adjust.invoke(null, automobile, 1);
                case DECREASE -> adjust.invoke(null, automobile, -1);
                case RESUME -> resume.invoke(null, automobile);
            };
            String state = ((Enum<?>) resultAction.invoke(result)).name();
            int speed = Math.round(((Number) resultTarget.invoke(result)).floatValue());
            int minimum = Math.round(((Number) resultMinimum.invoke(result)).floatValue());
            return switch (state) {
                case "ENGAGED" -> Component.translatable("message.momentum.cruise_enabled", speed);
                case "DISENGAGED" -> Component.translatable("message.momentum.cruise_disabled");
                case "RESUMED" -> Component.translatable("message.momentum.cruise_resumed", speed);
                case "ADJUSTED", "ALREADY_ACTIVE" -> Component.translatable("message.momentum.cruise_adjusted", speed);
                case "TOO_SLOW" -> Component.translatable("message.momentum.cruise_too_slow", minimum);
                case "NOT_ACTIVE" -> Component.translatable("message.momentum.cruise_not_active");
                case "NO_SAVED_TARGET" -> Component.translatable("message.momentum.cruise_no_saved_speed");
                default -> Component.translatable("message.momentum.cruise_unavailable");
            };
        }
    }
}
