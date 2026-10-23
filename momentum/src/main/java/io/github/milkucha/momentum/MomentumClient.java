package io.github.milkucha.momentum;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.platform.Platform;
import io.github.foundationgames.automobility.client.vehicle.GarageTestDriveClient;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.milkucha.momentum.hud.BarHud;
import io.github.milkucha.momentum.network.KeyStatePacket;
import io.github.milkucha.momentum.network.GameplayConfigPacket;
import io.github.milkucha.momentum.network.VehicleInputPacket;
import io.github.milkucha.momentum.network.ClientPhysicsSession;
import io.github.milkucha.momentum.network.PhysicsContract;
import io.github.milkucha.momentum.network.PhysicsHelloPacket;
import io.github.milkucha.momentum.network.PhysicsStatusPacket;
import io.github.milkucha.momentum.sound.BrakingSkidSound;
import io.github.milkucha.momentum.sound.ArcadeDriftSkidSound;
import io.github.milkucha.momentum.sound.ResponsiveDriftSkidSound;
import io.github.milkucha.momentum.sound.VanillaDriftSkidSound;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public class MomentumClient implements ClientModInitializer {
    private static final KeyMapping.Category MOMENTUM_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("momentum", "momentum"));

    // ── Registered KeyBindings ────────────────────────────────────────────────
    // The target KeyMapping MAP stores a list for each physical key (SDK-audited).
    // Brake/drift therefore share S/Space without replacing vanilla Back/Jump.
    // Runtime shared-key dispatch and saved remaps remain target acceptance checks.

    // ── Tick state ────────────────────────────────────────────────────────────

    private boolean prevBrakeHeld        = false;
    private boolean prevVanillaDriftActive = false;
    private boolean prevArcadeDriftActive  = false;
    private boolean prevResponsiveDriftActive = false;
    private float   kCameraDriftYawOffset  = 0f;
    private float   mCameraDriftYawOffset  = 0f;
    private float   steeringTiltOffset     = 0f;
    private float   reverseYawOffset       = 0f;
    private float   brakeZoomVelocity      = 0f;
    private float   prevHSpeed             = 0f;
    private AutomobileEntity brakeZoomVehicle;
    private Object brakeZoomLevel;
    private boolean brakeZoomComfort;
    private float brakeZoomMaximum;

    // Last key-state snapshot sent to the server; used to send only on changes.
    private boolean pktBrake = false;
    private boolean pktDrift = false;
    private boolean forceKeyStatePacket = true;
    private boolean compatibilityWarningShown;

    @Override
    public void onInitializeClient() {
        MomentumConfig.get();
        io.github.milkucha.momentum.network.CruiseMotorClient.initialize();
        ClientPlayNetworking.registerGlobalReceiver(io.github.milkucha.momentum.network.ReverseDriveCapability.TYPE, (payload, context) -> {});
        io.github.milkucha.momentum.network.ReverseDriveCapability.installClient(car -> {
            var client = Minecraft.getInstance();
            return client.player != null && client.player.isAlive() && client.getConnection() != null
                    && client.level == car.level() && client.player.getVehicle() == car && car.isDriving(client.player)
                    && ClientPlayNetworking.canSend(io.github.milkucha.momentum.network.ReverseDriveCapability.TYPE);
        });
        io.github.milkucha.momentum.hud.MomentumHudContributor.register();

        ClientPlayConnectionEvents.INIT.register((handler, client) -> {
            MomentumConfig.clearServerGameplayConfig();
            if (client.hasSingleplayerServer()) ClientPhysicsSession.endSession();
            else ClientPhysicsSession.beginRemoteSession();
            MomentumCruiseControl.clearSession();
            MomentumBrakeState.sampleInput(false, false);
            MomentumDriftState.driftKeyHeld = false;
            pktBrake = false; pktDrift = false; forceKeyStatePacket = true;
            compatibilityWarningShown = false;
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!client.hasSingleplayerServer()) {
                ClientPhysicsSession.setServerHasMomentum(ClientPlayNetworking.canSend(KeyStatePacket.TYPE));
                if (ClientPlayNetworking.canSend(PhysicsHelloPacket.TYPE)) {
                    ClientPlayNetworking.send(new PhysicsHelloPacket(PhysicsContract.REVISION, ClientPhysicsSession.nonce()));
                }
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(PhysicsStatusPacket.TYPE, (payload, context) -> {
            var connection = context.client().getConnection();
            context.client().execute(() -> {
                if (connection == null || context.client().getConnection() != connection) return;
                ClientPhysicsSession.receiveStatus(payload.revision(), payload.nonce(), payload.accepted());
                if (!ClientPhysicsSession.physicsAllowed(MomentumConfig.hasServerGameplayConfig())) {
                    MomentumCruiseControl.clearSession();
                    MomentumBrakeState.sampleInput(false, false);
                    MomentumDriftState.driftKeyHeld = false;
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(GameplayConfigPacket.TYPE, (payload, context) -> {
            var connection = context.client().getConnection();
            context.client().execute(() -> {
                if (connection == null || context.client().getConnection() != connection) return;
                if (payload.protocolVersion() != GameplayConfigPacket.PROTOCOL_VERSION) {
                    Momentum.LOGGER.warn("Ignoring unsupported Momentum gameplay config protocol {}", payload.protocolVersion());
                    rejectServerGameplayConfig();
                } else if (context.client().hasSingleplayerServer()) {
                    MomentumConfig.clearServerGameplayConfig();
                } else if (!MomentumConfig.applyServerGameplayConfig(payload.json())) {
                    rejectServerGameplayConfig();
                }
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(VehicleInputPacket.TYPE, (payload, context) -> {
            var connection = context.client().getConnection();
            context.client().execute(() -> {
                    if (connection == null || context.client().getConnection() != connection) return;
                    if (ClientPhysicsSession.physicsAllowed(MomentumConfig.hasServerGameplayConfig())
                            && context.client().level != null
                            && context.client().level.getEntity(payload.entityId()) instanceof AutomobileEntity automobile) {
                        ((SteeringDebugAccessor) automobile).momentum$setRemoteInputState(payload.brake(), payload.drift());
                    }
                });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientPhysicsSession.endSession();
            MomentumConfig.clearServerGameplayConfig();
            BarHud.resetSpeedDisplay();
            MomentumCruiseControl.clearSession();
            MomentumBrakeState.sampleInput(false, false);
            MomentumDriftState.driftKeyHeld = false;
            pktBrake = false;
            pktDrift = false;
            forceKeyStatePacket = true;
            compatibilityWarningShown = false;
        });

        // ── KeyBinding registrations ──────────────────────────────────────────
        // These appear in Minecraft's vanilla Controls screen under "Momentum".

        MomentumKeyMappings.initialize(MOMENTUM_CATEGORY);

        KeyMapping reloadKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.momentum.reload_config",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_F6,
                MOMENTUM_CATEGORY
        ));

        final KeyMapping openOptionsKey;
        if (FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
            openOptionsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "key.momentum.open_options",
                    InputConstants.UNKNOWN.getType(),
                    InputConstants.UNKNOWN.getValue(),
                    MOMENTUM_CATEGORY
            ));
        } else {
            openOptionsKey = null;
        }

        KeyMapping cruiseKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.momentum.cruise_control",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_C,
                MOMENTUM_CATEGORY
        ));
        KeyMapping cruiseIncreaseKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.momentum.cruise_increase",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_PAGEUP,
                MOMENTUM_CATEGORY
        ));
        KeyMapping cruiseDecreaseKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.momentum.cruise_decrease",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_PAGEDOWN,
                MOMENTUM_CATEGORY
        ));
        boolean irisLoaded = FabricLoader.getInstance().isModLoaded("iris");
        KeyMapping cruiseResumeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.momentum.cruise_resume",
                irisLoaded ? InputConstants.UNKNOWN.getType() : InputConstants.Type.KEYBOARD,
                irisLoaded ? InputConstants.UNKNOWN.getValue() : InputConstants.KEY_R,
                MOMENTUM_CATEGORY
        ));

        // ── HUD rendering ─────────────────────────────────────────────────────

        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath("momentum", "bar_hud"), (drawContext, tickCounter) -> {
            MomentumConfig cfg = MomentumConfig.get();
            if (!io.github.milkucha.momentum.hud.MomentumHudContributor.dashboardEnabled()) return;
            float tickDelta = tickCounter.getGameTimeDeltaPartialTick(true);
            BarHud.render(drawContext, tickDelta);
            BarHud.renderDebug(drawContext, tickDelta);
        });

        // ── Key polling ───────────────────────────────────────────────────────
        // Poll the KeyBinding held state before entity ticks so both client and
        // server entities read the correct values this frame.

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            boolean locallyDriving = client.player != null
                    && client.player.getVehicle() instanceof AutomobileEntity auto
                    && auto.isDriving(client.player);

            MomentumCruiseControl.maintainSession(locallyDriving
                    ? (AutomobileEntity) client.player.getVehicle() : null);

            if (ClientPhysicsSession.physicsAllowed(MomentumConfig.hasServerGameplayConfig())
                    && locallyDriving && client.gui.screen() == null) {
                var controller = Platform.get().controller();
                var pedals = MomentumKeyMappings.pedals(controller.braking(), controller.drifting());
                boolean enabled = MomentumConfig.gameplay().enabled;
                MomentumBrakeState.sampleInput(pedals.brake(), enabled);
                MomentumDriftState.driftKeyHeld = enabled && pedals.drift();
                MomentumCruiseControl.cancelOnDriverInput(pedals.brake(), pedals.drift());
            } else {
                MomentumBrakeState.sampleInput(false, false);
                MomentumDriftState.driftKeyHeld = false;
            }

            if (!locallyDriving) compatibilityWarningShown = false;
            if (locallyDriving && MomentumConfig.clientDrivingBlocked()
                    && !compatibilityWarningShown) {
                client.player.sendOverlayMessage(Component.translatable("momentum.compatibility.driver_required"));
                compatibilityWarningShown = true;
            }

            if (!locallyDriving || !MomentumCruiseControl.isEnabled()) {
                MomentumCruiseControl.clearSession();
            }

            // Send key state to server whenever any value changes.
            boolean nb = MomentumBrakeState.rawBrakeHeld;
            boolean nd = MomentumDriftState.driftKeyHeld;
            if (client.getConnection() != null
                    && ClientPhysicsSession.physicsAllowed(MomentumConfig.hasServerGameplayConfig())
                    && ClientPlayNetworking.canSend(KeyStatePacket.TYPE)
                    && (forceKeyStatePacket || nb != pktBrake || nd != pktDrift)) {
                ClientPlayNetworking.send(new KeyStatePacket(nb, nd));
                pktBrake = nb; pktDrift = nd;
                forceKeyStatePacket = false;
            }
        });

        // ── Per-tick effects ──────────────────────────────────────────────────

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            BarHud.updateSpeedDisplay(client);
            MomentumControllerInput.ActionSnapshot controllerActions = MomentumControllerInput.poll();
            boolean cruisePressed = consumeClicks(cruiseKey) > 0 || controllerActions.cruiseToggle();
            int increaseClicks = Math.max(consumeClicks(cruiseIncreaseKey), controllerActions.cruiseIncrease() ? 1 : 0);
            int decreaseClicks = Math.max(consumeClicks(cruiseDecreaseKey), controllerActions.cruiseDecrease() ? 1 : 0);
            boolean cruiseResumePressed = consumeClicks(cruiseResumeKey) > 0 || controllerActions.cruiseResume();
            boolean garageReturnContext = client.player != null
                    && client.player.getVehicle() instanceof AutomobileEntity returnCar
                    && returnCar.isDriving(client.player) && GarageTestDriveClient.matchesVehicle(returnCar);
            if (!controllerActions.cruiseResume() && !MomentumInputPolicy.allowCruiseResume(garageReturnContext,
                    MomentumKeyMappings.sameBinding(cruiseResumeKey, GarageTestDriveClient.returnKey()))) {
                cruiseResumePressed = false;
            }
            boolean reloadPressed = consumeClicks(reloadKey) > 0;
            boolean optionsPressed = consumeClicks(openOptionsKey) > 0;
            if (cruisePressed && client.gui.screen() == null && client.player != null) {
                if (client.player.getVehicle() instanceof AutomobileEntity auto && auto.isDriving(client.player)) {
                    handleCruiseResult(client, MomentumCruiseControl.toggle(auto), 1.20f);
                } else {
                    MomentumCruiseControl.clearSession();
                    handleCruiseResult(client,
                            new MomentumCruiseControl.ActionResult(
                                    MomentumCruiseControl.Action.UNAVAILABLE, 0f, 0f),
                            0.70f);
                }
            }

            if (client.gui.screen() == null && client.player != null
                    && client.player.getVehicle() instanceof AutomobileEntity auto
                    && auto.isDriving(client.player)) {
                if (increaseClicks > 0) {
                    MomentumCruiseControl.ActionResult result = null;
                    for (int click = 0; click < increaseClicks; click++) result = MomentumCruiseControl.adjustTarget(auto, 1);
                    handleCruiseResult(client, result, 1.45f);
                }
                if (decreaseClicks > 0) {
                    MomentumCruiseControl.ActionResult result = null;
                    for (int click = 0; click < decreaseClicks; click++) result = MomentumCruiseControl.adjustTarget(auto, -1);
                    handleCruiseResult(client, result, 0.90f);
                }
                if (cruiseResumePressed) {
                    handleCruiseResult(client, MomentumCruiseControl.resume(auto), 1.30f);
                }
            } else if (client.gui.screen() == null && client.player != null
                    && (increaseClicks > 0 || decreaseClicks > 0 || cruiseResumePressed)) {
                handleCruiseResult(client,
                        new MomentumCruiseControl.ActionResult(
                                MomentumCruiseControl.Action.UNAVAILABLE, 0f, 0f),
                        0.70f);
            }

            MomentumCruiseControl.CancellationReason cancellation =
                    MomentumCruiseControl.consumeCancellationReason();
            if (cancellation != MomentumCruiseControl.CancellationReason.NONE && client.player != null) {
                client.player.sendOverlayMessage(Component.translatable("message.momentum.cruise_cancelled"));
                playCruiseTone(client, 0.70f);
            }

            if (reloadPressed && MomentumInputPolicy.allowReload(client.player != null, client.gui.screen() != null)) {
                MomentumDrivingModelClient.reloadSettings();
            }
            if (optionsPressed) {
                if (client.player != null && client.player.getVehicle() instanceof AutomobileEntity optionsDriver
                        && optionsDriver.isDriving(client.player)) {
                    // Consume saved bindings too; pending clicks must not open options after dismount.
                } else {
                    client.execute(() -> {
                        if (client.player != null && client.player.getVehicle() instanceof AutomobileEntity automobile
                                && automobile.isDriving(client.player)) return;
                        try {
                            var screenClass = Class.forName("io.github.milkucha.momentum.config.MomentumConfigScreen");
                            var create = screenClass.getMethod("create", net.minecraft.client.gui.screens.Screen.class);
                            client.gui.setScreen((net.minecraft.client.gui.screens.Screen) create.invoke(null, client.gui.screen()));
                        } catch (ReflectiveOperationException | LinkageError e) {
                            if (client.player != null) {
                                client.player.sendOverlayMessage(Component.translatable(
                                        "message.momentum.config_screen_unavailable"));
                            }
                        }
                    });
                }
            }

            if (client.player != null && client.player.getVehicle() instanceof AutomobileEntity auto) {
                MomentumConfig cfg = MomentumConfig.get();
                SteeringDebugAccessor accessor = (SteeringDebugAccessor) auto;

                if (!MomentumConfig.presentationEnabled()) {
                    resetTickState();
                    return;
                }

                // Independent camera lerp for Arcade and Responsive drift.
                boolean arcadeDriftCamActive    = accessor.momentum$isArcadeDriftActive();
                boolean responsiveDriftCamActive = accessor.momentum$isResponsiveDriftActive();

                float kTarget = cfg.arcadeDrift.cameraEnabled
                        ? accessor.momentum$getArcadeDriftOffset() * cfg.arcadeDrift.cameraScale : 0f;
                float mTarget = cfg.responsiveDrift.cameraEnabled
                        ? accessor.momentum$getResponsiveDriftOffset() * cfg.responsiveDrift.cameraScale : 0f;

                kCameraDriftYawOffset += (kTarget - kCameraDriftYawOffset)
                        * (arcadeDriftCamActive ? cfg.arcadeDrift.cameraLerpIn : cfg.arcadeDrift.cameraLerpOut);
                mCameraDriftYawOffset += (mTarget - mCameraDriftYawOffset)
                        * (responsiveDriftCamActive ? cfg.responsiveDrift.cameraLerpIn : cfg.responsiveDrift.cameraLerpOut);

                // Steering tilt: subtle yaw lean toward the turn direction.
                float tiltTarget = cfg.camera.enabled
                        ? accessor.momentum$getSteering() * cfg.camera.steeringTilt : 0f;
                steeringTiltOffset += (tiltTarget - steeringTiltOffset) * cfg.camera.steeringTiltLerp;

                // Reverse camera: lerp to 180° offset when engine speed is negative.
                boolean reversing = cfg.camera.enabled && cfg.camera.reverseFlip
                        && accessor.momentum$getEngineSpeed() < -0.01f;
                float reverseTarget = reversing ? 180f : 0f;
                reverseYawOffset += (reverseTarget - reverseYawOffset) * cfg.camera.reverseFlipLerp;

                if (MomentumCameraPolicy.lockActive(MomentumConfig.presentationEnabled(), cfg.camera.enabled, cfg.camera.lock)) {
                    client.player.setYRot(auto.getYRot() + kCameraDriftYawOffset + mCameraDriftYawOffset + steeringTiltOffset + reverseYawOffset);
                    client.player.setXRot(cfg.camera.pitch);
                }

                // Brake skid sound
                boolean brakeHeld = MomentumBrakeState.brakeHeld;
                if (brakeHeld && !prevBrakeHeld) {
                    client.getSoundManager().play(new BrakingSkidSound(auto));
                }
                prevBrakeHeld = brakeHeld;

                // Brake zoom - spring-damper driven by deceleration.
                float hSpd = accessor.momentum$getHSpeed();
                if (cfg.camera.enabled && Float.isFinite(hSpd)) {
                    boolean sameBaseline = auto == brakeZoomVehicle && client.level == brakeZoomLevel
                            && brakeZoomComfort == cfg.camera.comfortBrakeZoom
                            && Float.compare(brakeZoomMaximum, cfg.camera.brakeZoomFov) == 0
                        && Float.isFinite(prevHSpeed);
                    if (!sameBaseline) resetBrakeZoom();
                    float decel = sameBaseline ? BrakeZoomDisplayState.deceleration(prevHSpeed, hSpd) : 0f;
                    prevHSpeed = hSpd;
                    brakeZoomVehicle = auto;
                    brakeZoomLevel = client.level;
                    brakeZoomComfort = cfg.camera.comfortBrakeZoom;
                    brakeZoomMaximum = cfg.camera.brakeZoomFov;
                    float inputForce = decel * cfg.camera.brakeZoomInputScale;
                    brakeZoomVelocity = brakeZoomVelocity * cfg.camera.brakeZoomDamping
                        + inputForce
                        - cfg.camera.brakeZoomSpring * MomentumBrakeState.brakeZoomOffset;
                    MomentumBrakeState.brakeZoomOffset += brakeZoomVelocity;
                    if (MomentumBrakeState.brakeZoomOffset > cfg.camera.brakeZoomFov) {
                        MomentumBrakeState.brakeZoomOffset = cfg.camera.brakeZoomFov;
                        brakeZoomVelocity = Math.min(brakeZoomVelocity, 0f);
                    } else if (MomentumBrakeState.brakeZoomOffset < 0f) {
                        MomentumBrakeState.brakeZoomOffset = 0f;
                        brakeZoomVelocity = Math.max(brakeZoomVelocity, 0f);
                    }
                    if (!Float.isFinite(brakeZoomVelocity) || !Float.isFinite(MomentumBrakeState.brakeZoomOffset)) {
                        resetBrakeZoom();
                    } else {
                        MomentumBrakeState.brakeZoomDisplay.tick(MomentumBrakeState.brakeZoomOffset,
                                cfg.camera.comfortBrakeZoom, cfg.camera.brakeZoomFov);
                    }
                } else {
                    resetBrakeZoom();
                }

                // Drift skid sounds - one per drift type, triggered on rising edge of active state
                boolean vanillaActive = accessor.momentum$isDrifting();
                if (vanillaActive && !prevVanillaDriftActive) {
                    client.getSoundManager().play(new VanillaDriftSkidSound(auto));
                }
                prevVanillaDriftActive = vanillaActive;

                boolean arcadeActive = accessor.momentum$isArcadeDriftActive();
                if (arcadeActive && !prevArcadeDriftActive) {
                    client.getSoundManager().play(new ArcadeDriftSkidSound(auto));
                }
                prevArcadeDriftActive = arcadeActive;

                boolean responsiveActive = accessor.momentum$isResponsiveDriftActive();
                if (responsiveActive && !prevResponsiveDriftActive) {
                    client.getSoundManager().play(new ResponsiveDriftSkidSound(auto));
                }
                prevResponsiveDriftActive = responsiveActive;

            } else {
                resetTickState();
            }
        });
    }

    private void resetTickState() {
        prevBrakeHeld             = false;
        prevVanillaDriftActive    = false;
        prevArcadeDriftActive     = false;
        prevResponsiveDriftActive = false;
        kCameraDriftYawOffset              = 0f;
        mCameraDriftYawOffset              = 0f;
        steeringTiltOffset                 = 0f;
        reverseYawOffset                   = 0f;
        resetBrakeZoom();
    }

    private void resetBrakeZoom() {
        brakeZoomVelocity = 0f;
        prevHSpeed = 0f;
        brakeZoomVehicle = null;
        brakeZoomLevel = null;
        MomentumBrakeState.brakeZoomOffset = 0f;
        MomentumBrakeState.brakeZoomDisplay.reset();
    }

    private static void handleCruiseResult(Minecraft client,
                                           MomentumCruiseControl.ActionResult result,
                                           float successPitch) {
        if (client.player == null) return;

        Component message;
        float pitch = successPitch;
        switch (result.action()) {
            case ENGAGED -> message = Component.translatable(
                    "message.momentum.cruise_enabled", roundedSpeed(result.targetKmh()));
            case DISENGAGED -> {
                message = Component.translatable("message.momentum.cruise_disabled");
                pitch = 0.75f;
            }
            case RESUMED -> message = Component.translatable(
                    "message.momentum.cruise_resumed", roundedSpeed(result.targetKmh()));
            case ADJUSTED, ALREADY_ACTIVE -> message = Component.translatable(
                    "message.momentum.cruise_adjusted", roundedSpeed(result.targetKmh()));
            case TOO_SLOW -> {
                message = Component.translatable(
                        "message.momentum.cruise_too_slow", roundedSpeed(result.minimumKmh()));
                pitch = 0.70f;
            }
            case NOT_ACTIVE -> {
                message = Component.translatable("message.momentum.cruise_not_active");
                pitch = 0.70f;
            }
            case NO_SAVED_TARGET -> {
                message = Component.translatable("message.momentum.cruise_no_saved_speed");
                pitch = 0.70f;
            }
            case UNAVAILABLE -> {
                message = Component.translatable("message.momentum.cruise_unavailable");
                pitch = 0.70f;
            }
            default -> throw new IllegalStateException("Unhandled cruise action " + result.action());
        }

        client.player.sendOverlayMessage(message);
        playCruiseTone(client, pitch);
    }

    private void rejectServerGameplayConfig() {
        if ((pktBrake || pktDrift) && ClientPlayNetworking.canSend(KeyStatePacket.TYPE)) {
            ClientPlayNetworking.send(new KeyStatePacket(false, false));
        }
        pktBrake = false; pktDrift = false; forceKeyStatePacket = true;
        MomentumConfig.clearServerGameplayConfig();
        MomentumCruiseControl.clearSession();
        MomentumBrakeState.sampleInput(false, false);
        MomentumDriftState.driftKeyHeld = false;
    }

    private static String roundedSpeed(float speedKmh) {
        return Integer.toString(Math.round(speedKmh));
    }

    private static int consumeClicks(KeyMapping mapping) {
        int clicks = 0;
        if (mapping != null) while (mapping.consumeClick()) clicks++;
        return clicks;
    }

    private static void playCruiseTone(Minecraft client, float pitch) {
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

}
