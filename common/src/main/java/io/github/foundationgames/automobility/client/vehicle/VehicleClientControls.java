package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.screen.VehicleSettingsScreen;
import io.github.foundationgames.automobility.util.network.ClientPackets;
import io.github.foundationgames.automobility.util.network.content.CoreClientContentSession;
import io.github.foundationgames.automobility.util.network.content.CoreContentProtocol;
import io.github.foundationgames.automobility.util.network.content.Mx5Action;
import io.github.foundationgames.automobility.util.network.content.Mx5MovingPartsSync;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.io.IOException;

/** Menus select nearby cars; the server authorizes every operation and gameplay state. */
public final class VehicleClientControls {
    private static KeyMapping settingsKey;
    private static KeyMapping lightKey;
    private static KeyMapping highBeamKey;
    private static KeyMapping engineKey;
    private static KeyMapping mx5HeadlightKey;
    private static final Mx5HeadlightKeyEdge mx5HeadlightEdge = new Mx5HeadlightKeyEdge();
    private static KeyMapping signalLeftKey, signalRightKey, hazardsKey;
    private static KeyMapping comfortLeftKey, comfortRightKey;
    private static final VehicleLightCycle lightCycle = new VehicleLightCycle();
    private static long controlTick;
    private static VehicleHudPreferences preferences = VehicleHudPreferences.defaults();
    private static VehicleHudPreferenceStore store;
    private static PendingVehicleMenu<ClientLevel,LocalPlayer,AutomobileEntity> pendingMenu;
    private static KeyMapping lockKey;
    private static VehicleProfileStore profileStore;
    private static java.util.List<VehicleConfigurationProfile> profiles = java.util.List.of();
    private static boolean profilesReadable = true;
    private static Component actionFeedback;
    private static String feedbackKey;
    private static int feedbackEntity, feedbackTicks;
    private static net.minecraft.client.multiplayer.ClientLevel feedbackLevel;

    private VehicleClientControls() {}

    public static void initialize(KeyMapping settings, KeyMapping lights, KeyMapping highBeam, KeyMapping engine) {
        engineKey = engine;
        settingsKey = settings;
        lightKey = lights;
        highBeamKey = highBeam;
        lightCycle.reset();
        controlTick = 0;
        pendingMenu = null;
        preferences = VehicleHudPreferences.defaults();
        VehicleClientEffects.initialize();
        store = new VehicleHudPreferenceStore(FabricLoader.getInstance().getConfigDir().resolve("automobility-vehicle-ui.json"));
        profileStore = new VehicleProfileStore(FabricLoader.getInstance().getConfigDir().resolve("automobility-vehicle-profiles.json"));
        try { profiles = profileStore.load(); }
        catch (IOException error) { profilesReadable = false; Automobility.LOG.warn("Could not read vehicle profiles; preserving the file", error); }
        try {
            preferences = store.load();
        } catch (IOException error) {
            Automobility.LOG.warn("Could not read vehicle UI preferences; using defaults and preserving the file", error);
        }
        VehicleUiState.initialize(FabricLoader.getInstance().getConfigDir(),preferences,store.readable());
    }

    public static void tick(Minecraft client) {
        VehicleClientEffects.tick(client);
        UnifiedVehicleHud.tick(client);
        GarageTestDriveClient.tick(client);
        if (feedbackTicks > 0 && --feedbackTicks == 0) actionFeedback = null;
        if (pendingMenu != null && pendingMenu.evaluate(client.level, client.player, client.screen, controlTick,
                client.player != null && client.player.isAlive() && !client.player.isRemoved() && client.player.level() == client.level,
                pendingMenu.kind() == PendingVehicleMenu.Kind.EFFECTS || currentMenuTarget(client, pendingMenu.vehicle()))
                == PendingVehicleMenu.State.DISCARD) pendingMenu = null;
        boolean openKey = consume(settingsKey);
        boolean open = openKey || pendingMenu != null;
        boolean toggle = consume(lightKey);
        int lightCycleClicks = consumeClicks(highBeamKey);
        boolean ignition = consume(engineKey);
        boolean locking = consume(lockKey);
        int leftSignals = consumeClicks(signalLeftKey);
        int rightSignals = consumeClicks(signalRightKey);
        int hazards = consumeClicks(hazardsKey);
        int comfortLeft = consumeClicks(comfortLeftKey);
        int comfortRight = consumeClicks(comfortRightKey);
        int mx5Clicks = consumeClicks(mx5HeadlightKey);
        AutomobileEntity auto = client.player != null && client.player.getVehicle() instanceof AutomobileEntity car ? car : null;
        boolean driver = isDriver(client, auto);
        boolean mx5Pressed = mx5HeadlightEdge.press(mx5HeadlightKey != null && mx5HeadlightKey.isDown(),
                mx5Clicks, client.screen == null && !open && canControlHeadlightDance(auto));
        long tick = ++controlTick;
        if (driver) lightCycle.observe(client.level, auto, client.player, auto.getVehicleLightMode(), tick);
        else lightCycle.reset();
        if (client.screen == null && client.player != null) {
            if (openKey) { pendingMenu = null; openSettings(client); return; }
            if (pendingMenu != null) {
                var request = pendingMenu;
                pendingMenu = null; // Single use, including a denied final target check.
                if (request.kind() == PendingVehicleMenu.Kind.EFFECTS)
                    client.setScreen(new io.github.foundationgames.automobility.screen.AutomobilityOptionsScreen(null));
                else openSettings(client, request.vehicle());
                return;
            }
            if (locking) {
                var target = targetVehicle(client);
                if (target != null && target.getVehicleAccess().hasKey(client.player.getUUID()))
                    requestAccess(target, io.github.foundationgames.automobility.util.network.VehicleAccessPackets.Action.LOCK,
                            target.getVehicleAccess().locked() ? 0 : 1, "");
            }
        }
        boolean signalControls = client.screen == null && driver;
        dispatchSignalClicks(signalControls, leftSignals, () -> requestVehicleSignal(auto, 5));
        dispatchSignalClicks(signalControls, rightSignals, () -> requestVehicleSignal(auto, 6));
        dispatchSignalClicks(signalControls, hazards, () -> requestVehicleSignal(auto, 7));
        boolean comfortEnabled = signalControls && auto.isVehicleComfortSignalsEnabled();
        dispatchSignalClicks(comfortEnabled, comfortLeft, () -> requestVehicleSignal(auto, 8));
        dispatchSignalClicks(comfortEnabled, comfortRight, () -> requestVehicleSignal(auto, 9));
        if (client.screen != null || !driver) return;
        if (mx5Pressed) requestHeadlightDance(auto);
        if (toggle) requestLightMode(auto, auto.getVehicleLightMode() == VehicleLightMode.OFF ? VehicleLightMode.LOW : VehicleLightMode.OFF);
        else {
            var mode = lightCycle.cycle(client.level, auto, client.player, auto.getVehicleLightMode(), tick, lightCycleClicks);
            if (mode != null) {
                ClientPackets.sendVehicleLightModePacket(auto, mode);
            }
        }
        if (ignition) requestIgnition(auto, auto.getIgnitionState() == io.github.foundationgames.automobility.automobile.engine.VehicleIgnition.OFF);
    }

    private static int consumeClicks(KeyMapping mapping) {
        int clicks = 0;
        if (mapping != null) while (mapping.consumeClick()) clicks++;
        return clicks;
    }

    /** Preserve each click: the server toggles its current state, including before an ACK arrives. */
    static void dispatchSignalClicks(boolean allowed, int clicks, Runnable send) {
        if (allowed) for (int click = 0; click < clicks; click++) send.run();
    }

    private static boolean consume(KeyMapping mapping) {
        boolean pressed = false;
        if (mapping != null) while (mapping.consumeClick()) pressed = true;
        return pressed;
    }

    public static boolean openSettings(Minecraft client) {
        var target = targetVehicle(client);
        if (target != null) return openSettings(client, target);
        if (client.player == null || client.level == null || client.screen != null) return false;
        client.setScreen(new io.github.foundationgames.automobility.screen.AutomobilityOptionsScreen(null));
        return true;
    }

    private static boolean openSettings(Minecraft client, AutomobileEntity auto) {
        if (!currentMenuTarget(client, auto)) return false;
        var settings = new VehicleSettingsScreen(auto);
        client.setScreen(isDriver(client, auto) ? settings : new io.github.foundationgames.automobility.screen.VehicleAccessScreen(settings, auto));
        return true;
    }

    /** Defer command opens until ChatScreen has finished closing itself. */
    public static boolean queueEffectsSettings(Minecraft client) {
        if (!mayQueueMenu(client)) return false;
        pendingMenu = new PendingVehicleMenu<>(PendingVehicleMenu.Kind.EFFECTS,
                client.level, client.player, null, client.screen, controlTick);
        return true;
    }

    /** Defer command opens until ChatScreen has finished closing itself. */
    public static boolean queueSettings(Minecraft client) {
        var target = targetVehicle(client);
        if (!mayQueueMenu(client) || !currentMenuTarget(client, target)) return false;
        pendingMenu = new PendingVehicleMenu<>(PendingVehicleMenu.Kind.VEHICLE,
                client.level, client.player, target, client.screen, controlTick);
        return true;
    }

    private static boolean mayQueueMenu(Minecraft client) {
        return client.player != null && client.player.isAlive() && !client.player.isRemoved() && client.level != null
                && client.player.level() == client.level
                && (client.screen == null || client.screen instanceof ChatScreen);
    }

    private static boolean currentMenuTarget(Minecraft client, AutomobileEntity target) {
        return canUseMenu(client, target) && target.isAlive() && client.level.getEntity(target.getId()) == target;
    }

    public static boolean isDriver(Minecraft client, AutomobileEntity auto) {
        return client.player != null && client.player.isAlive() && client.level != null && auto != null && auto.isAlive() && !auto.isRemoved()
                && auto.level() == client.level && client.player.getVehicle() == auto && auto.isDriving(client.player);
    }

    public static void requestLightMode(AutomobileEntity auto, VehicleLightMode mode) {
        if (isDriver(Minecraft.getInstance(), auto) && mode != null)
            sendExplicitLightSelection(lightCycle, () -> ClientPackets.sendVehicleLightModePacket(auto, mode));
    }

    public static void initializeMx5HeadlightDance(KeyMapping mapping) {
        mx5HeadlightKey = mapping; mx5HeadlightEdge.reset();
    }
    public static Component mx5HeadlightBinding() { return binding(mx5HeadlightKey); }
    private static boolean mx5Ready(Minecraft client, AutomobileEntity auto) {
        return canUseMenu(client, auto) && auto.isAlive() && auto.hasOpeningHood()
                && CoreClientContentSession.supports(CoreContentProtocol.MX5_ANIMATION) && auto.mx5State().remoteBound();
    }
    public static boolean canControlHeadlightDance(AutomobileEntity auto) {
        var client = Minecraft.getInstance(); return mx5Ready(client, auto) && isDriver(client, auto);
    }
    public static boolean canControlHood(AutomobileEntity auto) {
        var client = Minecraft.getInstance();
        return mx5Ready(client, auto) && auto.getVehicleAccess().mayConfigure(client.player.getUUID());
    }
    public static boolean requestHeadlightDance(AutomobileEntity auto) {
        if (!canControlHeadlightDance(auto)) return false;
        sendMx5Intent(auto, Mx5Action.DANCE, false); return true;
    }
    public static boolean requestHood(AutomobileEntity auto, boolean open) {
        if (!canControlHood(auto) || open && !auto.canOpenHood()) return false;
        sendMx5Intent(auto, Mx5Action.HOOD, open); return true;
    }
    private static void sendMx5Intent(AutomobileEntity auto, int action, boolean value) {
        Mx5MovingPartsSync.clientRequest.accept(new Mx5Action(auto.level().dimension().identifier(),
                auto.getId(), auto.getUUID(), auto.mx5State().epoch(), action, value));
    }

    /** A manual selection or profile supersedes key intent even if HIGH already matches. */
    static void sendExplicitLightSelection(VehicleLightCycle state, Runnable sendPacket) {
        state.reset();
        sendPacket.run();
    }

    public static VehicleHudPreferences preferences() {
        return VehicleUiState.current().basic();
    }

    public static void requestIgnition(AutomobileEntity auto, boolean start) {
        if (isDriver(Minecraft.getInstance(), auto)) {
            if (!start) MomentumVehicleCapabilities.deactivateCruise(auto);
            ClientPackets.sendVehicleIgnitionPacket(auto, start);
        }
    }

    public static void requestEngineSettings(AutomobileEntity auto,
            io.github.foundationgames.automobility.automobile.engine.VehicleEngineSettings settings) {
        if (canConfigureDriving(auto) && settings != null) ClientPackets.sendVehicleEngineSettingsPacket(auto, settings);
    }

    public static void initializeLock(KeyMapping mapping) { lockKey = mapping; }
    public static KeyMapping lockKey() { return lockKey; }

    public static void initializeSignals(KeyMapping left, KeyMapping right, KeyMapping hazards) {
        signalLeftKey = left;
        signalRightKey = right;
        hazardsKey = hazards;
    }

    public static KeyMapping signalLeftKey() { return signalLeftKey; }
    public static KeyMapping signalRightKey() { return signalRightKey; }
    public static KeyMapping hazardsKey() { return hazardsKey; }

    public static void initializeComfortSignals(KeyMapping left, KeyMapping right) {
        comfortLeftKey = left;
        comfortRightKey = right;
    }

    public static KeyMapping comfortLeftKey() { return comfortLeftKey; }
    public static KeyMapping comfortRightKey() { return comfortRightKey; }

    public static void requestVehicleSignal(AutomobileEntity auto, int request) {
        if (isDriver(Minecraft.getInstance(), auto))
            ClientPackets.sendVehicleSignalPacket(auto, request);
    }

    public static void requestVehicleSignalAutoCancel(AutomobileEntity auto, boolean enabled) {
        if (canConfigureDriving(auto))
            requestVehicleSignalSettings(auto, enabled, auto.isVehicleComfortSignalsEnabled(),
                    auto.isVehicleLockSignalConfirmationEnabled());
    }

    public static void requestVehicleSignalSettings(AutomobileEntity auto, boolean automaticCancel,
                                                    boolean comfort, boolean lockConfirmation) {
        if (canConfigureDriving(auto))
            ClientPackets.sendVehicleSignalSettingsPacket(auto, automaticCancel, comfort, lockConfirmation);
    }

    /** A settings ACK must confirm every field; a partial update cannot unlock another full-packet edit. */
    public record SignalSettings(boolean automaticCancel, boolean comfort, boolean lockConfirmation) {
        public boolean matches(boolean observedAutomaticCancel, boolean observedComfort, boolean observedLockConfirmation) {
            return automaticCancel == observedAutomaticCancel && comfort == observedComfort
                    && lockConfirmation == observedLockConfirmation;
        }
    }

    public static Component signalsHint() {
        return Component.translatable("vehicle.automobility.signals.keys",
                binding(signalLeftKey), binding(signalRightKey), binding(hazardsKey));
    }

    public static Component comfortSignalsHint() {
        return Component.translatable("vehicle.automobility.signals.comfort_keys",
                binding(comfortLeftKey), binding(comfortRightKey));
    }

    public static AutomobileEntity targetVehicle(Minecraft client) {
        if (client.player == null) return null;
        if (client.player.getVehicle() instanceof AutomobileEntity car) return car;
        if (client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit) {
            if (hit.getEntity() instanceof AutomobileEntity car) return car;
            if (hit.getEntity() instanceof io.github.foundationgames.automobility.entity.HitboxEntity box) return box.automobile();
        }
        return null;
    }
    public static boolean canUseMenu(Minecraft client, AutomobileEntity car) {
        return client.player != null && client.player.isAlive() && client.level != null && car != null && !car.isRemoved()
                && car.level() == client.level && client.player.distanceToSqr(car) <= 64;
    }
    public static boolean canConfigureDriving(AutomobileEntity car) {
        var client = Minecraft.getInstance();
        return isDriver(client, car) && car.getVehicleAccess().mayConfigure(client.player.getUUID());
    }

    public static boolean canSelectDrivingMode(AutomobileEntity car,
            io.github.foundationgames.automobility.automobile.driving.VehicleDrivingMode mode) {
        return mode != null && canUseMenu(Minecraft.getInstance(), car) && canConfigureDriving(car)
                && MomentumVehicleCapabilities.drivingModeSupported(car)
                && io.github.foundationgames.automobility.automobile.driving.VehicleDrivingModeSupport.stationary(car)
                && (mode == io.github.foundationgames.automobility.automobile.driving.VehicleDrivingMode.INHERIT
                    || (car.getVehicleDrivingModeCapabilities() & 2) != 0);
    }

    public static boolean requestDrivingMode(AutomobileEntity car,
            io.github.foundationgames.automobility.automobile.driving.VehicleDrivingMode mode) {
        if (!canSelectDrivingMode(car, mode)) return false;
        clearActionFeedback(); ClientPackets.sendVehicleDrivingModePacket(car, mode); return true;
    }

    public static boolean canApplyProfile(AutomobileEntity car, VehicleConfigurationProfile profile) {
        return profile != null && canUseMenu(Minecraft.getInstance(), car) && canConfigureDriving(car)
                && (!MomentumVehicleCapabilities.drivingModeSupported(car)
                    || profile.drivingMode() == car.getVehicleDrivingMode() || canSelectDrivingMode(car, profile.drivingMode()));
    }
    public static void requestAccess(AutomobileEntity car,
            io.github.foundationgames.automobility.util.network.VehicleAccessPackets.Action action, int value, String target) {
        if (canUseMenu(Minecraft.getInstance(), car)) { clearActionFeedback(); ClientPackets.sendVehicleAccessPacket(car, action, value, target); }
    }
    public static java.util.List<VehicleConfigurationProfile> profiles() { return profiles; }
    public static void receiveActionFeedback(int entity, String key) {
        feedbackEntity = entity; feedbackLevel = Minecraft.getInstance().level; feedbackTicks = 100;
        feedbackKey = key;
        actionFeedback = Component.translatable("vehicle.automobility.access." + key);
    }
    public static Component feedbackFor(AutomobileEntity car) {
        return car != null && car.getId() == feedbackEntity && car.level() == feedbackLevel ? actionFeedback : null;
    }
    public static Component profileFeedbackFor(AutomobileEntity car) {
        return "profile_applied".equals(feedbackKey) || "denied".equals(feedbackKey) ? feedbackFor(car) : null;
    }
    public static void clearActionFeedback() { actionFeedback=null; feedbackKey=null; feedbackTicks=0; }
    public static boolean profilesWritable() { return profilesReadable; }
    public static boolean saveProfile(VehicleConfigurationProfile profile) {
        clearActionFeedback();
        if (!profilesReadable) return false;
        try {
            var next = VehicleProfileStore.put(profiles, profile);
            profileStore.save(next); profiles = next; return true;
        } catch (IOException | RuntimeException error) { Automobility.LOG.warn("Could not save vehicle profile", error); return false; }
    }
    public static boolean deleteProfile(VehicleConfigurationProfile profile) {
        clearActionFeedback();
        if (!profilesReadable) return false;
        try {
            var next = profiles.stream().filter(p -> !p.name().equalsIgnoreCase(profile.name())).toList();
            profileStore.save(next); profiles = next; return true;
        } catch (IOException | RuntimeException error) { Automobility.LOG.warn("Could not delete vehicle profile", error); return false; }
    }
    public static void applyProfile(AutomobileEntity car, VehicleConfigurationProfile profile) {
        if (canApplyProfile(car, profile)) {
            clearActionFeedback();
            sendExplicitLightSelection(lightCycle, () -> ClientPackets.sendVehicleProfilePacket(car, profile));
        }
    }

    public static Component engineBinding() { return binding(engineKey); }
    public static KeyMapping engineKey() { return engineKey; }

    public static boolean savePreferences(VehicleHudPreferences value) {
        return value!=null && VehicleUiState.save(VehicleUiState.current().withBasic(value));
    }

    public static boolean preferencesWritable() { return VehicleUiState.writable(); }

    public static KeyMapping settingsKey() {
        return settingsKey;
    }

    public static Component binding(KeyMapping mapping) {
        return mapping == null || mapping.isUnbound()
                ? Component.translatable("vehicle.automobility.unbound") : mapping.getTranslatedKeyMessage();
    }

    public static Component controlsHint() {
        return Component.translatable("vehicle.automobility.controls", binding(settingsKey), binding(lightKey), binding(highBeamKey));
    }
}
