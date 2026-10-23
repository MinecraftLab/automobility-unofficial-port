package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.automobile.drivetrain.EngineController;
import io.github.foundationgames.automobility.automobile.drivetrain.PowertrainSimulation;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** A bounded row inside the existing instrument reservation, never an independent overlapping panel. */
public final class VehicleDrivetrainHud {
    public static final int HEIGHT = 20;
    private VehicleDrivetrainHud() { }
    public static boolean present(AutomobileEntity car) { return VehiclePowertrainPresentation.reading(car).isPresent(); }
    public static void draw(GuiGraphicsExtractor graphics, AutomobileEntity car, int y, int width) {
        var optional = VehiclePowertrainPresentation.reading(car);
        if (car != null && optional.isEmpty()) return;
        String range = "3", mode = "A"; double rpm = 3200, maximum = 6500; boolean stalled = false;
        if (optional.isPresent()) {
            var reading = optional.get();
            range = reading.rangeLabel(); rpm = Math.abs(reading.signedRpm()); maximum = reading.maximumRpm();
            mode = reading.electric() ? "EV" : reading.snapshot().controlMode() == PowertrainSimulation.ControlMode.AUTOMATIC ? "A" : "M";
            stalled = reading.snapshot().enginePhase() == EngineController.Phase.STALLED;
        }
        var font = Minecraft.getInstance().font;
        graphics.fill(0, y, width, y + HEIGHT, 0xea171a18);
        String text = mode + "  " + range + "    " + String.format(Locale.ROOT, "%.0f rpm", rpm);
        int color = stalled || rpm > maximum ? 0xffff8a79 : 0xffd7e3df;
        graphics.text(font, Component.literal(font.plainSubstrByWidth(text, Math.max(1, width - 10))), 5, y + 3, color, false);
        int fill = (int)Math.round(Math.clamp(rpm / maximum, 0.0, 1.0) * Math.max(0, width - 10));
        graphics.fill(5, y + 15, width - 5, y + 17, 0xff39443f);
        graphics.fill(5, y + 15, 5 + fill, y + 17, rpm > maximum * 0.9 ? 0xffffa06a : 0xff8adeae);
    }
}
