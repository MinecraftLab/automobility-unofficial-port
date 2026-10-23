package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.client.vehicle.VehicleClientControls;
import io.github.foundationgames.automobility.client.vehicle.VehicleClientEffects;
import io.github.foundationgames.automobility.client.vehicle.VehicleEffectsLayout;
import io.github.foundationgames.automobility.client.vehicle.VehicleEffectsPreferences;
import io.github.foundationgames.automobility.client.vehicle.VehicleLightQuality;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Locale;

/** Car-independent client options. Draft values apply only after a successful save. */
public final class VehicleEffectsScreen extends Screen {
    private final Screen parent;
    private final EnumMap<VehicleLightQuality, Button> qualityButtons = new EnumMap<>(VehicleLightQuality.class);
    private VehicleEffectsPreferences draft = VehicleClientEffects.preferences();
    private VehicleEffectsLayout layout;
    private VolumeSlider volume;
    private Button mute, save, custom;
    private int unmutedVolume = draft.signalVolumePercent() > 0 ? draft.signalVolumePercent() : 40;
    private Component feedback, hoveredText;

    public VehicleEffectsScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        qualityButtons.clear();
        layout = VehicleEffectsLayout.resolve(width, height);
        if (layout.small()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                    .bounds(Math.max(0, width / 2 - 60), Math.max(0, height - 26), Math.min(120, width), 20).build());
            return;
        }
        VehicleLightQuality[] qualities = VehicleLightQuality.displayOrder();
        for (int index = 0; index < qualities.length; index++) {
            VehicleLightQuality quality = qualities[index];
            var bounds = layout.quality(index);
            qualityButtons.put(quality, addRenderableWidget(Button.builder(Component.translatable(quality.translationKey()),
                    ignored -> { draft = draft.withQuality(quality); feedback = null; updateState(); })
                    .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height())
                    .tooltip(Tooltip.create(qualityHelp(quality))).build()));
        }
        var customBounds = layout.custom();
        custom = addRenderableWidget(Button.builder(text("custom"), ignored -> minecraft.gui.setScreen(
                new VehicleCustomGraphicsScreen(this, draft.quality(), draft.custom(), value -> {
                    draft = draft.withCustom(value); feedback = null;
                }))).bounds(customBounds.x(), customBounds.y(), customBounds.width(), customBounds.height())
                .tooltip(Tooltip.create(text("custom_help"))).build());
        var volumeBounds = layout.volume();
        volume = addRenderableWidget(new VolumeSlider(volumeBounds));
        volume.setTooltip(Tooltip.create(text("volume_help")));
        var muteBounds = layout.mute();
        mute = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            if (draft.signalVolumePercent() > 0) {
                unmutedVolume = draft.signalVolumePercent();
                draft = draft.withSignalVolumePercent(0);
            } else draft = draft.withSignalVolumePercent(unmutedVolume);
            volume.showPercent(draft.signalVolumePercent());
            feedback = null;
            updateState();
        }).bounds(muteBounds.x(), muteBounds.y(), muteBounds.width(), muteBounds.height()).build());
        var controlsBounds = layout.footer(0);
        addRenderableWidget(Button.builder(Component.translatable("vehicle.automobility.controls_button"),
                ignored -> minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options)))
                .bounds(controlsBounds.x(), controlsBounds.y(), controlsBounds.width(), controlsBounds.height())
                .tooltip(Tooltip.create(VehicleClientControls.signalsHint())).build());
        var backBounds = layout.footer(1);
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                .bounds(backBounds.x(), backBounds.y(), backBounds.width(), backBounds.height())
                .tooltip(Tooltip.create(text("cancel_help"))).build());
        var saveBounds = layout.footer(2);
        save = addRenderableWidget(Button.builder(text("save"), ignored -> {
            if (VehicleClientEffects.savePreferences(draft)) onClose();
            else {
                feedback = text("save_failed");
                minecraft.getNarrator().saySystemNow(feedback);
                updateState();
            }
        }).bounds(saveBounds.x(), saveBounds.y(), saveBounds.width(), saveBounds.height())
                .tooltip(Tooltip.create(text("apply_help"))).build());
        updateState();
    }

    private void updateState() {
        boolean writable = VehicleClientEffects.writable();
        qualityButtons.forEach((quality, button) -> button.active = writable && (draft.customEnabled() || quality != draft.quality()));
        if (custom != null) { custom.active = writable; custom.setMessage(text(draft.customEnabled() ? "custom_active" : "custom")); }
        if (volume != null) volume.active = writable;
        if (mute != null) {
            mute.active = writable;
            mute.setMessage(text(draft.signalVolumePercent() == 0 ? "unmute" : "mute"));
        }
        if (save != null) save.active = writable;
    }

    @Override public void tick() { updateState(); }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        hoveredText = null;
        graphics.fill(0, 0, width, height, 0x9910171D);
        if (layout.small()) {
            graphics.textWithWordWrap(font, Component.translatable("vehicle.automobility.too_small"),
                    12, 12, Math.max(1, width - 24), 0xFFE4EDF1);
        } else {
            graphics.fill(layout.left(), layout.top(), layout.left() + layout.width(), layout.top() + layout.height(), 0xFF162128);
            graphics.outline(layout.left(), layout.top(), layout.width(), layout.height(), 0xFF586D7A);
            graphics.fill(layout.left() + 1, layout.top() + 1, layout.left() + layout.width() - 1, layout.top() + 35, 0xFF23343F);
            label(graphics, title, 10, 0xFFE4EDF1, mouseX, mouseY);
            label(graphics, text("local"), 23, 0xFFA7BAC5, mouseX, mouseY);
            label(graphics, text("quality_label"), 42, 0xFF8DD4E7, mouseX, mouseY);
            label(graphics, text("audio_label"), 115, 0xFF8DD4E7, mouseX, mouseY);
            Component status = feedback != null ? feedback : text(VehicleClientEffects.writable() ? "apply_help" : "file_preserved");
            if (feedback == null && io.github.foundationgames.automobility.client.lighting.ComplementaryTemporalProjection.passState()
                    == io.github.foundationgames.automobility.client.lighting.VehicleRenderPassPolicy.Pass.API_UNAVAILABLE)
                status = Component.translatable("vehicle.automobility.renderer.suppressed");
            label(graphics, status, layout.height() - 43, feedback != null || !VehicleClientEffects.writable() ? 0xFFFFB8A5 : 0xFFA7BAC5, mouseX, mouseY);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (hoveredText != null) graphics.setTooltipForNextFrame(font, hoveredText, mouseX, mouseY);
    }

    private void label(GuiGraphicsExtractor graphics, Component value, int y, int color, int mouseX, int mouseY) {
        int available = layout.width() - 32;
        var fitted = GarageTextLayout.fit(font, value.getString(), available);
        graphics.text(font, fitted.visualOrder(), layout.left() + 16, layout.top() + y, color, false);
        if (fitted.fullWidth() > available && mouseX >= layout.left() + 16 && mouseX < layout.left() + layout.width() - 16
                && mouseY >= layout.top() + y && mouseY < layout.top() + y + font.lineHeight) hoveredText = value;
    }

    private void paragraph(GuiGraphicsExtractor graphics, Component value, int y, int maxLines, int mouseX, int mouseY) {
        int available = layout.width() - 32;
        var lines = font.split(value, available);
        for (int index = 0; index < Math.min(maxLines, lines.size()); index++)
            graphics.text(font, lines.get(index), layout.left() + 16, layout.top() + y + index * 11, 0xFFA7BAC5, false);
        if (lines.size() > maxLines && mouseX >= layout.left() + 16 && mouseX < layout.left() + layout.width() - 16
                && mouseY >= layout.top() + y && mouseY < layout.top() + y + maxLines * 11) hoveredText = value;
    }

    private final class VolumeSlider extends AbstractSliderButton {
        private VolumeSlider(VehicleEffectsLayout.Bounds bounds) {
            super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), Component.empty(), draft.signalVolumePercent() / 100.0);
            updateMessage();
        }

        @Override protected void updateMessage() {
            setMessage(Component.translatable("vehicle.automobility.effects.volume", (int)Math.round(value * 100)));
        }

        @Override protected void applyValue() {
            int percent = (int)Math.round(value * 100);
            draft = draft.withSignalVolumePercent(percent);
            if (percent > 0) unmutedVolume = percent;
            feedback = null;
            updateState();
        }

        private void showPercent(int percent) {
            value = percent / 100.0;
            updateMessage();
        }
    }

    private static Component qualityHelp(VehicleLightQuality quality) {
        return text("quality_help." + quality.name().toLowerCase(Locale.ROOT));
    }

    private static Component text(String key) {
        return Component.translatable("vehicle.automobility.effects." + key);
    }
}

