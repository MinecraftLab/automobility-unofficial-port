package io.github.foundationgames.automobility.client.vehicle.energy;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.client.vehicle.VehicleEnergyReading;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.lang.reflect.Method;

/** Optional exact-provider adapters. Reflection is bound once; getters are sampled once per client tick. */
public final class VehicleEnergyAdapters {
    public interface Reader { VehicleEnergyReading read(AutomobileEntity auto) throws ReflectiveOperationException; }
    private static Reader fuel, battery;
    private static boolean initialized;
    private VehicleEnergyAdapters() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        var loader = VehicleEnergyAdapters.class.getClassLoader();
        if (EnergyCompatibilityGate.pumps()) try {
            var type = Class.forName("dev.rosenvik.pumpsandpistons.FuelledAutomobile", false, loader);
            var access = Class.forName("dev.rosenvik.pumpsandpistons.compat.automobility.api.AutomobilityIntegrationAccess", false, loader);
            Method enabled = access.getMethod("fuelEnabled"), amount = type.getMethod("pumpsandpistons$getFuelMl"),
                    capacity = type.getMethod("pumpsandpistons$getCapacityMl"), stored = type.getMethod("pumpsandpistons$getFuelType"),
                    required = type.getMethod("pumpsandpistons$getRequiredFuelType");
            fuel = auto -> {
                if (!type.isInstance(auto) || !Boolean.TRUE.equals(enabled.invoke(null))) return VehicleEnergyReading.NONE;
                var needed = required.invoke(auto);
                if (!(needed instanceof Enum<?> expected) || expected.name().equals("NONE")) return VehicleEnergyReading.NONE;
                long current = ((Number) amount.invoke(auto)).longValue(), maximum = ((Number) capacity.invoke(auto)).longValue();
                var value = stored.invoke(auto);
                boolean wrong = current > 0 && value instanceof Enum<?> actual
                        && !actual.name().equals("NONE") && value != needed;
                return VehicleEnergyReading.of(VehicleEnergyReading.Kind.FUEL, current, maximum, wrong, expected.name().equals("DIESEL"));
            };
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Automobility.LOG.warn("Optional fuel HUD adapter unavailable; keeping the producer HUD", failure);
        }
        if (EnergyCompatibilityGate.powerworks()) try {
            var type = Class.forName("com.offchunk.powerworks.compat.automobility.VehicleBatteryAccess", false, loader);
            var bridge = Class.forName("com.offchunk.powerworks.compat.automobility.AutomobilityBridge", false, loader);
            Method energy = type.getMethod("powerworks$getSyncedEnergy"), electric = type.getMethod("powerworks$isElectricVehicle");
            long maximum = bridge.getField("BATTERY_CAPACITY").getLong(null);
            if (maximum <= 0) throw new ReflectiveOperationException("Invalid qualified battery capacity");
            battery = auto -> {
                if (!type.isInstance(auto) || !Boolean.TRUE.equals(electric.invoke(auto))) return VehicleEnergyReading.NONE;
                return VehicleEnergyReading.of(VehicleEnergyReading.Kind.BATTERY,
                        ((Number) energy.invoke(auto)).longValue(), maximum, false, false);
            };
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            Automobility.LOG.warn("Optional battery HUD adapter unavailable; keeping producer notices", failure);
        }
    }

    public static boolean hasFuel() { initialize(); return fuel != null; }
    public static boolean hasBattery() { initialize(); return battery != null; }
    public static VehicleEnergyReading read(AutomobileEntity auto) {
        initialize();
        if (battery != null) try {
            var value = battery.read(auto);
            if (value.present()) return value;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            battery = null; Automobility.LOG.warn("Battery HUD adapter stopped; preserving producer notices", failure);
        }
        if (fuel != null) try { return fuel.read(auto); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            fuel = null; Automobility.LOG.warn("Fuel HUD adapter stopped; preserving producer HUD", failure);
        }
        return VehicleEnergyReading.NONE;
    }
}
