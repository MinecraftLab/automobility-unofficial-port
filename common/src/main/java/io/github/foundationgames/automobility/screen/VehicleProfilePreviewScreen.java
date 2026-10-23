package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.automobile.access.VehicleAccessState;
import io.github.foundationgames.automobility.client.vehicle.VehicleClientControls;
import io.github.foundationgames.automobility.client.vehicle.MomentumVehicleCapabilities;
import io.github.foundationgames.automobility.client.vehicle.VehicleConfigurationProfile;
import io.github.foundationgames.automobility.client.vehicle.VehicleProfilePreview;
import io.github.foundationgames.automobility.client.vehicle.VehicleStatusHud;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Read-only saved configuration, with one explicit application of the exact reviewed record. */
public final class VehicleProfilePreviewScreen extends Screen {
    private final VehicleProfilesScreen parent;
    private final AutomobileEntity car;
    private final VehicleConfigurationProfile profile;
    private final VehicleProfilePreview selection;
    private int left, top, panelWidth, panelHeight;
    private boolean small;
    private Button apply;
    private Component hoveredText;

    public VehicleProfilePreviewScreen(VehicleProfilesScreen parent, AutomobileEntity car,
                                       VehicleConfigurationProfile profile) {
        super(text("title"));
        this.parent = parent;
        this.car = car;
        this.selection = new VehicleProfilePreview(profile);
        this.profile = selection.profile();
    }

    @Override
    protected void init() {
        apply = null;
        panelWidth = Math.max(1, Math.min(360, width - 16));
        panelHeight = Math.max(1, Math.min(252, height - 16));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        small = panelWidth < 240 || panelHeight < 224;
        if (small) {
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                    .bounds(Math.max(0, width / 2 - 60), Math.max(0, height - 26), Math.min(120, width), 20).build());
            return;
        }
        int buttonWidth = (panelWidth - 36) / 2;
        apply = addRenderableWidget(Button.builder(text("apply"), ignored -> confirm())
                .bounds(left + 16, top + panelHeight - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(left + 20 + buttonWidth, top + panelHeight - 28, buttonWidth, 20).build());
        updateState();
    }

    private void confirm() {
        var reviewed = selection.confirm(VehicleClientControls.profiles(),
                VehicleClientControls.canUseMenu(minecraft, car) && VehicleClientControls.canApplyProfile(car, profile));
        if (reviewed == null) {
            updateState();
            return;
        }
        VehicleClientControls.applyProfile(car, reviewed);
        parent.awaitProfileReply(reviewed);
        minecraft.gui.setScreen(parent);
    }

    private void updateState() {
        if (apply == null) return;
        apply.active = selection.canApply(VehicleClientControls.profiles(),
                VehicleClientControls.canUseMenu(minecraft, car) && VehicleClientControls.canApplyProfile(car, profile));
        apply.setTooltip(Tooltip.create(!selection.available(VehicleClientControls.profiles()) ? text("changed")
                : !VehicleClientControls.canConfigureDriving(car) ? text("permission")
                : !VehicleClientControls.canApplyProfile(car, profile) ? text("driving_mode_unavailable") : text("apply_help")));
    }

    @Override
    public void tick() {
        if (!VehicleClientControls.canUseMenu(minecraft, car)) {
            minecraft.gui.setScreen(null);
            return;
        }
        updateState();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(VehicleClientControls.canUseMenu(minecraft, car) ? parent : null);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        hoveredText = null;
        graphics.fill(0, 0, width, height, 0x9910171D);
        if (small) {
            graphics.textWithWordWrap(font, Component.translatable("vehicle.automobility.too_small"),
                    12, 12, Math.max(1, width - 24), 0xFFE4EDF1);
        } else {
            // Compare one current configuration with the same immutable target being reviewed.
            var current = new VehicleConfigurationProfile("Current", car.getVehicleEngineSettings(),
                    car.getVehicleLightMode(), car.getVehicleAccess().feedback().withoutHorn(),
                    car.isVehicleSignalAutoCancelEnabled(), car.isVehicleComfortSignalsEnabled(), false,
                    car.getVehicleDrivingMode());
            var differences = selection.compare(current);
            boolean showDrivingMode = MomentumVehicleCapabilities.drivingModeSupported(car);
            int changeTotal = differences.count(showDrivingMode);
            int rowSpacing = showDrivingMode ? 14 : 16;
            graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xFF162128);
            graphics.outline(left, top, panelWidth, panelHeight, 0xFF586D7A);
            graphics.fill(left + 1, top + 1, left + panelWidth - 1, top + 35, 0xFF23343F);
            label(graphics, title, 16, 10, panelWidth - 32, 0xFFE4EDF1, mouseX, mouseY);
            Component changeCount = changeTotal == 0 ? text("no_changes")
                    : Component.translatable("vehicle.automobility.profiles.preview.change_count", changeTotal);
            int countWidth = Math.min((panelWidth - 32) / 2, font.width(changeCount));
            int countX = panelWidth - 16 - countWidth;
            label(graphics, Component.literal(profile.name()), 16, 23,
                    panelWidth - 32 - countWidth - 12, 0xFFA7BAC5, mouseX, mouseY);
            label(graphics, changeCount, countX, 23, countWidth,
                    changeTotal == 0 ? 0xFFA7BAC5 : 0xFFFFCE86, mouseX, mouseY);
            if (mouseX >= left + countX && mouseX < left + countX + countWidth
                    && mouseY >= top + 23 && mouseY < top + 23 + font.lineHeight)
                hoveredText = Component.empty().append(changeCount).append("\n").append(text("comparison_help"));
            row(graphics, "lights", VehicleStatusHud.modeName(current.lights()),
                    VehicleStatusHud.modeName(profile.lights()), differences.lights(), 49, mouseX, mouseY);
            row(graphics, "automatic_start", enabled(current.engine().automaticStart()),
                    enabled(profile.engine().automaticStart()), differences.autoStart(), 49 + rowSpacing, mouseX, mouseY);
            row(graphics, "shutdown", shutdownName(current.engine().idleShutdownSeconds()),
                    shutdownName(profile.engine().idleShutdownSeconds()), differences.idleDelay(), 49 + rowSpacing * 2, mouseX, mouseY);
            row(graphics, "lights_with_engine", enabled(current.engine().lightsWithEngine()),
                    enabled(profile.engine().lightsWithEngine()), differences.linked(), 49 + rowSpacing * 3, mouseX, mouseY);
            row(graphics, "lock_sound", feedbackName(current.feedback()), feedbackName(profile.feedback()),
                    differences.feedback(), 49 + rowSpacing * 4, mouseX, mouseY);
            row(graphics, "auto_cancel", enabled(current.signalAutoCancel()), enabled(profile.signalAutoCancel()),
                    differences.autoCancel(), 49 + rowSpacing * 5, mouseX, mouseY);
            row(graphics, "comfort", enabled(current.comfortSignals()), enabled(profile.comfortSignals()),
                    differences.comfort(), 49 + rowSpacing * 6, mouseX, mouseY);
            if (showDrivingMode) {
                row(graphics, "driving_mode", VehicleSettingsScreen.drivingModeName(current.drivingMode()),
                        VehicleSettingsScreen.drivingModeName(profile.drivingMode()), differences.drivingMode(),
                        49 + rowSpacing * 7, mouseX, mouseY);
            }
            Component help = !selection.available(VehicleClientControls.profiles()) ? text("changed")
                    : !VehicleClientControls.canConfigureDriving(car) ? text("permission")
                    : !VehicleClientControls.canApplyProfile(car, profile) ? text("driving_mode_unavailable") : text("help");
            var lines = font.split(help, panelWidth - 32);
            int count = Math.min(2, lines.size());
            for (int index = 0; index < count; index++)
                graphics.text(font, lines.get(index), left + 16, top + 167 + index * 11, 0xFFA7BAC5, false);
            if (lines.size() > count && mouseX >= left + 16 && mouseX < left + panelWidth - 16
                    && mouseY >= top + 167 && mouseY < top + 167 + count * 11) hoveredText = help;
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (hoveredText != null) {
            var tooltipLines = font.split(hoveredText, Math.max(1, Math.min(240, width - 24)));
            graphics.setTooltipForNextFrame(font, tooltipLines, mouseX, mouseY);
        }
    }

    private void row(GuiGraphicsExtractor graphics, String key, Component current, Component saved,
                     boolean changed, int y, int mouseX, int mouseY) {
        int available = panelWidth - 32;
        Component value = changed ? Component.translatable("vehicle.automobility.profiles.preview.transition", current, saved)
                : saved;
        int valueWidth = Math.min(available / 2, font.width(value));
        if (changed) graphics.fill(left + 9, top + y + 2, left + 12, top + y + 7, 0xFFE4B46B);
        label(graphics, text(key), 16, y, available - valueWidth - 12,
                changed ? 0xFFE4B46B : 0xFF84949D, mouseX, mouseY);
        label(graphics, value, panelWidth - 16 - valueWidth, y, valueWidth,
                changed ? 0xFFFFCE86 : 0xFFA7BAC5, mouseX, mouseY);
        if (changed && mouseX >= left + 9 && mouseX < left + panelWidth - 16
                && mouseY >= top + y && mouseY < top + y + font.lineHeight)
            hoveredText = Component.translatable("vehicle.automobility.profiles.preview.difference_detail",
                    text(key), current, saved);
    }

    private void label(GuiGraphicsExtractor graphics, Component value, int x, int y, int available,
                       int color, int mouseX, int mouseY) {
        var fitted = GarageTextLayout.fit(font, value.getString(), available);
        graphics.text(font, fitted.visualOrder(), left + x, top + y, color, false);
        if (fitted.fullWidth() > available && mouseX >= left + x && mouseX < left + x + available
                && mouseY >= top + y && mouseY < top + y + font.lineHeight) hoveredText = value;
    }

    private static Component enabled(boolean value) {
        return Component.translatable("vehicle.automobility.signals.auto_cancel_" + (value ? "on" : "off"));
    }

    private static Component feedbackName(VehicleAccessState.Feedback feedback) {
        return Component.translatable("vehicle.automobility.access.sound." + (feedback.click() ? "click" : "silent"));
    }

    private static Component shutdownName(int seconds) {
        if (seconds < 0) return text("shutdown.manual");
        if (seconds == 0) return text("shutdown.immediate");
        return Component.translatable("vehicle.automobility.profiles.preview.shutdown."
                + (seconds % 60 == 0 ? "minutes" : "seconds"), seconds % 60 == 0 ? seconds / 60 : seconds);
    }

    private static Component text(String key) {
        return Component.translatable("vehicle.automobility.profiles.preview." + key);
    }
}
