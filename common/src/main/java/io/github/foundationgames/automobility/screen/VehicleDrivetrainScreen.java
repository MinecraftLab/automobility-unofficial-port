package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.automobile.drivetrain.*;
import io.github.foundationgames.automobility.client.vehicle.VehicleClientControls;
import io.github.foundationgames.automobility.client.vehicle.VehicleDrivetrainSettingsClient;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/** Vehicle preferences are a local draft until the server accepts the explicit Apply action. */
public final class VehicleDrivetrainScreen extends Screen {
    private final Screen parent;
    private final AutomobileEntity car;
    private DrivetrainSettingsEnvelope.Settings draft = DrivetrainSettingsPolicy.defaultSettings();
    private UUID seenReply;
    private boolean dirty, resetUnrecognized, small;
    private int left, top, panelWidth, panelHeight, ticks;
    private Button mode, assistance, release, slew, stall, protection, apply, reset;
    private DrivetrainSettingsMessages.Status status;
    public VehicleDrivetrainScreen(Screen parent, AutomobileEntity car) {
        super(text("title", "Drivetrain")); this.parent = parent; this.car = car;
        VehicleDrivetrainSettingsClient.query(car);
    }
    @Override protected void init() {
        mode = assistance = release = slew = stall = protection = apply = reset = null;
        panelWidth = Math.max(1, Math.min(360, width - 16)); panelHeight = Math.max(1, Math.min(252, height - 16));
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2; small = panelWidth < 260 || panelHeight < 222;
        if (!small) {
            int half = (panelWidth - 36) / 2;
            boolean compact = panelHeight < 244;
            int firstRow = compact ? 62 : 70, rowStep = compact ? 22 : 25;
            mode = addRenderableWidget(Button.builder(Component.empty(), button -> change(new DrivetrainSettingsEnvelope.Settings(
                    draft.transmissionMode() == DrivetrainSettingsEnvelope.TransmissionMode.AUTOMATIC ? DrivetrainSettingsEnvelope.TransmissionMode.MANUAL : DrivetrainSettingsEnvelope.TransmissionMode.AUTOMATIC,
                    draft.clutchReleaseSeconds(), draft.analogSlewSeconds(), draft.assistanceStrength(), draft.stallEnabled(), draft.downshiftProtection())))
                    .bounds(left + 16, top + firstRow, half, 20).build());
            mode.setTooltip(Tooltip.create(text("mode.help", "Stop, select neutral and release the accelerator before changing the transmission mode. Use Keys to bind the range controls.")));
            assistance = addRenderableWidget(Button.builder(Component.empty(), button -> change(new DrivetrainSettingsEnvelope.Settings(draft.transmissionMode(),
                    draft.clutchReleaseSeconds(), draft.analogSlewSeconds(), next(draft.assistanceStrength(), new double[]{0, .25, .5, .75, 1}).floatValue(), draft.stallEnabled(), draft.downshiftProtection())))
                    .bounds(left + 20 + half, top + firstRow, half, 20).build());
            assistance.setTooltip(Tooltip.create(text("assistance.help", "Manual clutch, launch, anti-stall and rev matching assistance. Holding the clutch always disengages it.")));
            release = button(firstRow + 1 * rowStep, () -> change(new DrivetrainSettingsEnvelope.Settings(draft.transmissionMode(),
                    next(draft.clutchReleaseSeconds(), new double[]{.15, .35, .6, .8, 1.2, 2}), draft.analogSlewSeconds(), draft.assistanceStrength(), draft.stallEnabled(), draft.downshiftProtection())));
            release.setTooltip(Tooltip.create(text("release.help", "After releasing the digital clutch key, the clutch engages over this time. Hold it to disengage immediately.")));
            slew = button(firstRow + 2 * rowStep, () -> change(new DrivetrainSettingsEnvelope.Settings(draft.transmissionMode(), draft.clutchReleaseSeconds(),
                    next(draft.analogSlewSeconds(), new double[]{0, .1, .25, .5, 1}), draft.assistanceStrength(), draft.stallEnabled(), draft.downshiftProtection())));
            slew.setTooltip(Tooltip.create(text("slew.help", "Optional smoothing for an analog pedal. Zero follows the actual pedal directly.")));
            stall = button(firstRow + 3 * rowStep, () -> change(new DrivetrainSettingsEnvelope.Settings(draft.transmissionMode(), draft.clutchReleaseSeconds(), draft.analogSlewSeconds(),
                    draft.assistanceStrength(), !draft.stallEnabled(), draft.downshiftProtection())));
            protection = button(firstRow + 4 * rowStep, () -> change(new DrivetrainSettingsEnvelope.Settings(draft.transmissionMode(), draft.clutchReleaseSeconds(), draft.analogSlewSeconds(),
                    draft.assistanceStrength(), draft.stallEnabled(), !draft.downshiftProtection())));
            protection.setTooltip(Tooltip.create(text("protection.help", "Reject a gear that would over-rev the engine. Direction and parking interlocks always remain active.")));
            int third = (panelWidth - 40) / 3;
            reset = addRenderableWidget(Button.builder(text("reset", "Reset"), button -> {
                draft = DrivetrainSettingsPolicy.defaultSettings(); resetUnrecognized = status == DrivetrainSettingsMessages.Status.UNRECOGNIZED; dirty = true; update();
            }).bounds(left + 16, top + panelHeight - 28, third, 20).build());
            apply = addRenderableWidget(Button.builder(text("apply", "Apply"), button -> {
                if (VehicleDrivetrainSettingsClient.save(car, draft, resetUnrecognized)) update();
            }).bounds(left + 20 + third, top + panelHeight - 28, third, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                    .bounds(left + 24 + 2 * third, top + panelHeight - 28, third, 20).build());
            addRenderableWidget(Button.builder(text("keys", "Keys…"), button -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options)))
                    .bounds(left + panelWidth - 70, top + 8, 54, 20).build());
        } else addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(Math.max(0, width / 2 - 60), Math.max(0, height - 26), Math.min(120, width), 20).build());
        update();
    }
    private Button button(int y, Runnable action) { return addRenderableWidget(Button.builder(Component.empty(), button -> action.run()).bounds(left + 16, top + y, panelWidth - 32, 20).build()); }
    private void change(DrivetrainSettingsEnvelope.Settings value) { draft = value; dirty = true; update(); }
    private static Double next(double value, double[] choices) { for (double choice : choices) if (choice > value + 1e-6) return choice; return choices[0]; }
    private void update() {
        var view = VehicleDrivetrainSettingsClient.view(car);
        if (view.isPresent()) {
            var received = view.get(); status = received.status();
            if (!received.request().equals(seenReply)) {
                seenReply = received.request();
                if (!dirty || status == DrivetrainSettingsMessages.Status.SAVED) received.settings().ifPresent(value -> { draft = value; dirty = false; resetUnrecognized = false; });
                if (status == DrivetrainSettingsMessages.Status.SAVED && minecraft != null) minecraft.getNarrator().saySystemNow(text("saved", "Drivetrain settings saved."));
            }
        }
        if (mode == null) return;
        boolean editable = view.isPresent() && view.get().mayConfigure()
                && (view.get().settings().isPresent() || resetUnrecognized) && !VehicleDrivetrainSettingsClient.saving(car);
        mode.active = assistance.active = release.active = slew.active = stall.active = protection.active = editable;
        reset.active = view.isPresent() && view.get().mayConfigure() && !VehicleDrivetrainSettingsClient.saving(car);
        apply.active = editable && dirty && !VehicleDrivetrainSettingsClient.pending(car);
        mode.setMessage(text(draft.transmissionMode() == DrivetrainSettingsEnvelope.TransmissionMode.AUTOMATIC ? "automatic" : "manual", draft.transmissionMode() == DrivetrainSettingsEnvelope.TransmissionMode.AUTOMATIC ? "Automatic" : "Manual"));
        assistance.setMessage(label("assistance", "Assistance", String.format(Locale.ROOT, "%.0f%%", draft.assistanceStrength() * 100)));
        release.setMessage(label("release", "Clutch release", String.format(Locale.ROOT, "%.2f s", draft.clutchReleaseSeconds())));
        slew.setMessage(label("slew", "Analog smoothing", String.format(Locale.ROOT, "%.2f s", draft.analogSlewSeconds())));
        stall.setMessage(option("stall", "Engine stalling", draft.stallEnabled()));
        protection.setMessage(option("protection", "Downshift protection", draft.downshiftProtection()));
    }
    @Override public void tick() {
        if (!VehicleClientControls.canUseMenu(minecraft, car)) { minecraft.setScreen(null); return; }
        if (++ticks % 20 == 0) VehicleDrivetrainSettingsClient.query(car);
        update();
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0x9910171d);
        if (small) graphics.textWithWordWrap(font, Component.translatable("vehicle.automobility.too_small"), 12, 12, Math.max(1, width - 24), 0xffe4edf1);
        else {
            AutomobilityUiTheme.panel(graphics, left, top, panelWidth, panelHeight);
            AutomobilityUiTheme.divider(graphics, left + 16, top + 33, panelWidth - 32);
            graphics.text(font, title, left + 16, top + 10, AutomobilityUiTheme.TEXT, false);
            Component state = status == null ? text("waiting", "Waiting for the server…") : switch (status) {
                        case CURRENT -> text("current", "Change settings while stopped, then apply.");
                        case SAVED -> text("saved", "Drivetrain settings saved.");
                        case MOVING -> text("moving", "Stop the vehicle and its driven wheels first.");
                        case NEUTRAL_REQUIRED -> text("neutral_required", "Select neutral before changing the transmission mode.");
                        case UNRECOGNIZED -> text("unrecognized", "Unrecognized settings. Reset explicitly to replace them.");
                        case STALE -> text("stale", "Vehicle settings changed; review and apply again.");
                        case FORBIDDEN -> text("forbidden", "Vehicle configuration permission is required.");
                        case POLICY_UNAVAILABLE -> text("policy_unavailable", "Server drivetrain settings are unavailable.");
                    };
            if (!dirty && !VehicleDrivetrainSettingsClient.pending(car)
                    && (status == DrivetrainSettingsMessages.Status.SAVED || status == DrivetrainSettingsMessages.Status.CURRENT)) {
                var expected = draft.transmissionMode() == DrivetrainSettingsEnvelope.TransmissionMode.AUTOMATIC ? PowertrainSimulation.ControlMode.AUTOMATIC
                        : draft.assistanceStrength() > 0 ? PowertrainSimulation.ControlMode.MANUAL_ASSISTED : PowertrainSimulation.ControlMode.MANUAL_CLUTCH;
                if (io.github.foundationgames.automobility.client.vehicle.VehiclePowertrainPresentation.reading(car)
                        .map(value -> !value.electric() && value.snapshot().controlMode() != expected).orElse(false)) {
                    state = text("mode_pending", "Saved. Select neutral and release the accelerator to activate the mode.");
                }
            }
            var lines = font.split(state, panelWidth - 32);
            for (int index = 0; index < Math.min(2, lines.size()); index++) graphics.text(font, lines.get(index), left + 16, top + 41 + index * 10, AutomobilityUiTheme.MUTED, false);
            var policyLines = font.split(text("policy_note", "Server rules apply to saved settings."), panelWidth - 32);
            int policyY = panelHeight < 244 ? 174 : 198;
            for (int index = 0; index < Math.min(2, policyLines.size()); index++) graphics.text(font, policyLines.get(index), left + 16, top + policyY + index * 9, AutomobilityUiTheme.MUTED, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
    private static Component option(String key, String fallback, boolean value) { return text(key, fallback).copy().append(": ").append(Component.translatable(value ? "options.on" : "options.off")); }
    private static Component label(String key, String fallback, String value) { return text(key, fallback).copy().append(": " + value); }
    private static Component text(String key, String fallback) { return Component.translatableWithFallback("vehicle.automobility.drivetrain." + key, fallback); }
}
