package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;
import io.github.foundationgames.automobility.automobile.driving.VehicleDrivingMode;
import io.github.foundationgames.automobility.client.vehicle.MomentumVehicleCapabilities;
import io.github.foundationgames.automobility.client.vehicle.GarageTestDriveClient;
import io.github.foundationgames.automobility.client.vehicle.VehicleClientControls;
import io.github.foundationgames.automobility.client.vehicle.VehicleHudLayout;
import io.github.foundationgames.automobility.client.vehicle.VehicleHudPreferences;
import io.github.foundationgames.automobility.client.vehicle.VehicleStatusHud;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/** Native keyboard/narrator-aware controls, showing only implemented and available vehicle features. */
public final class VehicleSettingsScreen extends Screen {
    private static final int TEXT = AutomobilityUiTheme.TEXT;
    private static final int MUTED = AutomobilityUiTheme.MUTED;
    private static final int ACCENT = AutomobilityUiTheme.ACCENT;
    private final AutomobileEntity automobile;
    private final EnumMap<VehicleLightMode, Button> modeButtons = new EnumMap<>(VehicleLightMode.class);
    private Tab tab = Tab.OVERVIEW;
    private Button overviewEngine, overviewLights, overviewHazards, overviewCruise;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private boolean small;
    private Button cruiseToggle;
    private Button cruiseIncrease;
    private Button cruiseDecrease;
    private Button cruiseResume;
    private Button drivingMode;
    private Button drivingModel;
    private VehicleDrivingMode drivingModeBefore, requestedDrivingMode;
    private int drivingModeWaitTicks;
    private Button hudEnabled;
    private Button hudScale;
    private Button hudPosition;
    private Button cameraDistance;
    private Button engineToggle, engineStartMode, engineShutdown, engineLights;
    private Button hoodToggle, mx5DanceToggle;
    private Button testDriveReturn;
    private boolean testDriveContext;
    private Component feedback;
    private int feedbackTicks;
    private Component hoveredClippedText;
    private int renderMouseX;
    private int renderMouseY;

    public VehicleSettingsScreen(AutomobileEntity automobile) {
        super(text("title"));
        this.automobile = automobile;
    }

    @Override
    protected void init() {
        modeButtons.clear();
        overviewEngine = overviewLights = overviewHazards = overviewCruise = null;
        cruiseToggle = cruiseIncrease = cruiseDecrease = cruiseResume = null;
        drivingMode = null;
        drivingModel = null;
        hudEnabled = hudScale = hudPosition = cameraDistance = null;
        engineToggle = engineStartMode = engineShutdown = engineLights = null;
        hoodToggle = mx5DanceToggle = null;
        testDriveReturn = null;
        testDriveContext = GarageTestDriveClient.matchesVehicle(automobile);
        int contextHeight = testDriveContext ? 24 : 0;
        panelWidth = Math.max(1, Math.min(360, width - 16));
        panelHeight = Math.max(1, Math.min(252, height - 16 - contextHeight));
        left = (width - panelWidth) / 2;
        top = Math.max(0, (height - panelHeight - contextHeight) / 2);
        small = panelWidth < 240 || panelHeight < 224;
        if (small) {
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                    .bounds(Math.max(0, width / 2 - 60), Math.max(0, height - 26), Math.min(120, width), 20).build());
            if (testDriveContext) {
                testDriveReturn = addRenderableWidget(Button.builder(testDriveText("return"), ignored -> GarageTestDriveClient.requestReturn())
                        .bounds(Math.max(0, width / 2 - 100), Math.max(0, height - 50), Math.min(200, width), 20).build());
                updateTestDriveReturn();
            }
            return;
        }

        List<Tab> tabs = new ArrayList<>(List.of(Tab.OVERVIEW, Tab.ENGINE, Tab.LIGHTS));
        if (MomentumVehicleCapabilities.snapshot(automobile).isPresent()) tabs.add(Tab.DRIVING);
        tabs.add(Tab.DISPLAY);
        if (!tabs.contains(tab)) tab = Tab.LIGHTS;
        int tabWidth = (panelWidth - 32 - (tabs.size() - 1) * 4) / tabs.size();
        for (int index = 0; index < tabs.size(); index++) {
            Tab value = tabs.get(index);
            Button button = addRenderableWidget(Button.builder(text("tab." + value.id), ignored -> {
                tab = value;
                feedback = null;
                rebuildWidgets();
            }).bounds(left + 16 + index * (tabWidth + 4), top + 44, tabWidth, 20).build());
            button.active = value != tab;
        }

        switch (tab) {
            case OVERVIEW -> initOverview();
            case LIGHTS -> initLights();
            case ENGINE -> initEngine();
            case DRIVING -> initDriving();
            case DISPLAY -> initDisplay();
        }
        addRenderableWidget(Button.builder(text("access.general"), ignored -> minecraft.gui.setScreen(new VehicleAccessScreen(this, automobile)))
                .bounds(left + panelWidth - 84, top + 9, 68, 20).tooltip(Tooltip.create(text("access.open_help"))).build());
        int buttonWidth = (panelWidth - 40) / 3;
        addRenderableWidget(Button.builder(text("controls_button"), button ->
                minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options)))
                .bounds(left + 16, top + panelHeight - 28, buttonWidth, 20)
                .tooltip(Tooltip.create(VehicleClientControls.controlsHint())).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + 24 + buttonWidth * 2, top + panelHeight - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(text("profiles.title"), ignored -> minecraft.gui.setScreen(new VehicleProfilesScreen(this, automobile)))
                .bounds(left + 20 + buttonWidth, top + panelHeight - 28, buttonWidth, 20).build());
        if (testDriveContext) testDriveReturn = addRenderableWidget(Button.builder(testDriveText("return"), ignored -> GarageTestDriveClient.requestReturn())
                .bounds(left + 16, top + panelHeight + 4, panelWidth - 32, 20).build());
        updateState();
    }

    private void initOverview() {
        overviewEngine = addRenderableWidget(Button.builder(Component.empty(), ignored ->
                VehicleClientControls.requestIgnition(automobile, automobile.getIgnitionState() == 0))
                .bounds(left+16,top+112,panelWidth-32,20).build());
        int half = (panelWidth-36)/2;
        overviewLights = addRenderableWidget(Button.builder(Component.translatable("ui.automobility.quick_lights"), ignored ->
                VehicleClientControls.requestLightMode(automobile, io.github.foundationgames.automobility.client.vehicle.VehicleLightCycle.nextMode(automobile.getVehicleLightMode())))
                .bounds(left+16,top+137,half,20).build());
        overviewHazards = addRenderableWidget(Button.builder(Component.empty(), ignored -> VehicleClientControls.requestVehicleSignal(automobile,3))
                .bounds(left+20+half,top+137,half,20).build());
        overviewCruise = addRenderableWidget(Button.builder(text("cruise.enable"), ignored -> {
            feedback=MomentumVehicleCapabilities.perform(automobile,MomentumVehicleCapabilities.CruiseAction.TOGGLE);
            feedbackTicks=80; minecraft.getNarrator().saySystemNow(feedback);
        }).bounds(left+16,top+162,half,20).build());
        addRenderableWidget(Button.builder(Component.translatable("ui.automobility.title"), ignored ->
                minecraft.gui.setScreen(new AutomobilityOptionsScreen(this)))
                .bounds(left+20+half,top+162,half,20).build());
    }

    private void initEngine() {
        engineToggle = addRenderableWidget(Button.builder(Component.empty(), ignored ->
                VehicleClientControls.requestIgnition(automobile, automobile.getIgnitionState() ==
                        io.github.foundationgames.automobility.automobile.engine.VehicleIgnition.OFF))
                .bounds(left + 16, top + 74, panelWidth - 32, 20).build());
        engineStartMode = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            var value = automobile.getVehicleEngineSettings();
            VehicleClientControls.requestEngineSettings(automobile,
                    new io.github.foundationgames.automobility.automobile.engine.VehicleEngineSettings(!value.automaticStart(),
                            value.idleShutdownSeconds(), value.lightsWithEngine()));
        }).bounds(left + 16, top + 99, panelWidth - 32, 20).tooltip(Tooltip.create(text("engine.start_help"))).build());
        engineShutdown = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            var value = automobile.getVehicleEngineSettings();
            int next = io.github.foundationgames.automobility.automobile.engine.VehicleEngineSettings.nextDelay(
                    value.idleShutdownSeconds(), automobile.getMaximumEngineIdleSeconds(), automobile.isManualEngineShutdownAllowed());
            VehicleClientControls.requestEngineSettings(automobile,
                    new io.github.foundationgames.automobility.automobile.engine.VehicleEngineSettings(value.automaticStart(),
                            next, value.lightsWithEngine()));
        }).bounds(left + 16, top + 124, panelWidth - 32, 20).tooltip(Tooltip.create(text("engine.shutdown_help"))).build());
        engineLights = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            var value = automobile.getVehicleEngineSettings();
            VehicleClientControls.requestEngineSettings(automobile,
                    new io.github.foundationgames.automobility.automobile.engine.VehicleEngineSettings(value.automaticStart(),
                            value.idleShutdownSeconds(), !value.lightsWithEngine()));
        }).bounds(left + 16, top + 149, panelWidth - 32, 20).tooltip(Tooltip.create(text("engine.lights_help"))).build());
        if (automobile.hasOpeningHood()) hoodToggle = addRenderableWidget(Button.builder(Component.empty(), ignored ->
                VehicleClientControls.requestHood(automobile, !automobile.isHoodOpen()))
                .bounds(left + 16, top + 174, panelWidth - 32, 20).build());
    }

    private static Component shutdownName(int seconds) {
        if (seconds == -1) return text("engine.shutdown.manual");
        if (seconds == 0) return text("engine.shutdown.immediate");
        return Component.translatable("vehicle.automobility.engine.shutdown." + (seconds % 60 == 0 ? "minutes" : "seconds"),
                seconds % 60 == 0 ? seconds / 60 : seconds);
    }

    private void initLights() {
        int buttonWidth = (panelWidth - 36) / 2;
        boolean mx5 = automobile.hasOpeningHood();
        VehicleLightMode[] modes = {VehicleLightMode.OFF, VehicleLightMode.POSITION,
                VehicleLightMode.LOW, VehicleLightMode.HIGH, VehicleLightMode.AUTO};
        for (int index = 0; index < modes.length; index++) {
            VehicleLightMode mode = modes[index];
            Button button = addRenderableWidget(Button.builder(VehicleStatusHud.modeName(mode), ignored -> {
                VehicleClientControls.requestLightMode(automobile, mode);
            }).bounds(left + 16 + (index % 2) * (buttonWidth + 4), top + 74 + (index / 2) * 25,
                    index == modes.length - 1 && !mx5 ? panelWidth - 32 : buttonWidth, 20)
                    .tooltip(Tooltip.create(text("light.help." + mode.name().toLowerCase(Locale.ROOT))
                            .copy().append("\n").append(text("light.cycle_help")))).build());
            modeButtons.put(mode, button);
        }
        if (mx5) mx5DanceToggle = addRenderableWidget(Button.builder(Component.empty(), ignored ->
                VehicleClientControls.requestHeadlightDance(automobile))
                .bounds(left + 20 + buttonWidth, top + 124, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("vehicle.automobility.unattended.open"), ignored ->
                minecraft.gui.setScreen(new VehicleUnattendedLightsScreen(this, automobile)))
                .bounds(left + 16, top + 176, panelWidth - 112, 18)
                .tooltip(Tooltip.create(Component.translatable("vehicle.automobility.unattended.help"))).build());
        addRenderableWidget(Button.builder(text("signals.open"), ignored ->
                minecraft.gui.setScreen(new VehicleSignalsScreen(this, automobile)))
                .bounds(left + panelWidth - 88, top + 176, 72, 18)
                .tooltip(Tooltip.create(text("signals.open_help"))).build());
    }

    private void initDriving() {
        drivingModel = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            boolean extended = MomentumVehicleCapabilities.snapshot(automobile)
                    .map(MomentumVehicleCapabilities.Snapshot::enabled).orElse(false);
            MomentumVehicleCapabilities.requestDrivingModel(!extended);
        }).bounds(left + 16, top + 74, panelWidth - 32, 20).build());
        if (MomentumVehicleCapabilities.drivingModeSupported(automobile)) {
            drivingMode = addRenderableWidget(Button.builder(Component.empty(), ignored -> requestNextDrivingMode())
                    .bounds(left + 16, top + 99, panelWidth - 32, 20).build());
        }
        int available = panelWidth - 32;
        int sideWidth = Math.min(36, available / 8);
        int toggleWidth = (available - sideWidth * 2 - 12) / 2;
        int resumeWidth = available - toggleWidth - sideWidth * 2 - 12;
        cruiseToggle = cruiseButton("cruise.enable", MomentumVehicleCapabilities.CruiseAction.TOGGLE,
                left + 16, top + 163, toggleWidth);
        cruiseDecrease = cruiseButton("cruise.decrease", MomentumVehicleCapabilities.CruiseAction.DECREASE,
                left + 20 + toggleWidth, top + 163, sideWidth);
        cruiseIncrease = cruiseButton("cruise.increase", MomentumVehicleCapabilities.CruiseAction.INCREASE,
                left + 24 + toggleWidth + sideWidth, top + 163, sideWidth);
        cruiseResume = cruiseButton("cruise.resume", MomentumVehicleCapabilities.CruiseAction.RESUME,
                left + 28 + toggleWidth + sideWidth * 2, top + 163, resumeWidth);
    }

    private VehicleDrivingMode nextDrivingMode() {
        return (automobile.getVehicleDrivingModeCapabilities() & 2) != 0
                ? automobile.getVehicleDrivingMode().next() : VehicleDrivingMode.INHERIT;
    }

    private void requestNextDrivingMode() {
        if (requestedDrivingMode != null) return;
        var next = nextDrivingMode();
        if (next == automobile.getVehicleDrivingMode()) return;
        if (!VehicleClientControls.requestDrivingMode(automobile, next)) {
            drivingModeFeedback(text("driving.mode.rejected"));
            return;
        }
        drivingModeBefore = automobile.getVehicleDrivingMode();
        requestedDrivingMode = next;
        drivingModeWaitTicks = 100;
        feedback = text("driving.mode.waiting");
        feedbackTicks = 0;
        updateState();
    }

    private void drivingModeFeedback(Component value) {
        requestedDrivingMode = drivingModeBefore = null;
        drivingModeWaitTicks = 0;
        feedback = value;
        feedbackTicks = 100;
        minecraft.getNarrator().saySystemNow(value);
    }

    private void tickDrivingModeReply() {
        if (requestedDrivingMode == null) return;
        if (!VehicleClientControls.isDriver(minecraft, automobile)
                || !MomentumVehicleCapabilities.drivingModeSupported(automobile)) {
            drivingModeFeedback(text("driving.mode.rejected"));
            return;
        }
        var reply = VehicleClientControls.feedbackFor(automobile);
        if (automobile.getVehicleDrivingMode() == requestedDrivingMode) {
            drivingModeFeedback(Component.translatable("vehicle.automobility.access.updated"));
        } else if (automobile.getVehicleDrivingMode() != drivingModeBefore) {
            drivingModeFeedback(text("driving.mode.rejected"));
        } else if (reply != null && !reply.equals(Component.translatable("vehicle.automobility.access.updated"))) {
            drivingModeFeedback(reply);
        } else if (--drivingModeWaitTicks <= 0) {
            drivingModeFeedback(text("driving.mode.rejected"));
        }
    }

    private Button cruiseButton(String key, MomentumVehicleCapabilities.CruiseAction action, int x, int y, int buttonWidth) {
        return addRenderableWidget(Button.builder(text(key), button -> {
            feedback = MomentumVehicleCapabilities.perform(automobile, action);
            feedbackTicks = 80;
            minecraft.getNarrator().saySystemNow(feedback);
            updateState();
        }).bounds(x, y, buttonWidth, 20).tooltip(Tooltip.create(text("cruise.help"))).build());
    }

    private void initDisplay() {
        int halfWidth = (panelWidth - 36) / 2;
        hudEnabled = preferenceButton(16, 74, halfWidth, () -> {
            var current = VehicleClientControls.preferences();
            return current.withEnabled(!current.enabled());
        });
        hudScale = preferenceButton(20 + halfWidth, 74, halfWidth, () -> VehicleClientControls.preferences().nextScale());
        hudPosition = preferenceButton(panelHeight < 252 ? 96 : 100, () -> VehicleClientControls.preferences().nextPosition());
        cameraDistance = preferenceButton(16, panelHeight < 252 ? 118 : 126, halfWidth,
                () -> VehicleClientControls.preferences().nextCameraDistance());
        addRenderableWidget(Button.builder(Component.translatable("vehicle.automobility.effects.open_short"),
                ignored -> minecraft.gui.setScreen(new AutomobilityOptionsScreen(this)))
                .bounds(left + 20 + halfWidth, top + (panelHeight < 252 ? 118 : 126), halfWidth, 20)
                .tooltip(Tooltip.create(Component.translatable("vehicle.automobility.effects.local_help"))).build());
    }

    private Button preferenceButton(int y, java.util.function.Supplier<VehicleHudPreferences> value) {
        return preferenceButton(16, y, panelWidth - 32, value);
    }

    private Button preferenceButton(int x, int y, int buttonWidth, java.util.function.Supplier<VehicleHudPreferences> value) {
        return addRenderableWidget(Button.builder(Component.empty(), button -> {
            if (!VehicleClientControls.savePreferences(value.get())) {
                feedback = text("save_failed");
                feedbackTicks = 100;
                minecraft.getNarrator().saySystemNow(feedback);
            }
            updateState();
        }).bounds(left + x, top + y, buttonWidth, 20).tooltip(Tooltip.create(text("display.local_help"))).build());
    }

    @Override
    public void tick() {
        if (!VehicleClientControls.canUseMenu(minecraft, automobile)) {
            onClose();
            return;
        }
        if (testDriveContext != GarageTestDriveClient.matchesVehicle(automobile)) {
            rebuildWidgets(); return;
        }
        tickDrivingModeReply();
        if (feedbackTicks > 0 && --feedbackTicks == 0) feedback = null;
        if (tab == Tab.DRIVING && MomentumVehicleCapabilities.snapshot(automobile).isEmpty()) {
            tab = Tab.LIGHTS;
            rebuildWidgets();
        } else if (!small && tab == Tab.DRIVING
                && (drivingMode != null) != MomentumVehicleCapabilities.drivingModeSupported(automobile)) {
            rebuildWidgets();
        }
        updateState();
    }

    private void updateState() {
        updateTestDriveReturn();
        boolean canControl = VehicleClientControls.isDriver(minecraft, automobile);
        boolean canConfigure = VehicleClientControls.canConfigureDriving(automobile);
        if (overviewEngine != null) {
            overviewEngine.active = overviewLights.active = overviewHazards.active = canControl;
            overviewEngine.setMessage(Component.empty().append(text(automobile.getIgnitionState()==0 ? "engine.start" : "engine.stop"))
                    .append(" [").append(VehicleClientControls.engineBinding()).append("]"));
            overviewHazards.setMessage(Component.translatable("ui.automobility.quick_hazards",VehicleClientControls.binding(VehicleClientControls.hazardsKey())));
            var state=MomentumVehicleCapabilities.snapshot(automobile).orElse(null);
            overviewCruise.active=canControl && automobile.engineRunning() && state!=null && state.cruiseEnabled();
            overviewCruise.setMessage(text(state!=null && state.cruiseActive() ? "cruise.disable" : "cruise.enable"));
            overviewCruise.setTooltip(Tooltip.create(Component.translatable("ui.automobility.cruise_help")));
        }
        if (drivingModel != null) {
            boolean extended = MomentumVehicleCapabilities.snapshot(automobile)
                    .map(MomentumVehicleCapabilities.Snapshot::enabled).orElse(false);
            drivingModel.active = MomentumVehicleCapabilities.canChangeDrivingModel();
            drivingModel.setMessage(Component.translatableWithFallback(
                    "vehicle.automobility.driving.model.label", "Driving model: %s",
                    Component.translatableWithFallback("vehicle.automobility.driving.model."
                            + (extended ? "extended" : "original"), extended ? "Extended" : "Original")));
            drivingModel.setTooltip(Tooltip.create(Component.translatableWithFallback(
                    "vehicle.automobility.driving.model.help",
                    "Server setting. The host or an operator may switch after all loaded cars are parked.")));
        }
        if (engineToggle != null) {
            var value = automobile.getVehicleEngineSettings();
            engineToggle.active = canControl;
            engineStartMode.active = engineShutdown.active = engineLights.active = canConfigure;
            engineToggle.setMessage(text(automobile.getIgnitionState() == 0 ? "engine.start" : "engine.stop"));
            engineStartMode.setMessage(text(value.automaticStart() ? "engine.start.automatic" : "engine.start.manual"));
            engineShutdown.setMessage(Component.translatable("vehicle.automobility.engine.shutdown.label", shutdownName(value.idleShutdownSeconds())));
            engineLights.setMessage(Component.translatable("vehicle.automobility.engine.lights.label", Component.translatable(value.lightsWithEngine() ? "options.on" : "options.off")));
        }
        modeButtons.forEach((mode, button) -> button.active = canControl && automobile.getVehicleLightMode() != mode);
        if (hoodToggle != null) {
            boolean open = automobile.isHoodOpen();
            hoodToggle.setMessage(text(open ? "hood.close" : "hood.open"));
            hoodToggle.active = VehicleClientControls.canControlHood(automobile) && (open || automobile.canOpenHood());
            hoodToggle.setTooltip(Tooltip.create(text(!open && !automobile.canOpenHood() ? "hood.unavailable" : "hood.help")));
        }
        if (mx5DanceToggle != null) {
            mx5DanceToggle.setMessage(text(automobile.isHeadlightDanceActive() ? "mx5.dance.stop" : "mx5.dance.start"));
            mx5DanceToggle.active = VehicleClientControls.canControlHeadlightDance(automobile);
            mx5DanceToggle.setTooltip(Tooltip.create(Component.translatable("vehicle.automobility.mx5.dance.help",
                    VehicleClientControls.mx5HeadlightBinding())));
        }
        if (drivingMode != null) {
            var selected = automobile.getVehicleDrivingMode();
            var next = nextDrivingMode();
            drivingMode.active = requestedDrivingMode == null && next != selected
                    && VehicleClientControls.canSelectDrivingMode(automobile, next);
            drivingMode.setMessage(Component.translatable("vehicle.automobility.driving.mode.button", drivingModeName(selected)));
            Component hint = requestedDrivingMode != null ? text("driving.mode.waiting")
                    : !canConfigure ? text("driving.mode.configure_only")
                    : (automobile.getVehicleDrivingModeCapabilities() & 2) == 0 ? text("driving.mode.server_disabled")
                    : !drivingMode.active ? text("driving.mode.stopped_only") : text("driving.mode.help");
            drivingMode.setTooltip(Tooltip.create(Component.empty().append(hint).append("\n")
                    .append(Component.translatable("vehicle.automobility.driving.mode.effective",
                            effectiveDrivingModeName()))));
        }
        if (cruiseToggle != null) {
            var state = MomentumVehicleCapabilities.snapshot(automobile).orElse(null);
            boolean enabled = canControl && state != null && state.cruiseEnabled();
            cruiseToggle.active = enabled;
            cruiseToggle.setMessage(text(state != null && state.cruiseActive() ? "cruise.disable" : "cruise.enable"));
            cruiseIncrease.active = cruiseDecrease.active = enabled && state.cruiseActive();
            cruiseResume.active = enabled && state.canResume();
        }
        if (hudEnabled != null) {
            boolean writable = VehicleClientControls.preferencesWritable();
            hudEnabled.active = hudScale.active = hudPosition.active = cameraDistance.active = writable;
            var help = Tooltip.create(text(writable ? "display.local_help" : "display.unreadable"));
            hudEnabled.setTooltip(help);
            hudScale.setTooltip(help);
            hudPosition.setTooltip(help);
            cameraDistance.setTooltip(Tooltip.create(text(writable ? "display.camera_help" : "display.unreadable")));
            var value = VehicleClientControls.preferences();
            hudEnabled.setMessage(Component.translatable("vehicle.automobility.display.enabled", Component.translatable(value.enabled() ? "options.on" : "options.off")));
            hudScale.setMessage(Component.translatable("vehicle.automobility.display.scale", value.scalePercent()));
            cameraDistance.setMessage(Component.translatable("vehicle.automobility.display.camera_distance", value.cameraDistanceBlocks()));
            hudPosition.setMessage(Component.translatable("vehicle.automobility.display.position", text("position." + value.position().name().toLowerCase(Locale.ROOT))));
        }
    }

    private void updateTestDriveReturn() {
        if (testDriveReturn == null) return;
        var state = GarageTestDriveClient.availability(automobile);
        testDriveReturn.active = state.enabled();
        Component label = testDriveText(GarageTestDriveClient.returning() ? "return_waiting" : "return");
        if (!state.enabled() && !GarageTestDriveClient.returning())
            label = Component.empty().append(label).append(" · ").append(state.reason());
        testDriveReturn.setMessage(label);
        testDriveReturn.setTooltip(Tooltip.create(Component.empty().append(testDriveText("return_help"))
                .append("\n").append(state.reason())));
    }

    private static Component testDriveText(String key) {
        return Component.translatable("garage.automobility.test_drive." + key);
    }

    public Button testDriveReturnButton() { return testDriveReturn; }
    public Button buttonForGarageReturn() { return testDriveReturn; }

    @Override
    public boolean keyPressed(KeyEvent event) {
        var key = VehicleClientControls.settingsKey();
        if (key != null && key.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean isInGameUi() { return true; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        hoveredClippedText = null;
        renderMouseX = mouseX;
        renderMouseY = mouseY;
        graphics.fill(0, 0, width, height, 0x9910171D);
        if (small) {
            graphics.textWithWordWrap(font, text("too_small"), 12, 12, Math.max(1, width - 24), TEXT);
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            return;
        }
        AutomobilityUiTheme.panel(graphics,left,top,panelWidth,panelHeight);
        if (testDriveContext) {
            graphics.fill(left, top + panelHeight + 3, left + panelWidth, top + panelHeight + 25, AutomobilityUiTheme.PANEL);
            graphics.fill(left, top + panelHeight + 3, left + panelWidth, top + panelHeight + 4, 0xFFD2BA85);
        }
        graphics.fill(left + 1, top + 1, left + panelWidth - 1, top + 35, 0xFFB6B6B6);
        graphics.fill(left + 16, top + 36, left + panelWidth - 16, top + 37, 0xFF888888);
        label(graphics, title, 16, 10, panelWidth - 108, TEXT, mouseX, mouseY);
        label(graphics, automobile.getName(), 16, 23, panelWidth - 108, MUTED, mouseX, mouseY);
        switch (tab) {
            case OVERVIEW -> {
                label(graphics,Component.translatable("vehicle.automobility.hud.motor",Component.translatable("vehicle.automobility.engine.state."+automobile.getIgnitionState())),16,77,panelWidth-32,TEXT,mouseX,mouseY);
                label(graphics,VehicleStatusHud.modeName(automobile.getEffectiveVehicleLightMode()),16,92,panelWidth-32,ACCENT,mouseX,mouseY);
                if(feedback!=null && panelHeight>=248) label(graphics,feedback,16,190,panelWidth-32,TEXT,mouseX,mouseY);
            }
            case ENGINE -> {
                if (hoodToggle == null) paragraph(graphics, Component.translatable("vehicle.automobility.engine.key_hint", VehicleClientControls.engineBinding()),
                        16, 176, panelWidth - 32, panelHeight < 252 ? 1 : 2);
            }
            case LIGHTS -> {
                paragraph(graphics, text("light.help." + automobile.getVehicleLightMode().name().toLowerCase(Locale.ROOT)), 16, 152, panelWidth - 32, 1);
                label(graphics, Component.translatable("vehicle.automobility.light.effective", VehicleStatusHud.modeName(automobile.getEffectiveVehicleLightMode())),
                        16, 164, panelWidth - 32, ACCENT, mouseX, mouseY);
            }
            case DRIVING -> drawDriving(graphics, mouseX, mouseY);
            case DISPLAY -> {
                var preferences = VehicleClientControls.preferences();
                int previewTop = panelHeight < 252 ? 155 : 165;
                int previewWidth = panelWidth - 32;
                int previewHeight = panelHeight - previewTop - 32;
                var layout = VehicleHudLayout.resolvePreview(previewWidth, previewHeight, VehicleStatusHud.contentWidth(automobile), preferences);
                boolean fitted = preferences.enabled() && layout.scale() + 0.0001f < preferences.scalePercent() / 100f;
                var status = feedback == null ? text(fitted ? "display.preview_fitted" : "display.preview") : feedback;
                label(graphics, status, 16, panelHeight < 252 ? 144 : 153, panelWidth - 32, feedback == null ? MUTED : 0xFFFFB8A5, mouseX, mouseY);
                graphics.fill(left + 16, top + previewTop, left + 16 + previewWidth, top + previewTop + previewHeight, 0xFF101A20);
                graphics.outline(left + 16, top + previewTop, previewWidth, previewHeight, 0xFF314854);
                if (preferences.enabled()) {
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(left + 16, top + previewTop);
                    VehicleStatusHud.drawPanelAt(graphics, automobile, layout);
                    graphics.pose().popMatrix();
                } else {
                    label(graphics, text("display.preview_hidden"), 24, previewTop + (previewHeight - font.lineHeight) / 2,
                            previewWidth - 16, MUTED, mouseX, mouseY);
                }
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (hoveredClippedText != null) graphics.setTooltipForNextFrame(font, hoveredClippedText, mouseX, mouseY);
    }

    private void drawDriving(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        var state = MomentumVehicleCapabilities.snapshot(automobile).orElse(null);
        if (state == null) return;
        if (drivingMode != null) {
            label(graphics, Component.translatable("vehicle.automobility.driving.mode.effective",
                    effectiveDrivingModeName()),
                    16, 124, panelWidth - 32, ACCENT, mouseX, mouseY);
        } else {
            label(graphics, presetName(state.preset()), 16, 103, panelWidth - 32, ACCENT, mouseX, mouseY);
            paragraph(graphics, text(state.serverControlled() ? "driving.server_help" : "driving.global_help"),
                    16, 124, panelWidth - 32, 1);
        }
        Component status = !state.cruiseEnabled() ? text("cruise.unavailable")
                : state.cruiseActive() ? Component.translatable("vehicle.automobility.cruise.active", state.targetKmh()) : text("cruise.ready");
        label(graphics, status, 16, 145, panelWidth - 32, state.cruiseActive() ? 0xFF8ADEAE : MUTED, mouseX, mouseY);
        if (feedback != null) label(graphics, feedback, 16, 187, panelWidth - 32, TEXT, mouseX, mouseY);
    }

    static Component drivingModeName(VehicleDrivingMode mode) {
        return text("driving.mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private static Component presetName(String preset) {
        if (preset.equals("touring") || preset.equals("balanced") || preset.equals("sport"))
            return drivingModeName(VehicleDrivingMode.valueOf(preset.toUpperCase(Locale.ROOT)));
        return preset.equals("unknown") ? text("driving.unknown")
                : Component.translatable("config.momentum.driving_preset." + preset);
    }

    private Component effectiveDrivingModeName() {
        return MomentumVehicleCapabilities.snapshot(automobile).map(MomentumVehicleCapabilities.Snapshot::enabled).orElse(false)
                ? presetName(MomentumVehicleCapabilities.effectivePreset(automobile)) : Component.translatable("options.off");
    }

    private void paragraph(GuiGraphicsExtractor graphics, Component value, int x, int y, int maxWidth, int maxLines) {
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(value, maxWidth);
        for (int index = 0; index < Math.min(maxLines, lines.size()); index++) {
            graphics.text(font, lines.get(index), left + x, top + y + index * 11, MUTED, false);
        }
        if (lines.size() > maxLines && renderMouseX >= left + x && renderMouseX < left + x + maxWidth
                && renderMouseY >= top + y && renderMouseY < top + y + maxLines * 11) hoveredClippedText = value;
    }

    private void label(GuiGraphicsExtractor graphics, Component value, int x, int y, int maxWidth, int color, int mouseX, int mouseY) {
        var layout = GarageTextLayout.fit(font, value.getString(), maxWidth);
        graphics.text(font, layout.visualOrder(), left + x, top + y, color, false);
        if (layout.fullWidth() > maxWidth && mouseX >= left + x && mouseX < left + x + maxWidth
                && mouseY >= top + y && mouseY < top + y + font.lineHeight) hoveredClippedText = value;
    }

    private static Component text(String key) { return Component.translatable("vehicle.automobility." + key); }

    private enum Tab {
        OVERVIEW("overview"), ENGINE("engine"), LIGHTS("lights"), DRIVING("driving"), DISPLAY("display");
        private final String id;
        Tab(String id) { this.id = id; }
    }
}
