package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;
import io.github.foundationgames.automobility.automobile.light.VehicleSignalMode;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** A dedicated light indicator; existing speed and Momentum dashboards keep ownership of speed. */
public final class VehicleStatusHud {
    private VehicleStatusHud() {}

    public static Component modeName(VehicleLightMode mode) {
        return Component.translatable("vehicle.automobility.light.mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    public static void render(GuiGraphicsExtractor graphics) {
        var client = Minecraft.getInstance();
        if(UnifiedVehicleHud.unified()) {
            if(client.player!=null && client.player.getVehicle() instanceof AutomobileEntity auto) UnifiedVehicleHud.render(graphics,auto);
            return;
        }
        var preferences = VehicleClientControls.preferences();
        if (client.options.hideGui || client.screen != null || client.player == null
                || !(client.player.getVehicle() instanceof AutomobileEntity auto) || !VehicleClientControls.isDriver(client, auto)) return;
        var layout = VehicleHudLayout.resolve(graphics.guiWidth(), graphics.guiHeight(), contentWidth(auto), preferences);
        if (preferences.enabled()) drawPanelAt(graphics, auto, layout);
        int engineHintHeight = 0;
        if (preferences.enabled() && (auto.input.accelerating || auto.rawBrakePedalHeld()) && auto.getIgnitionState() == 0) {
            Component hint = auto.getEngine().isEmpty() ? Component.translatable("vehicle.automobility.hud.missing_engine")
                    : auto.getVehicleEngineSettings().automaticStart() ? Component.translatable("vehicle.automobility.hud.release_pedal")
                    : Component.translatable("vehicle.automobility.hud.manual_start", VehicleClientControls.engineBinding());
            int x = layout.x();
            int hintWidth = Math.max(1, Math.min(client.font.width(hint) + 10, graphics.guiWidth() - x));
            var lines = client.font.split(hint, Math.max(1, hintWidth - 10));
            int count = Math.min(2, lines.size()), hintHeight = count * 11;
            engineHintHeight = hintHeight;
            int y = engineHintY(graphics.guiHeight(), layout, hintHeight, preferences.position());
            graphics.fill(x, y - 1, x + hintWidth, y + hintHeight, 0xEB172026);
            for (int i = 0; i < count; i++) graphics.text(client.font, lines.get(i), x + 5, y + i * 11, 0xFFFFD095, false);
        }
        drawTestDriveHint(graphics, auto, layout, preferences, engineHintHeight);
    }

    private static void drawTestDriveHint(GuiGraphicsExtractor graphics, AutomobileEntity auto,
                                          VehicleHudLayout.Layout layout, VehicleHudPreferences preferences, int engineHintHeight) {
        if (!GarageTestDriveClient.matchesVehicle(auto)) return;
        var client = Minecraft.getInstance();
        var availability = GarageTestDriveClient.availability(auto);
        Component heading = Component.translatable("garage.automobility.test_drive.return_hint", GarageTestDriveClient.binding());
        Component feedback = GarageTestDriveClient.feedback();
        Component detail = availability.enabled() ? feedback == null
                ? Component.translatable("garage.automobility.test_drive.session_active") : feedback : availability.reason();
        int x = preferences.enabled() ? layout.x() : 8;
        int availableWidth = Math.max(1, graphics.guiWidth() - x - 8);
        int width = Math.min(availableWidth, Math.max(client.font.width(heading), client.font.width(detail)) + 10);
        var headingLines = client.font.split(heading, Math.max(1, width - 10));
        int headingCount = Math.min(2, headingLines.size());
        var lines = client.font.split(detail, Math.max(1, width - 10));
        int count = Math.min(2, lines.size()), height = (headingCount + count) * 11;
        int y = testDriveHintY(graphics.guiHeight(), layout, height, engineHintHeight, preferences.position(), preferences.enabled());
        graphics.fill(x, y - 1, x + width, y + height, 0xEB172026);
        graphics.fill(x, y - 1, x + 2, y + height, availability.enabled() ? 0xFFD2BA85 : 0xFF7B8990);
        for (int line = 0; line < headingCount; line++) graphics.text(client.font, headingLines.get(line), x + 5, y + line * 11,
                availability.enabled() ? 0xFFFFD095 : 0xFFA7BAC5, false);
        for (int line = 0; line < count; line++) graphics.text(client.font, lines.get(line), x + 5, y + (headingCount + line) * 11,
                availability.enabled() ? 0xFFA7BAC5 : 0xFFE9A48F, false);
    }

    /** The drive cue is outside both the status panel and an existing engine warning. */
    static int testDriveHintY(int screenHeight, VehicleHudLayout.Layout layout, int hintHeight, int engineHintHeight,
                             VehicleHudPreferences.Position position, boolean statusPanelVisible) {
        if (!statusPanelVisible) return Math.max(1, Math.min(12, screenHeight - hintHeight - 2));
        int first = layout.y(), last = layout.y() + (int)Math.ceil(23 * layout.scale());
        if (engineHintHeight > 0) {
            int warning = engineHintY(screenHeight, layout, engineHintHeight, position);
            first = Math.min(first, warning); last = Math.max(last, warning + engineHintHeight);
        }
        int above = first - hintHeight - 3, below = last + 3;
        boolean preferAbove = position == VehicleHudPreferences.Position.BOTTOM_LEFT;
        int anchor = preferAbove ? above : below;
        if (anchor < 1 || anchor + hintHeight > screenHeight - 2) {
            int alternate = preferAbove ? below : above;
            if (alternate >= 1 && alternate + hintHeight <= screenHeight - 2) anchor = alternate;
        }
        return Math.max(1, Math.min(screenHeight - hintHeight - 2, anchor));
    }

    /** Maximum over all tracked modes: changing lights never changes cluster bounds. */
    public static int maximumContentWidth() {
        var font=Minecraft.getInstance().font;int width=0;
        for(int state=0;state<3;state++) width=Math.max(width,font.width(Component.translatable("vehicle.automobility.hud.motor",Component.translatable("vehicle.automobility.engine.state."+state))));
        for(var mode:VehicleLightMode.values()) {
            width=Math.max(width,font.width(modeName(mode)));
            width=Math.max(width,font.width(Component.translatable("vehicle.automobility.hud.auto_status",modeName(mode))));
        }
        return width+67;
    }

    public static int contentWidth(AutomobileEntity auto) {
        var font = Minecraft.getInstance().font;
        int engineWidth = 0;
        for (int state = 0; state < 3; state++) engineWidth = Math.max(engineWidth,
                font.width(Component.translatable("vehicle.automobility.hud.motor", Component.translatable("vehicle.automobility.engine.state." + state))));
        return Math.max(engineWidth, font.width(lightStatus(auto))) + 67;
    }

    /** Bottom HUD hints stay above the panel, preserving its reserved lower dashboard area. */
    static int engineHintY(int screenHeight, VehicleHudLayout.Layout layout, int hintHeight,
                           VehicleHudPreferences.Position position) {
        int anchor = position == VehicleHudPreferences.Position.BOTTOM_LEFT
                ? layout.y() - hintHeight - 3 : layout.y() + (int)Math.ceil(23 * layout.scale()) + 3;
        return Math.max(1, Math.min(screenHeight - hintHeight - 2, anchor));
    }

    static int lightColor(VehicleLightMode effectiveMode) {
        return switch (effectiveMode) {
            case HIGH -> 0xFF77BBFF;
            case LOW -> 0xFF8ADEAE;
            case POSITION -> 0xFFFFD095;
            default -> 0xFF919DA4;
        };
    }

    /** The live indicator and preview use the same content and transform. */
    public static void drawPanelAt(GuiGraphicsExtractor graphics, AutomobileEntity auto, VehicleHudLayout.Layout layout) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(layout.x(), layout.y());
        graphics.pose().scale(layout.scale());
        drawPanel(graphics, auto, layout.logicalWidth());
        graphics.pose().popMatrix();
    }

    public static void drawPanel(GuiGraphicsExtractor graphics, AutomobileEntity auto, int width) {
        var font = Minecraft.getInstance().font;
        graphics.fill(0, 0, width, 23, 0xEB172026);
        graphics.fill(0, 0, width, 1, 0xFF596E7B);
        graphics.fill(0, 22, width, 23, 0xFF0A1014);
        int engineColor = auto.getIgnitionState() == 2 ? 0xFF8ADEAE : auto.getIgnitionState() == 1 ? 0xFFFFD095 : 0xFF919DA4;
        graphics.fill(0, 1, 2, 22, engineColor);
        int color = lightColor(auto.getEffectiveVehicleLightMode());
        graphics.fill(7, 4, 16, 10, engineColor);
        graphics.fill(9, 2, 14, 4, engineColor);
        graphics.fill(5, 6, 7, 9, engineColor);
        graphics.fill(16, 5, 18, 8, engineColor);
        drawLamp(graphics, 5, 12, color, auto.getEffectiveVehicleLightMode() == VehicleLightMode.HIGH);
        graphics.text(font, Component.translatable("vehicle.automobility.hud.motor",
                Component.translatable("vehicle.automobility.engine.state." + auto.getIgnitionState())), 23, 3, engineColor, false);
        graphics.text(font, lightStatus(auto), 23, 13, color, false);
        int signals = signalMask(auto.getVehicleSignalMode(), auto.vehicleSignalLit());
        drawSignal(graphics, signalX(width, false), 8, false, (signals & 1) != 0);
        drawSignal(graphics, signalX(width, true), 8, true, (signals & 2) != 0);
        var access = auto.getVehicleAccess();
        if (access.owner() != null) {
            int lockColor = access.locked() ? 0xFFFFD095 : 0xFF7F929D;
            int x = width - 13;
            graphics.fill(x, 7, x + 7, 13, lockColor);
            graphics.fill(x + 1, 3, x + 6, 4, lockColor);
            graphics.fill(x + 1, 4, x + 2, 7, lockColor);
            if (access.locked()) graphics.fill(x + 5, 4, x + 6, 7, lockColor);
        }
    }

    public static java.util.List<Component> warningLines(AutomobileEntity auto) {
        var result=new java.util.ArrayList<Component>();
        if(VehicleClientControls.preferences().enabled() && (auto.input.accelerating || auto.rawBrakePedalHeld()) && auto.getIgnitionState()==0) {
            result.add(auto.getEngine().isEmpty()?Component.translatable("vehicle.automobility.hud.missing_engine")
                    :auto.getVehicleEngineSettings().automaticStart()?Component.translatable("vehicle.automobility.hud.release_pedal")
                    :Component.translatable("vehicle.automobility.hud.manual_start",VehicleClientControls.engineBinding()));
        }
        if(GarageTestDriveClient.matchesVehicle(auto)) {
            var availability=GarageTestDriveClient.availability(auto);var feedback=GarageTestDriveClient.feedback();
            result.add(Component.translatable("garage.automobility.test_drive.return_hint",GarageTestDriveClient.binding()));
            result.add(availability.enabled()?feedback==null?Component.translatable("garage.automobility.test_drive.session_active"):feedback:availability.reason());
        }
        return result;
    }

    private static Component status(AutomobileEntity auto) {
        return Component.translatable("vehicle.automobility.hud.engine_status",
                Component.translatable("vehicle.automobility.engine.state." + auto.getIgnitionState()), lightStatus(auto));
    }

    static int signalMask(VehicleSignalMode mode, boolean lit) {
        return !lit ? 0 : (mode.left() ? 1 : 0) | (mode.right() ? 2 : 0);
    }

    /** The two seven-pixel glyphs fit between text padding and the existing lock reservation. */
    static int signalX(int panelWidth, boolean right) {
        return panelWidth - (right ? 28 : 39);
    }

    private static void drawSignal(GuiGraphicsExtractor graphics, int x, int y, boolean right, boolean lit) {
        int color = lit ? 0xFF8ADEAE : 0xFF40534F;
        for (int row = 0; row < 7; row++) {
            int inset = Math.abs(row - 3);
            int from = inset, to = row >= 2 && row <= 4 ? 7 : 4;
            if (right) {
                int mirroredFrom = 7 - to;
                to = 7 - from;
                from = mirroredFrom;
            }
            graphics.fill(x + from, y + row, x + to, y + row + 1, color);
        }
    }

    private static Component lightStatus(AutomobileEntity auto) {
        if (auto.getVehicleLightMode() == VehicleLightMode.AUTO) {
            return Component.translatable("vehicle.automobility.hud.auto_status", modeName(auto.getEffectiveVehicleLightMode()));
        }
        return modeName(auto.getEffectiveVehicleLightMode());
    }

    private static void drawLamp(GuiGraphicsExtractor graphics, int x, int y, int color, boolean high) {
        graphics.fill(x + 7, y + 1, x + 12, y + 9, color);
        graphics.fill(x + 12, y + 3, x + 14, y + 7, color);
        for (int row = 0; row < 3; row++) {
            int rayY = y + 1 + row * 3;
            graphics.fill(x, rayY + (high ? 0 : 2), x + 3, rayY + (high ? 1 : 3), color);
            graphics.fill(x + 3, rayY, x + 6, rayY + 1, color);
        }
    }
}
