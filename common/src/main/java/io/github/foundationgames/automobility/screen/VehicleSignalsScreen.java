package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.automobile.light.VehicleSignalMode;
import io.github.foundationgames.automobility.client.vehicle.VehicleClientControls;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Locale;

/** Separate ordinary/three-flash actions from the vehicle's saved signal preferences. */
public final class VehicleSignalsScreen extends Screen {
    private final Screen parent;
    private final AutomobileEntity automobile;
    private final EnumMap<VehicleSignalMode, Button> buttons = new EnumMap<>(VehicleSignalMode.class);
    private Button autoCancel, comfort, comfortLeft, comfortRight;
    private int left, top, panelWidth, panelHeight, page;
    private boolean small;
    private Component hoveredText;
    private VehicleClientControls.SignalSettings pendingSettings;
    private int pendingTicks;
    private Component settingsFeedback;

    public VehicleSignalsScreen(Screen parent, AutomobileEntity automobile) {
        super(text("title"));
        this.parent = parent;
        this.automobile = automobile;
    }

    @Override
    protected void init() {
        buttons.clear();
        autoCancel = comfort = comfortLeft = comfortRight = null;
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
        for (int index = 0; index < 2; index++) {
            int target = index;
            var tab = addRenderableWidget(Button.builder(text(index == 0 ? "tab.controls" : "tab.settings"), ignored -> {
                page = target;
                rebuildWidgets();
            }).bounds(left + 16 + index * (buttonWidth + 4), top + 44, buttonWidth, 20).build());
            tab.active = page != index;
        }
        if (page == 0) initControls(buttonWidth);
        else initPreferences();
        addRenderableWidget(Button.builder(Component.translatable("vehicle.automobility.controls_button"), ignored ->
                minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options)))
                .bounds(left + 16, top + panelHeight - 28, buttonWidth, 20)
                .tooltip(Tooltip.create(allKeyHints())).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(left + 20 + buttonWidth, top + panelHeight - 28, buttonWidth, 20).build());
        updateState();
    }

    private void initControls(int buttonWidth) {
        VehicleSignalMode[] modes = {VehicleSignalMode.LEFT, VehicleSignalMode.RIGHT,
                VehicleSignalMode.HAZARD, VehicleSignalMode.OFF};
        for (int index = 0; index < modes.length; index++) {
            VehicleSignalMode mode = modes[index];
            buttons.put(mode, addRenderableWidget(Button.builder(modeName(mode),
                    ignored -> VehicleClientControls.requestVehicleSignal(automobile, mode.id()))
                    .bounds(left + 16 + index % 2 * (buttonWidth + 4), top + 74 + index / 2 * 25, buttonWidth, 20)
                    .tooltip(Tooltip.create(text("help." + mode.name().toLowerCase(Locale.ROOT)))).build()));
        }
        comfortLeft = addRenderableWidget(Button.builder(text("three_left"),
                ignored -> VehicleClientControls.requestVehicleSignal(automobile, 8))
                .bounds(left + 16, top + 124, buttonWidth, 20)
                .tooltip(Tooltip.create(text("three_help"))).build());
        comfortRight = addRenderableWidget(Button.builder(text("three_right"),
                ignored -> VehicleClientControls.requestVehicleSignal(automobile, 9))
                .bounds(left + 20 + buttonWidth, top + 124, buttonWidth, 20)
                .tooltip(Tooltip.create(text("three_help"))).build());
    }

    private void initPreferences() {
        autoCancel = addRenderableWidget(Button.builder(Component.empty(),
                ignored -> sendPreference(!automobile.isVehicleSignalAutoCancelEnabled(),
                        automobile.isVehicleComfortSignalsEnabled()))
                .bounds(left + 16, top + 74, panelWidth - 32, 20).build());
        comfort = addRenderableWidget(Button.builder(Component.empty(), ignored ->
                sendPreference(automobile.isVehicleSignalAutoCancelEnabled(),
                        !automobile.isVehicleComfortSignalsEnabled()))
                .bounds(left + 16, top + 99, panelWidth - 32, 20).build());
    }

    private void sendPreference(boolean automaticCancel, boolean threeFlash) {
        if (pendingSettings != null || !VehicleClientControls.canConfigureDriving(automobile)) return;
        pendingSettings = new VehicleClientControls.SignalSettings(automaticCancel, threeFlash, false);
        pendingTicks = 0;
        settingsFeedback = null;
        VehicleClientControls.requestVehicleSignalSettings(automobile, automaticCancel, threeFlash, false);
        updateState();
    }

    private void updateState() {
        boolean driver = VehicleClientControls.isDriver(minecraft, automobile);
        // Same-mode LEFT/RIGHT can promote a three-flash action to continuous signalling.
        for (var button : buttons.values()) button.active = driver;
        if (comfortLeft != null) {
            boolean idle = automobile.getVehicleSignalMode() == VehicleSignalMode.OFF;
            comfortLeft.active = comfortRight.active = driver && automobile.isVehicleComfortSignalsEnabled() && idle;
            var help = text(!automobile.isVehicleComfortSignalsEnabled() ? "three_disabled"
                    : !idle ? "three_busy" : "three_help");
            comfortLeft.setTooltip(Tooltip.create(help));
            comfortRight.setTooltip(Tooltip.create(help));
        }
        if (autoCancel != null) {
            updatePreference(autoCancel, "auto_cancel", "auto_cancel_help", automobile.isVehicleSignalAutoCancelEnabled());
            updatePreference(comfort, "comfort", "comfort_help", automobile.isVehicleComfortSignalsEnabled());
        }
    }

    private void updatePreference(Button button, String label, String help, boolean enabled) {
        boolean permission = VehicleClientControls.canConfigureDriving(automobile);
        button.active = permission && pendingSettings == null;
        button.setMessage(Component.translatable("vehicle.automobility.signals." + label,
                text(enabled ? "auto_cancel_on" : "auto_cancel_off")));
        button.setTooltip(Tooltip.create(text(!permission ? "auto_cancel_configure_only"
                : pendingSettings != null ? "settings_waiting" : help)));
    }

    private static Component allKeyHints() {
        return Component.empty().append(VehicleClientControls.signalsHint()).append("\n")
                .append(VehicleClientControls.comfortSignalsHint());
    }

    @Override
    public void tick() {
        if (!VehicleClientControls.canUseMenu(minecraft, automobile)) {
            minecraft.gui.setScreen(null);
            return;
        }
        if (pendingSettings != null) {
            if (pendingSettings.matches(automobile.isVehicleSignalAutoCancelEnabled(),
                    automobile.isVehicleComfortSignalsEnabled(), automobile.isVehicleLockSignalConfirmationEnabled())) {
                pendingSettings = null;
            } else if (++pendingTicks >= 40) {
                pendingSettings = null;
                settingsFeedback = text("settings_rejected");
            }
        }
        updateState();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(VehicleClientControls.canUseMenu(minecraft, automobile) ? parent : null);
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
            graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xFF162128);
            graphics.outline(left, top, panelWidth, panelHeight, 0xFF586D7A);
            graphics.fill(left + 1, top + 1, left + panelWidth - 1, top + 35, 0xFF23343F);
            label(graphics, title, 10, 0xFFE4EDF1, mouseX, mouseY);
            label(graphics, automobile.getName(), 23, 0xFFA7BAC5, mouseX, mouseY);
            label(graphics, Component.translatable("vehicle.automobility.signals.status",
                    modeName(automobile.getVehicleSignalMode())), 151, 0xFF8DD4E7, mouseX, mouseY);
            Component help = page == 1 ? pendingSettings != null ? text("settings_waiting")
                    : settingsFeedback != null ? settingsFeedback : text(VehicleClientControls.canConfigureDriving(automobile)
                    ? "settings_help" : "auto_cancel_configure_only")
                    : VehicleClientControls.isDriver(minecraft, automobile) ? allKeyHints() : text("driver_only");
            var lines = font.split(help, panelWidth - 32);
            int count = Math.min(2, lines.size());
            for (int index = 0; index < count; index++)
                graphics.text(font, lines.get(index), left + 16, top + 167 + index * 11, 0xFFA7BAC5, false);
            if (lines.size() > count && mouseX >= left + 16 && mouseX < left + panelWidth - 16
                    && mouseY >= top + 167 && mouseY < top + 167 + count * 11) hoveredText = help;
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (hoveredText != null) graphics.setTooltipForNextFrame(font, hoveredText, mouseX, mouseY);
    }

    private void label(GuiGraphicsExtractor graphics, Component value, int y, int color, int mouseX, int mouseY) {
        int available = panelWidth - 32;
        var fitted = GarageTextLayout.fit(font, value.getString(), available);
        graphics.text(font, fitted.visualOrder(), left + 16, top + y, color, false);
        if (fitted.fullWidth() > available && mouseX >= left + 16 && mouseX < left + panelWidth - 16
                && mouseY >= top + y && mouseY < top + y + font.lineHeight) hoveredText = value;
    }

    private static Component modeName(VehicleSignalMode mode) {
        return text("mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private static Component text(String key) {
        return Component.translatable("vehicle.automobility.signals." + key);
    }
}
