package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.client.vehicle.energy.VehicleEnergyAdapters;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** One tick-local current-driver snapshot. Rendering never polls optional producers. */
public final class VehicleEnergyHud {
    private static AutomobileEntity car;
    private static Object world, driver;
    private static VehicleEnergyReading reading = VehicleEnergyReading.NONE;
    private static boolean reverse;
    private VehicleEnergyHud() {}

    public static void tick(Minecraft client) {
        AutomobileEntity current = client.player != null && client.player.getVehicle() instanceof AutomobileEntity auto
                && VehicleClientControls.isDriver(client, auto) ? auto : null;
        if (current != car || client.level != world || client.player != driver) {
            car = current; world = client.level; driver = client.player;
            reading = VehicleEnergyReading.NONE; reverse = false;
        }
        if (current == null || current.isRemoved()) { reading = VehicleEnergyReading.NONE; reverse = false; return; }
        reading = VehicleEnergyAdapters.read(current);
        var delta = current.getMeasuredMovement();
        reverse = VehicleEnergyPresentation.reverseMotion(delta.x, delta.z, current.getYRot());
    }

    private static boolean context(AutomobileEntity auto) {
        var c = Minecraft.getInstance();
        return auto != null && auto == car && !auto.isRemoved() && c.level == world && c.player == driver
                && c.player != null && c.player.getVehicle() == auto && VehicleClientControls.isDriver(c, auto);
    }
    public static VehicleEnergyReading reading(AutomobileEntity auto) { return context(auto) ? reading : VehicleEnergyReading.NONE; }
    public static boolean reversing(AutomobileEntity auto) { return context(auto) && reverse; }

    /** Editor examples are clearly preview-only and follow the actual current-car capability when present. */
    public static VehicleEnergyReading preview() {
        var c = Minecraft.getInstance();
        if (car != null && context(car)) return reading;
        if (VehicleEnergyAdapters.hasFuel()) return VehicleEnergyReading.of(VehicleEnergyReading.Kind.FUEL, 18000, 24000, false, false);
        if (VehicleEnergyAdapters.hasBattery()) return VehicleEnergyReading.of(VehicleEnergyReading.Kind.BATTERY, 180000, 240000, false, false);
        return VehicleEnergyReading.NONE;
    }

    public static boolean owns(VehicleEnergyReading.Kind kind) {
        var c = Minecraft.getInstance();
        boolean qualified = kind == VehicleEnergyReading.Kind.FUEL ? VehicleEnergyAdapters.hasFuel() : VehicleEnergyAdapters.hasBattery();
        return qualified && VehicleEnergyPresentation.ownsGauge(VehicleUiState.unified(), VehicleUiState.current().energyEnabled(),
                !c.options.hideGui && c.screen == null, context(car), reading, kind);
    }

    /** Route only the ordinary structured PW battery notice; every warning/regen/foreign overlay survives. */
    public static boolean suppressBatteryNotice(Component text) {
        return owns(VehicleEnergyReading.Kind.BATTERY) && text.getContents() instanceof TranslatableContents content
                && VehicleEnergyPresentation.ordinaryBatteryNotice(content.getKey(), text.getSiblings().isEmpty(), content.getArgs().length);
    }

    public static Component warning(VehicleEnergyReading value) {
        if (!value.known() || value.state() == VehicleEnergyReading.State.NORMAL) return null;
        String state = switch (value.state()) {
            case WRONG_FUEL -> "wrong_fuel";
            case EMPTY -> value.kind() == VehicleEnergyReading.Kind.BATTERY ? "battery_empty" : "fuel_empty";
            default -> value.kind() == VehicleEnergyReading.Kind.BATTERY ? "battery_low" : "fuel_low";
        };
        return Component.translatable("ui.automobility.energy.warning." + state);
    }
}
