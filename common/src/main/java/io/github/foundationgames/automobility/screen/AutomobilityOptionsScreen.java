package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.client.vehicle.*;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import java.util.EnumMap;
import java.util.Locale;

/** Car-independent settings; every category owns and saves only its own draft. */
public final class AutomobilityOptionsScreen extends Screen {
    public enum Page { GRAPHICS, HUD, SOUND, CONTROLS }
    private final Screen parent;
    private final Page page;
    private VehicleEffectsPreferences effects = VehicleClientEffects.preferences();
    private VehicleHudPreferences hud = VehicleClientControls.preferences();
    private VehicleUiSettings layoutDraft=VehicleUiState.current();
    private Button hintsMode,legacy,energy;
    private final EnumMap<VehicleLightQuality, Button> qualities = new EnumMap<>(VehicleLightQuality.class);
    private int left, top, panelWidth, panelHeight;
    private boolean small;
    private Button save, custom, status, scale, position, camera, mute;
    private SignalVolume signalVolume;
    private VehicleSettingsLayout layout;
    private Component feedback;

    public AutomobilityOptionsScreen(Screen parent) { this(parent, Page.GRAPHICS); }
    public AutomobilityOptionsScreen(Screen parent, Page page) {
        super(ui("title")); this.parent = parent; this.page = page;
    }

    @Override protected void init() {
        qualities.clear(); save = custom = status = scale = position = camera = energy = null;
        layout = VehicleSettingsLayout.resolve(width,height);
        panelWidth=layout.width(); panelHeight=layout.height(); left=layout.left(); top=layout.top(); small=layout.small();
        mute=null; signalVolume=null;
        if (small) {
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), ignored -> onClose())
                    .bounds(Math.max(0, width / 2 - 60), Math.max(0, height - 24), Math.min(width,120),20).build());
            return;
        }
        int tabWidth = (panelWidth - 32 - 9) / 4;
        for (var value : Page.values()) {
            var button = button(ui("page." + value.name().toLowerCase(Locale.ROOT)), 16 + value.ordinal() * (tabWidth + 3), 31, tabWidth,
                    ignored -> minecraft.gui.setScreen(new AutomobilityOptionsScreen(parent, value)));
            button.active = value != page;
            button.setTooltip(Tooltip.create(ui("page_switch_help")));
        }
        switch (page) {
            case GRAPHICS -> initGraphics();
            case HUD -> initHud();
            case SOUND -> initSound();
            case CONTROLS -> initControls();
        }
        int footerY = layout.footerY();
        button(Component.translatable("gui.cancel"), panelWidth - 192, footerY, 80, ignored -> onClose());
        if (page != Page.CONTROLS) save = button(ui("save"), panelWidth - 108, footerY, 92, ignored -> savePage());
        else button(Component.translatable("gui.done"), panelWidth - 108, footerY, 92, ignored -> onClose());
        updateState();
    }

    private void initGraphics() {
        var values = VehicleLightQuality.displayOrder();
        int itemWidth = (panelWidth - 32 - 9) / 4;
        for (int i = 0; i < values.length; i++) {
            var value = values[i];
            var button = button(Component.translatable(value.translationKey()), 16 + i * (itemWidth + 3), 78, itemWidth,
                    ignored -> { effects = effects.withQuality(value); feedback = null; updateState(); });
            button.setTooltip(Tooltip.create(effect("quality_help." + value.name().toLowerCase(Locale.ROOT))));
            qualities.put(value, button);
        }
        custom = button(effect("custom"), 16, 103, panelWidth - 32, ignored -> minecraft.gui.setScreen(
                new VehicleCustomGraphicsScreen(this, effects.quality(), effects.custom(), value -> {
                    effects = effects.withCustom(value); feedback = null;
                })));
        custom.setTooltip(Tooltip.create(effect("custom_help")));
        button(ui("open_hud"),16,layout.graphicsLinkY(),panelWidth-32, ignored -> minecraft.gui.setScreen(new AutomobilityOptionsScreen(parent, Page.HUD)))
                .setTooltip(Tooltip.create(ui("page_switch_help")));
    }

    private void initHud() {
        int half = (panelWidth - 36) / 2;
        status = button(Component.empty(),16,75,half, ignored -> { hud = hud.withEnabled(!hud.enabled()); updateState(); });
        scale = button(Component.empty(),20+half,75,half, ignored -> { hud = hud.nextScale(); updateState(); });
        position = button(ui("open_hud"),16,101,half, ignored -> minecraft.gui.setScreen(new VehicleHudEditorScreen(this,(layoutDraft.legacyLayout()?layoutDraft.resetLayout():layoutDraft).withBasic(hud),value -> {
            layoutDraft=value;hud=value.basic();updateState();
        })));
        hintsMode=button(Component.empty(),16,127,half,ignored -> {
            var values=VehicleUiSettings.HintMode.values();layoutDraft=layoutDraft.withHintMode(values[(layoutDraft.hintMode().ordinal()+1)%values.length]);updateState();
        });
        legacy=button(Component.empty(),20+half,127,half,ignored -> {
            layoutDraft=layoutDraft.legacyLayout()?layoutDraft.resetLayout():layoutDraft.restorePrevious();hud=layoutDraft.basic();updateState();
        });
        hintsMode.setTooltip(Tooltip.create(ui("hints_help")));legacy.setTooltip(Tooltip.create(ui("legacy_help")));
        camera = button(Component.empty(),20+half,101,half, ignored -> { hud = hud.nextCameraDistance(); updateState(); });
        camera.setTooltip(Tooltip.create(Component.translatable("vehicle.automobility.display.camera_help")));
        energy=button(Component.empty(),16,153,panelWidth-32,ignored -> {
            layoutDraft=layoutDraft.withEnergyEnabled(!layoutDraft.energyEnabled());updateState();
        });
        energy.setTooltip(Tooltip.create(ui("energy.help")));
    }

    private void initSound() {
        signalVolume=addRenderableWidget(new SignalVolume(left+16,top+87,panelWidth-32));
        mute=button(effect(effects.signalVolumePercent()==0 ? "unmute" : "mute"),16,113,panelWidth-32, ignored -> {
            effects = effects.withSignalVolumePercent(effects.signalVolumePercent()==0 ? 40 : 0);
            signalVolume.showDraft(); feedback = null; updateState();
        });
    }

    private void initControls() {
        button(ui("keybinds"),16,76,panelWidth-32, ignored -> minecraft.gui.setScreen(new KeyBindsScreen(this,minecraft.options)));
        button(ui("keys.title"),16,100,panelWidth-32,ignored -> minecraft.gui.setScreen(new VehicleKeyConflictScreen(this)));
        var advanced = button(Component.translatable("options.automobility.advanced"),16,124,panelWidth-32, ignored -> openAdvanced());
        advanced.active = currentCar() == null && FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3");
        advanced.setTooltip(Tooltip.create(Component.translatable("options.automobility." +
                (currentCar() != null ? "advanced_park" : "advanced_help"))));
        button(Component.translatable("vehicle.automobility.renderer.title"),16,148,panelWidth-32,
                ignored -> minecraft.gui.setScreen(new VehicleRendererSupportScreen(this)));
    }

    private void savePage() {
        boolean saved;
        if (page == Page.HUD) saved = VehicleUiState.save(layoutDraft.withBasic(hud));
        else {
            var current = VehicleClientEffects.preferences();
            var candidate = page == Page.SOUND ? VehicleSettingsDrafts.sound(current,effects)
                    : VehicleSettingsDrafts.graphics(current,effects);
            saved = VehicleClientEffects.savePreferences(candidate);
        }
        if (saved) onClose();
        else { feedback = effect("save_failed"); minecraft.getNarrator().saySystemNow(feedback); updateState(); }
    }

    private void updateState() {
        boolean writable = page == Page.HUD ? VehicleClientControls.preferencesWritable() : VehicleClientEffects.writable();
        if (save != null) save.active = writable;
        if (signalVolume != null) signalVolume.active = writable;
        if (mute != null) { mute.active=writable; mute.setMessage(effect(effects.signalVolumePercent()==0 ? "unmute" : "mute")); }
        qualities.forEach((value, button) -> {
            button.active = writable;
            button.setMessage(Component.translatable(value.translationKey()).withStyle(
                    !effects.customEnabled() && effects.quality() == value ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        });
        if (custom != null) { custom.active = writable; custom.setMessage(effect(effects.customEnabled() ? "custom_active" : "custom")); }
        if (status != null) {
            status.active = scale.active = position.active = camera.active = writable;
            status.setMessage(Component.translatable("vehicle.automobility.display.enabled",Component.translatable(hud.enabled() ? "options.on" : "options.off")));
            scale.setMessage(Component.translatable("vehicle.automobility.display.scale",hud.scalePercent()));
            position.setMessage(ui("open_hud"));
            hintsMode.active=legacy.active=writable;
            hintsMode.setMessage(Component.translatable("ui.automobility.hints_mode",ui("hints."+layoutDraft.hintMode().name().toLowerCase(Locale.ROOT))));
            legacy.setMessage(ui(layoutDraft.legacyLayout()?"use_new_layout":"restore_layout"));
            camera.setMessage(Component.translatable("vehicle.automobility.display.camera_distance",hud.cameraDistanceBlocks()));
            energy.active=writable;
            energy.setMessage(Component.translatable("ui.automobility.energy.enabled",Component.translatable(layoutDraft.energyEnabled()?"options.on":"options.off")));
        }
    }

    private AutomobileEntity currentCar() {
        return minecraft.player != null && minecraft.player.getVehicle() instanceof AutomobileEntity car
                && VehicleClientControls.isDriver(minecraft,car) ? car : null;
    }
    private void openAdvanced() {
        if (currentCar() != null || !FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) return;
        try {
            var type = Class.forName("io.github.milkucha.momentum.config.MomentumConfigScreen");
            minecraft.gui.setScreen((Screen)type.getMethod("create",Screen.class).invoke(null,this));
        } catch (ReflectiveOperationException | LinkageError error) {
            feedback = Component.translatable("options.automobility.advanced_unavailable");
            Automobility.LOG.warn("Could not open optional advanced driving settings",error);
        }
    }
    private Button button(Component title,int x,int y,int w,Button.OnPress action) {
        return addRenderableWidget(Button.builder(title,action).bounds(left+x,top+y,w,20).build());
    }
    @Override public void tick() { updateState(); }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return minecraft != null && minecraft.level != null; }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
        graphics.fill(0,0,width,height,0x88101010);
        if (small) graphics.textWithWordWrap(font,Component.translatable("vehicle.automobility.too_small"),12,12,Math.max(1,width-24),0xFFFFFFFF);
        else {
            AutomobilityUiTheme.panel(graphics,left,top,panelWidth,panelHeight);
            graphics.text(font,title,left+16,top+13,AutomobilityUiTheme.TEXT,false);
            if (page == Page.GRAPHICS) {
                label(graphics,effect("quality_label"),64);
                var help = effects.customEnabled() ? effect("custom_active") : effect("quality_help."+effects.quality().name().toLowerCase(Locale.ROOT));
                var lines=font.split(help,panelWidth-32);
                for(int i=0;i<Math.min(2,lines.size());i++) graphics.text(font,lines.get(i),left+16,top+126+i*11,AutomobilityUiTheme.MUTED,false);
                if(lines.size()>2 && mouseX>=left+16 && mouseX<left+panelWidth-16 && mouseY>=top+126 && mouseY<top+148)
                    graphics.setTooltipForNextFrame(font,help,mouseX,mouseY);
            } else if (page == Page.HUD) {
                label(graphics,ui("hud_help"),62);
                // The independent energy toggle owns this row; draft/save semantics are in its tooltip.
            } else if (page == Page.SOUND) {
                label(graphics,effect("audio_label"),65);
                graphics.textWithWordWrap(font,effect("volume_help"),left+16,top+145,panelWidth-32,AutomobilityUiTheme.MUTED);
            } else label(graphics,ui("controls_help"),64);
            AutomobilityUiTheme.divider(graphics,left+16,top+layout.dividerY(),panelWidth-32);
            label(graphics,feedback != null ? feedback : page == Page.CONTROLS ? ui("remaps_preserved") : ui("local"),panelHeight-43);
        }
        super.extractRenderState(graphics,mouseX,mouseY,delta);
    }
    private void label(GuiGraphicsExtractor graphics,Component value,int y) {
        var fitted = GarageTextLayout.fit(font,value.getString(),panelWidth-32);
        graphics.text(font,fitted.visualOrder(),left+16,top+y,AutomobilityUiTheme.TEXT,false);
    }
    private final class SignalVolume extends AbstractSliderButton {
        SignalVolume(int x,int y,int w) { super(x,y,w,20,Component.empty(),effects.signalVolumePercent()/100.0); updateMessage(); active=VehicleClientEffects.writable(); }
        @Override protected void updateMessage() { setMessage(Component.translatable("vehicle.automobility.effects.volume",(int)Math.round(value*100))); }
        void showDraft() { value=effects.signalVolumePercent()/100.0; updateMessage(); }
        @Override protected void applyValue() { effects=effects.withSignalVolumePercent((int)Math.round(value*100)); feedback=null; updateState(); }
    }
    private static Component ui(String key) { return Component.translatable("ui.automobility."+key); }
    private static Component effect(String key) { return Component.translatable("vehicle.automobility.effects."+key); }
}
