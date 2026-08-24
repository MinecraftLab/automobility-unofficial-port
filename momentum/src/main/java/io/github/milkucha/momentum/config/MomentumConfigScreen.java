package io.github.milkucha.momentum.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class MomentumConfigScreen {

    public static Screen create(Screen parent) {
        MomentumConfig cfg = MomentumConfig.get();
        MomentumConfig def = new MomentumConfig();

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Momentum"))
                .category(general(cfg, def, parent))
                .category(drift(cfg, def, parent))
                .category(movement(cfg, def))
                .category(steering(cfg, def))
                .category(camera(cfg, def))
                .category(hud(cfg, def))
                .save(cfg::save)
                .build()
                .generateScreen(parent);
    }

    // ── Movement ─────────────────────────────────────────────────────────────

    private static ConfigCategory movement(MomentumConfig cfg, MomentumConfig def) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Movement"))
                .option(floatOpt("Coast Decay",
                        "Speed lost per tick when coasting with no throttle.\n\nHigher values = stops quickly after releasing throttle.\nLower values = car rolls on longer.",
                        def.movement.coastDecay,
                        () -> cfg.movement.coastDecay, v -> cfg.movement.coastDecay = v,
                        0.001f, 0.05f, 0.001f))
                .option(floatOpt("Acceleration Scale",
                        "Divides the raw acceleration force.\n\nHigher values = slower, more gradual acceleration.\nLower values = faster acceleration from a stop.",
                        def.movement.accelerationScale,
                        () -> cfg.movement.accelerationScale, v -> cfg.movement.accelerationScale = v,
                        1.0f, 10.0f, 0.1f))
                .option(floatOpt("Brake Decay",
                        "Speed lost per tick while braking.\n\nHigher values = hard stop.\nLower values = soft, progressive braking.",
                        def.movement.brakeDecay,
                        () -> cfg.movement.brakeDecay, v -> cfg.movement.brakeDecay = v,
                        0.001f, 0.1f, 0.001f))
                .option(floatOpt("Comfortable Speed Multiplier",
                        "Scales the car's comfortable speed cap.\n\nHigher values = higher top speed.\nLower values = lower top speed.",
                        def.movement.comfortableSpeedMultiplier,
                        () -> cfg.movement.comfortableSpeedMultiplier, v -> cfg.movement.comfortableSpeedMultiplier = v,
                        0.5f, 5.0f, 0.05f))
                .build();
    }

    // ── Steering ─────────────────────────────────────────────────────────────

    private static ConfigCategory steering(MomentumConfig cfg, MomentumConfig def) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Steering"))
                .option(floatOpt("Ramp Rate",
                        "How fast steering builds toward full lock while holding a direction key.\n\nHigher values = snaps to full lock quickly.\nLower values = slow, smooth build-up.",
                        def.steering.rampRate,
                        () -> cfg.steering.rampRate, v -> cfg.steering.rampRate = v,
                        0.01f, 1.0f, 0.01f))
                .option(floatOpt("Center Rate",
                        "How fast steering returns to centre when no key is held.\n\nHigher values = snaps straight instantly.\nLower values = wheels drift back slowly.",
                        def.steering.centerRate,
                        () -> cfg.steering.centerRate, v -> cfg.steering.centerRate = v,
                        0.01f, 1.0f, 0.01f))
                .option(floatOpt("Understeer",
                        "How much turning is reduced at high speed.\n\nHigher values = car struggles to turn at speed (more realistic).\nLower values = sharp corners at any speed.",
                        def.steering.understeer,
                        () -> cfg.steering.understeer, v -> cfg.steering.understeer = v,
                        0.0f, 10.0f, 0.1f))
                .option(floatOpt("Understeer Curve",
                        "Exponent shaping when understeer kicks in relative to speed.\n\nHigher values = only affects very high speeds.\nLower values = understeer starts at lower speeds.",
                        def.steering.understeerCurve,
                        () -> cfg.steering.understeerCurve, v -> cfg.steering.understeerCurve = v,
                        0.5f, 5.0f, 0.1f))
                .build();
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private static ConfigCategory camera(MomentumConfig cfg, MomentumConfig def) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Camera"))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Lock"))
                        .option(boolOpt("Lock Camera",
                                "Forces camera to follow the car's yaw every tick.",
                                def.camera.lock,
                                () -> cfg.camera.lock, v -> cfg.camera.lock = v))
                        .option(floatOpt("Lock Pitch",
                                "Camera pitch when lock is on. Positive = looking down.\n\nHigher values = steeper downward angle.\nLower values = more horizontal view.",
                                def.camera.pitch,
                                () -> cfg.camera.pitch, v -> cfg.camera.pitch = v,
                                -45.0f, 45.0f, 0.5f))
                        .option(floatOpt("Steering Tilt",
                                "Degrees of camera yaw offset at full steering lock.\n\nThe camera leans slightly toward the turn direction - gives a sense of the car banking into corners.\n\nHigher values = more dramatic lean.\nLower values = subtle or none.",
                                def.camera.steeringTilt,
                                () -> cfg.camera.steeringTilt, v -> cfg.camera.steeringTilt = v,
                                0.0f, 20.0f, 0.5f))
                        .option(floatOpt("Steering Tilt Lerp",
                                "How quickly the camera lean tracks the steering input.\n\nHigher values = snappy, follows immediately.\nLower values = smooth, weighted follow.",
                                def.camera.steeringTiltLerp,
                                () -> cfg.camera.steeringTiltLerp, v -> cfg.camera.steeringTiltLerp = v,
                                0.01f, 1.0f, 0.01f))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Reverse Camera"))
                        .option(boolOpt("Reverse Camera",
                                "Flips the camera 180° when the car moves in reverse. Returns to forward when moving normally again.",
                                def.camera.reverseFlip,
                                () -> cfg.camera.reverseFlip, v -> cfg.camera.reverseFlip = v))
                        .option(floatOpt("Reverse Flip Speed",
                                "How quickly the camera sweeps to 180° when reversing (and back when moving forward).\n\nHigher values = faster snap with ease-out.\nLower values = slow, gradual rotation.",
                                def.camera.reverseFlipLerp,
                                () -> cfg.camera.reverseFlipLerp, v -> cfg.camera.reverseFlipLerp = v,
                                0.01f, 1.0f, 0.01f))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Brake Zoom"))
                        .option(floatOpt("Brake Zoom FOV",
                                "Degrees of FOV reduction at peak braking.\n\nHigher values = dramatic cinematic zoom.\nLower values = subtle zoom.",
                                def.camera.brakeZoomFov,
                                () -> cfg.camera.brakeZoomFov, v -> cfg.camera.brakeZoomFov = v,
                                0.0f, 30.0f, 0.5f))
                        .option(floatOpt("Brake Zoom Input Scale",
                                "How much deceleration translates into zoom force.\n\nHigher values = aggressive zoom on any deceleration.\nLower values = subtle response even during hard braking.",
                                def.camera.brakeZoomInputScale,
                                () -> cfg.camera.brakeZoomInputScale, v -> cfg.camera.brakeZoomInputScale = v,
                                1.0f, 100.0f, 1.0f))
                        .option(floatOpt("Brake Zoom Spring",
                                "Strength pulling zoom back to normal.\n\nHigher values = snaps back immediately.\nLower values = zoom lingers after braking.",
                                def.camera.brakeZoomSpring,
                                () -> cfg.camera.brakeZoomSpring, v -> cfg.camera.brakeZoomSpring = v,
                                0.001f, 0.5f, 0.001f))
                        .option(floatOpt("Brake Zoom Damping",
                                "Zoom velocity carry-over per tick (0 = stops instantly, 1 = never stops).\n\nHigher values = coasts back slowly with inertia.\nLower values = zoom ends abruptly.",
                                def.camera.brakeZoomDamping,
                                () -> cfg.camera.brakeZoomDamping, v -> cfg.camera.brakeZoomDamping = v,
                                0.5f, 1.0f, 0.01f))
                        .build())
                .build();
    }

    // ── HUD ───────────────────────────────────────────────────────────────────

    private static ConfigCategory hud(MomentumConfig cfg, MomentumConfig def) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("HUD"))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Debug Overlay Position"))
                        .option(intOpt("Debug X", "Debug overlay X. -1 = anchor from right using fraction.",
                                def.barHud.debugX, () -> cfg.barHud.debugX, v -> cfg.barHud.debugX = v, -1, 1920, 1))
                        .option(intOpt("Debug Y", "Vertical position of the debug overlay on screen.", def.barHud.debugY,
                                () -> cfg.barHud.debugY, v -> cfg.barHud.debugY = v, 0, 1080, 1))
                        .option(floatOpt("Debug X Fraction", "Fraction of screen width from right edge when Debug X = -1.", def.barHud.debugXFraction,
                                () -> cfg.barHud.debugXFraction, v -> cfg.barHud.debugXFraction = v, 0.0f, 1.0f, 0.001f))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Bar  |  Position"))
                        .option(intOpt("X", "Bar HUD X. -1 = anchor from right.",
                                def.barHud.x, () -> cfg.barHud.x, v -> cfg.barHud.x = v, -1, 1920, 1))
                        .option(intOpt("Y", "Bar HUD Y. -1 = anchor from bottom.",
                                def.barHud.y, () -> cfg.barHud.y, v -> cfg.barHud.y = v, -1, 1080, 1))
                        .option(floatOpt("X Fraction", "Fraction of screen width from right edge when X = -1.", def.barHud.xFraction,
                                () -> cfg.barHud.xFraction, v -> cfg.barHud.xFraction = v, 0.0f, 1.0f, 0.001f))
                        .option(intOpt("Margin Bottom", "Pixels from the bottom edge when Y = -1. Higher = further from edge.", def.barHud.marginBottom,
                                () -> cfg.barHud.marginBottom, v -> cfg.barHud.marginBottom = v, 0, 500, 1))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Bar  |  Size"))
                        .option(boolOpt("Compact Dashboard", "Use the polished instrument cluster with a stable speed panel and aligned cruise pod. Disable for the legacy layout.",
                                def.barHud.compactDashboard,
                                () -> cfg.barHud.compactDashboard, v -> cfg.barHud.compactDashboard = v))
                        .option(intOpt("Total Width", "Dashboard width; compact values below 104 are corrected when saved.",
                                def.barHud.totalWidth,
                                () -> cfg.barHud.totalWidth, v -> cfg.barHud.totalWidth = v, 24, 500, 1))
                        .option(intOpt("Total Height", "Dashboard height; compact values below 17 are corrected when saved.",
                                def.barHud.totalHeight,
                                () -> cfg.barHud.totalHeight, v -> cfg.barHud.totalHeight = v, 1, 100, 1))
                        .option(intOpt("Bar Width", "Legacy layout only: width of each individual bar segment.",
                                def.barHud.barWidth,
                                () -> cfg.barHud.barWidth, v -> cfg.barHud.barWidth = v, 1, 50, 1))
                        .option(intOpt("Bar Spacing", "Legacy layout only: gap between bar segments.",
                                def.barHud.barSpacing,
                                () -> cfg.barHud.barSpacing, v -> cfg.barHud.barSpacing = v, 0, 20, 1))
                        .option(floatOpt("Max Speed (km/h)", "Speed at which all segments are filled.",
                                def.barHud.maxSpeedKmh,
                                () -> cfg.barHud.maxSpeedKmh, v -> cfg.barHud.maxSpeedKmh = v,
                                50.0f, 500.0f, 10.0f))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Bar  |  Colors"))
                        .option(colorOpt("Bar Color", "ARGB color of filled bar segments.",
                                def.barHud.barColor,
                                () -> cfg.barHud.barColor, v -> cfg.barHud.barColor = v))
                        .option(colorOpt("Boost Bar Color", "ARGB color of bar segments showing boost contribution.",
                                def.barHud.boostBarColor,
                                () -> cfg.barHud.boostBarColor, v -> cfg.barHud.boostBarColor = v))
                        .option(colorOpt("Text Color", "ARGB color of the speed readout text.",
                                def.barHud.textColor,
                                () -> cfg.barHud.textColor, v -> cfg.barHud.textColor = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Bar  |  Text Position"))
                        .option(intOpt("Text Offset X", "Legacy layout only: speed text X relative to bar top-left.",
                                def.barHud.textOffsetX,
                                () -> cfg.barHud.textOffsetX, v -> cfg.barHud.textOffsetX = v, -100, 100, 1))
                        .option(intOpt("Text Offset Y", "Legacy layout only: speed text Y relative to bar top-left. Negative = above bar.",
                                def.barHud.textOffsetY,
                                () -> cfg.barHud.textOffsetY, v -> cfg.barHud.textOffsetY = v, -100, 100, 1))
                        .build())
                .build();
    }

    // ── General ───────────────────────────────────────────────────────────────

    private static ConfigCategory general(MomentumConfig cfg, MomentumConfig def, Screen parent) {

        // Profile selector - applies immediately to config and rebuilds screen
        Option<MomentumConfig.ODrift.Profile> profileOpt =
                Option.<MomentumConfig.ODrift.Profile>createBuilder()
                        .name(Component.literal("Drift Profile"))
                        .description(OptionDescription.of(Component.literal(
                                "Which drift behaviour is triggered by the Handbrake (drift) key.\n\n" +
                                "Vanilla Drift    - Automobility's own drift. No slip angle, just speed.\n" +
                                "Arcade Drift     - Quick sideslip. Precise and easy to control.\n" +
                                "Responsive Drift - Deep slide. Wide angle, steering-driven, rewarding.\n\n" +
                                "Default: Arcade Drift")))
                        .binding(def.oDrift.profile,
                                () -> cfg.oDrift.profile,
                                v  -> cfg.oDrift.profile = v)
                        .controller(opt -> EnumControllerBuilder.create(opt)
                                .enumClass(MomentumConfig.ODrift.Profile.class)
                                .formatValue(v -> Component.literal(switch (v) {
                                    case VANILLA    -> "Vanilla Drift";
                                    case ARCADE     -> "Arcade Drift";
                                    case RESPONSIVE -> "Responsive Drift";
                                })))
                        .listener((opt, val) -> {
                            if (val == cfg.oDrift.profile) return;
                            cfg.oDrift.profile = val;
                            cfg.save();
                            Minecraft mc = Minecraft.getInstance();
                            mc.execute(() -> mc.setScreen(MomentumConfigScreen.create(parent)));
                        })
                        .build();

        // Build feature toggles individually so we can attach immediate-write listeners.
        // YACL only calls the binding setter on Save, so without these listeners a
        // profile switch (which calls cfg.save() + screen rebuild before Save is pressed)
        // would lose any pending toggle changes.
        Option<Boolean> movOpt = boolOpt("Movement",
                "Enable all custom Movement features (coast, acceleration, braking, speed cap).",
                def.movement.enabled,
                () -> cfg.movement.enabled, v -> cfg.movement.enabled = v);
        movOpt.addListener((opt, val) -> cfg.movement.enabled = val);

        Option<Boolean> steerOpt = boolOpt("Steering",
                "Enable all custom Steering features (ramp rate, center rate, understeer).",
                def.steering.enabled,
                () -> cfg.steering.enabled, v -> cfg.steering.enabled = v);
        steerOpt.addListener((opt, val) -> cfg.steering.enabled = val);

        Option<Boolean> camOpt = boolOpt("Camera",
                "Enable all custom Camera features (lock, pitch, brake zoom).",
                def.camera.enabled,
                () -> cfg.camera.enabled, v -> cfg.camera.enabled = v);
        camOpt.addListener((opt, val) -> cfg.camera.enabled = val);

        Option<Boolean> hudOpt = boolOpt("HUD",
                "Enable the custom Momentum HUD. When off, Automobility's built-in speedometer is used.",
                def.barHud.enabled,
                () -> cfg.barHud.enabled, v -> cfg.barHud.enabled = v);
        hudOpt.addListener((opt, val) -> cfg.barHud.enabled = val);

        return ConfigCategory.createBuilder()
                .name(Component.literal("General"))
                .option(boolOpt("Enable Momentum",
                        "Turn the entire mod on or off. When off, Automobility's vanilla movement, HUD, and physics are used instead.",
                        def.enabled,
                        () -> cfg.enabled, v -> cfg.enabled = v))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Features"))
                        .option(profileOpt)
                        .option(movOpt)
                        .option(steerOpt)
                        .option(camOpt)
                        .option(hudOpt)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Key Bindings"))
                        .option(LabelOption.create(Component.literal("Brake: ").append(
                                InputConstants.Type.KEYSYM.getOrCreate(cfg.brakeKey).getDisplayName())))
                        .option(LabelOption.create(Component.literal("Handbrake (Drift): ").append(
                                InputConstants.Type.KEYSYM.getOrCreate(cfg.driftKey).getDisplayName())))
                        .option(LabelOption.create(Component.literal("(Edit brakeKey / driftKey in momentum.json to remap)")))
                        .build())
                .build();
    }

    // ── Drift ─────────────────────────────────────────────────────────────────

    private static ConfigCategory drift(MomentumConfig cfg, MomentumConfig def, Screen parent) {

        ConfigCategory.Builder b = ConfigCategory.createBuilder()
                .name(Component.literal("Drift"));

        switch (cfg.oDrift.profile) {
            case ARCADE -> {
                BoostGroup  kg = arcadeDriftBoostGroup(cfg, def);
                CameraGroup kc = arcadeDriftCameraGroup(cfg, def);
                b.group(buildGroupFromList("Slip",    arcadeDriftSlipOptions(cfg, def)));
                b.group(buildGroupFromList("Trigger", arcadeDriftTriggerOptions(cfg, def)));
                b.group(buildGroupFromList("Boost",   kg.all()));
                b.group(buildGroupFromList("Camera",  kc.all()));
            }
            case RESPONSIVE -> {
                List<Option<?>> mSlip = responsiveDriftSlipOptions(cfg, def);
                BoostGroup      mg    = responsiveDriftBoostGroup(cfg, def);
                CameraGroup     mc2   = responsiveDriftCameraGroup(cfg, def);
                b.group(buildGroupFromList("Slip",     mSlip));
                b.group(buildGroupFromList("Steering", responsiveDriftSteeringOptions(cfg, def)));
                b.group(buildGroupFromList("Trigger",  responsiveDriftTriggerOptions(cfg, def)));
                b.group(buildGroupFromList("Boost",    mg.all()));
                b.group(buildGroupFromList("Camera",   mc2.all()));
            }
            case VANILLA -> { /* No groups - Vanilla Drift uses Automobility defaults */ }
        }

        return b.build();
    }

    // ── Arcade Drift group builders ───────────────────────────────────────────

    private static List<Option<?>> arcadeDriftSlipOptions(MomentumConfig cfg, MomentumConfig def) {
        return List.of(
                floatOpt("Slip Angle",
                        "Maximum sideways slide angle in degrees.\n\nHigher values = big dramatic sideslip.\nLower values = subtle drift.",
                        def.arcadeDrift.slipAngle,
                        () -> cfg.arcadeDrift.slipAngle, v -> cfg.arcadeDrift.slipAngle = v,
                        0.0f, 45.0f, 0.5f),
                floatOpt("Slip Converge Rate",
                        "Degrees per tick the slip angle snaps toward its target while held.\n\nHigher values = instantaneous snap.\nLower values = slow ease-in.",
                        def.arcadeDrift.slipConvergeRate,
                        () -> cfg.arcadeDrift.slipConvergeRate, v -> cfg.arcadeDrift.slipConvergeRate = v,
                        0.1f, 20.0f, 0.1f),
                floatOpt("Slip Decay",
                        "How fast the drift angle fades after release.\n\nHigher values = car straightens out quickly.\nLower values = drift lingers.",
                        def.arcadeDrift.slipDecay,
                        () -> cfg.arcadeDrift.slipDecay, v -> cfg.arcadeDrift.slipDecay = v,
                        0.0f, 5.0f, 0.05f),
                floatOpt("Slip Decay Speed Ref",
                        "Reference speed for speed-adjusted decay.\n\nHigher values = drift lingers longer at high speed.\nLower values = decay rate stays constant.",
                        def.arcadeDrift.slipDecaySpeedRef,
                        () -> cfg.arcadeDrift.slipDecaySpeedRef, v -> cfg.arcadeDrift.slipDecaySpeedRef = v,
                        0.0f, 2.0f, 0.01f)
        );
    }

    private static List<Option<?>> arcadeDriftTriggerOptions(MomentumConfig cfg, MomentumConfig def) {
        return List.of(
                floatOpt("Min Speed (km/h)",
                        "Minimum car speed to trigger an Arcade Drift.\n\nHigher values = requires more speed to start.\nLower values = can start from low speed.",
                        def.arcadeDrift.minSpeedKmh,
                        () -> cfg.arcadeDrift.minSpeedKmh, v -> cfg.arcadeDrift.minSpeedKmh = v,
                        0.0f, 200.0f, 5.0f),
                floatOpt("Steer Threshold",
                        "Minimum steering input needed to start a drift.\n\nHigher values = requires near-full lock.\nLower values = any slight input triggers it.",
                        def.arcadeDrift.steerThreshold,
                        () -> cfg.arcadeDrift.steerThreshold, v -> cfg.arcadeDrift.steerThreshold = v,
                        0.0f, 1.0f, 0.05f),
                intOpt("Min Hold Ticks",
                        "Drift key must be held this many ticks before drift can start.\n\nHigher values = adds a deliberate delay before starting.\nLower values = triggers almost instantly.",
                        def.arcadeDrift.minHoldTicks,
                        () -> cfg.arcadeDrift.minHoldTicks, v -> cfg.arcadeDrift.minHoldTicks = v,
                        0, 100, 1),
                intOpt("Auto Trigger Ticks",
                        "Ticks without steering before drift starts automatically. 0 = disabled.\n\nHigher values = longer wait before auto-triggering.\nLower values = triggers quickly without steering.",
                        def.arcadeDrift.autoTriggerTicks,
                        () -> cfg.arcadeDrift.autoTriggerTicks, v -> cfg.arcadeDrift.autoTriggerTicks = v,
                        0, 200, 1),
                boolOpt("Brake Enabled",
                        "When on, braking is applied while drift key is held but no drift has started yet.",
                        def.arcadeDrift.brakeEnabled,
                        () -> cfg.arcadeDrift.brakeEnabled, v -> cfg.arcadeDrift.brakeEnabled = v)
        );
    }

    private static BoostGroup arcadeDriftBoostGroup(MomentumConfig cfg, MomentumConfig def) {
        Option<Boolean> toggle   = boolOpt("Boost Enabled",
                "Grant a speed burst when Arcade Drift ends cleanly. Turn off to disable the boost entirely.",
                def.arcadeDrift.boostEnabled,
                () -> cfg.arcadeDrift.boostEnabled, v -> cfg.arcadeDrift.boostEnabled = v);
        Option<Float>   boost    = floatOpt("Boost",
                "Engine speed added instantly on clean release.\n\nHigher values = large burst of speed.\nLower values = small nudge.",
                def.arcadeDrift.boost,
                () -> cfg.arcadeDrift.boost, v -> cfg.arcadeDrift.boost = v,
                0.0f, 0.5f, 0.005f);
        Option<Integer> duration = intOpt("Boost Duration",
                "Ticks the boost animation plays (20 = 1 s).\n\nHigher values = longer animation.\nLower values = brief flash.",
                def.arcadeDrift.boostDuration,
                () -> cfg.arcadeDrift.boostDuration, v -> cfg.arcadeDrift.boostDuration = v,
                0, 200, 1);
        Option<Integer> minTicks = intOpt("Min Ticks",
                "Drift must last this long to earn the boost.\n\nHigher values = only sustained drifts are rewarded.\nLower values = short drifts qualify.",
                def.arcadeDrift.minTicks,
                () -> cfg.arcadeDrift.minTicks, v -> cfg.arcadeDrift.minTicks = v,
                0, 120, 1);

        List<Option<?>> deps = List.of(boost, duration, minTicks);
        toggle.addListener((opt, val) -> deps.forEach(o -> o.setAvailable(val)));
        deps.forEach(o -> o.setAvailable(cfg.arcadeDrift.boostEnabled));

        List<Option<?>> all = concat(List.of(toggle), deps);
        return new BoostGroup(toggle, deps, all);
    }

    private static CameraGroup arcadeDriftCameraGroup(MomentumConfig cfg, MomentumConfig def) {
        Option<Boolean> toggle  = boolOpt("Camera Enabled",
                "Swings the camera to follow the drift angle. Turn off to keep the camera fixed.",
                def.arcadeDrift.cameraEnabled,
                () -> cfg.arcadeDrift.cameraEnabled, v -> cfg.arcadeDrift.cameraEnabled = v);
        Option<Float> scale     = floatOpt("Camera Scale",
                "How much the camera yaw exaggerates the slip angle.\n\nHigher values = dramatic swing.\nLower values = subtle lean.",
                def.arcadeDrift.cameraScale,
                () -> cfg.arcadeDrift.cameraScale, v -> cfg.arcadeDrift.cameraScale = v,
                0.0f, 10.0f, 0.1f);
        Option<Float> lerpIn    = floatOpt("Camera Lerp In",
                "How fast the camera moves toward the drift offset.\n\nHigher values = snappy, instant follow.\nLower values = smooth, gradual follow.",
                def.arcadeDrift.cameraLerpIn,
                () -> cfg.arcadeDrift.cameraLerpIn, v -> cfg.arcadeDrift.cameraLerpIn = v,
                0.01f, 1.0f, 0.01f);
        Option<Float> lerpOut   = floatOpt("Camera Lerp Out",
                "How fast the camera returns to centre after drift ends.\n\nHigher values = snaps back instantly.\nLower values = slow settle.",
                def.arcadeDrift.cameraLerpOut,
                () -> cfg.arcadeDrift.cameraLerpOut, v -> cfg.arcadeDrift.cameraLerpOut = v,
                0.01f, 1.0f, 0.01f);

        List<Option<?>> deps = List.of(scale, lerpIn, lerpOut);
        toggle.addListener((opt, val) -> deps.forEach(o -> o.setAvailable(val)));
        deps.forEach(o -> o.setAvailable(cfg.arcadeDrift.cameraEnabled));

        List<Option<?>> all = concat(List.of(toggle), deps);
        return new CameraGroup(toggle, deps, all);
    }

    // ── Responsive Drift group builders ──────────────────────────────────────

    private static List<Option<?>> responsiveDriftSlipOptions(MomentumConfig cfg, MomentumConfig def) {
        Option<Float>   slipAngle        = floatOpt("Slip Angle",
                "Maximum sideways slide angle in degrees.\n\nHigher values = big dramatic sideslip.\nLower values = subtle drift.",
                def.responsiveDrift.slipAngle,
                () -> cfg.responsiveDrift.slipAngle, v -> cfg.responsiveDrift.slipAngle = v,
                0.0f, 90.0f, 0.5f);
        Option<Float>   slipConvergeRate = floatOpt("Slip Converge Rate",
                "Fraction of remaining distance closed per tick (exponential).\n\nHigher values = snaps to target quickly.\nLower values = slow ease-in.",
                def.responsiveDrift.slipConvergeRate,
                () -> cfg.responsiveDrift.slipConvergeRate, v -> cfg.responsiveDrift.slipConvergeRate = v,
                0.01f, 1.0f, 0.01f);
        Option<Float>   slipDecay        = floatOpt("Slip Decay",
                "How fast the drift angle fades after release.\n\nHigher values = car straightens out quickly.\nLower values = drift lingers.",
                def.responsiveDrift.slipDecay,
                () -> cfg.responsiveDrift.slipDecay, v -> cfg.responsiveDrift.slipDecay = v,
                0.0f, 10.0f, 0.1f);
        Option<Float>   slipDecaySpeedRef = floatOpt("Slip Decay Speed Ref",
                "Reference speed for speed-adjusted decay.\n\nHigher values = drift lingers longer at high speed.\nLower values = decay rate stays constant.",
                def.responsiveDrift.slipDecaySpeedRef,
                () -> cfg.responsiveDrift.slipDecaySpeedRef, v -> cfg.responsiveDrift.slipDecaySpeedRef = v,
                0.0f, 2.0f, 0.01f);
        Option<Boolean> constantAngle    = boolOpt("Constant Angle",
                "Lock the slip angle at the configured maximum immediately, skipping the ease-in ramp. On = no build-up.",
                def.responsiveDrift.constantAngle,
                () -> cfg.responsiveDrift.constantAngle, v -> cfg.responsiveDrift.constantAngle = v);

        constantAngle.addListener((opt, val) -> slipConvergeRate.setAvailable(!val));
        slipConvergeRate.setAvailable(!cfg.responsiveDrift.constantAngle);

        return List.of(slipAngle, slipConvergeRate, slipDecay, slipDecaySpeedRef, constantAngle);
    }

    private static List<Option<?>> responsiveDriftSteeringOptions(MomentumConfig cfg, MomentumConfig def) {
        return List.of(
                floatOpt("Steer Sensitivity",
                        "Exponent on the steering accumulator.\n\nHigher values = requires sustained steering to reach full angle.\nLower values = slip angle builds more linearly.",
                        def.responsiveDrift.steerSensitivity,
                        () -> cfg.responsiveDrift.steerSensitivity, v -> cfg.responsiveDrift.steerSensitivity = v,
                        0.1f, 10.0f, 0.1f),
                floatOpt("Steer Build Rate",
                        "How fast the steering accumulator climbs per tick.\n\nHigher values = reaches full angle quickly.\nLower values = slip angle builds slowly.",
                        def.responsiveDrift.steerBuildRate,
                        () -> cfg.responsiveDrift.steerBuildRate, v -> cfg.responsiveDrift.steerBuildRate = v,
                        0.001f, 0.5f, 0.001f),
                floatOpt("Steer Decay Rate",
                        "How fast the accumulator falls when steering is released.\n\nHigher values = slip fades quickly.\nLower values = slip holds longer without input.",
                        def.responsiveDrift.steerDecayRate,
                        () -> cfg.responsiveDrift.steerDecayRate, v -> cfg.responsiveDrift.steerDecayRate = v,
                        0.001f, 0.5f, 0.001f),
                floatOpt("Steer Threshold",
                        "Minimum steering input to maintain the drift angle.\n\nHigher values = requires clear steering to keep drift alive.\nLower values = small input is enough.",
                        def.responsiveDrift.steerThreshold,
                        () -> cfg.responsiveDrift.steerThreshold, v -> cfg.responsiveDrift.steerThreshold = v,
                        0.0f, 1.0f, 0.05f)
        );
    }

    private static List<Option<?>> responsiveDriftTriggerOptions(MomentumConfig cfg, MomentumConfig def) {
        return List.of(
                floatOpt("Min Speed (km/h)",
                        "Minimum car speed to trigger a Responsive Drift.\n\nHigher values = requires more speed to start.\nLower values = can start from low speed.",
                        def.responsiveDrift.minSpeedKmh,
                        () -> cfg.responsiveDrift.minSpeedKmh, v -> cfg.responsiveDrift.minSpeedKmh = v,
                        0.0f, 200.0f, 5.0f),
                intOpt("Min Hold Ticks",
                        "Drift key must be held this many ticks before drift can start.\n\nHigher values = adds a deliberate delay before starting.\nLower values = triggers almost instantly.",
                        def.responsiveDrift.minHoldTicks,
                        () -> cfg.responsiveDrift.minHoldTicks, v -> cfg.responsiveDrift.minHoldTicks = v,
                        0, 100, 1),
                intOpt("Auto Trigger Ticks",
                        "Ticks without steering before drift starts automatically. 0 = disabled.\n\nHigher values = longer wait before auto-triggering.\nLower values = triggers quickly without steering.",
                        def.responsiveDrift.autoTriggerTicks,
                        () -> cfg.responsiveDrift.autoTriggerTicks, v -> cfg.responsiveDrift.autoTriggerTicks = v,
                        0, 200, 1),
                boolOpt("Brake Enabled",
                        "When on, braking is applied while drift key is held but no drift has started yet.",
                        def.responsiveDrift.brakeEnabled,
                        () -> cfg.responsiveDrift.brakeEnabled, v -> cfg.responsiveDrift.brakeEnabled = v)
        );
    }

    private static BoostGroup responsiveDriftBoostGroup(MomentumConfig cfg, MomentumConfig def) {
        Option<Boolean> toggle   = boolOpt("Boost Enabled",
                "Grant a speed burst when Responsive Drift ends cleanly. Turn off to disable the boost entirely.",
                def.responsiveDrift.boostEnabled,
                () -> cfg.responsiveDrift.boostEnabled, v -> cfg.responsiveDrift.boostEnabled = v);
        Option<Float>   boost    = floatOpt("Boost",
                "Engine speed added instantly on clean release.\n\nHigher values = large burst of speed.\nLower values = small nudge.",
                def.responsiveDrift.boost,
                () -> cfg.responsiveDrift.boost, v -> cfg.responsiveDrift.boost = v,
                0.0f, 0.5f, 0.005f);
        Option<Integer> duration = intOpt("Boost Duration",
                "Ticks the boost animation plays (20 = 1 s).\n\nHigher values = longer animation.\nLower values = brief flash.",
                def.responsiveDrift.boostDuration,
                () -> cfg.responsiveDrift.boostDuration, v -> cfg.responsiveDrift.boostDuration = v,
                0, 200, 1);
        Option<Integer> minTicks = intOpt("Min Ticks",
                "Drift must last this long to earn the boost.\n\nHigher values = only sustained drifts are rewarded.\nLower values = short drifts qualify.",
                def.responsiveDrift.minTicks,
                () -> cfg.responsiveDrift.minTicks, v -> cfg.responsiveDrift.minTicks = v,
                0, 200, 1);

        List<Option<?>> deps = List.of(boost, duration, minTicks);
        toggle.addListener((opt, val) -> deps.forEach(o -> o.setAvailable(val)));
        deps.forEach(o -> o.setAvailable(cfg.responsiveDrift.boostEnabled));

        List<Option<?>> all = concat(List.of(toggle), deps);
        return new BoostGroup(toggle, deps, all);
    }

    private static CameraGroup responsiveDriftCameraGroup(MomentumConfig cfg, MomentumConfig def) {
        Option<Boolean> toggle  = boolOpt("Camera Enabled",
                "Swings the camera to follow the drift angle. Turn off to keep the camera fixed.",
                def.responsiveDrift.cameraEnabled,
                () -> cfg.responsiveDrift.cameraEnabled, v -> cfg.responsiveDrift.cameraEnabled = v);
        Option<Float> scale     = floatOpt("Camera Scale",
                "How much the camera yaw exaggerates the slip angle.\n\nHigher values = dramatic swing.\nLower values = subtle lean.",
                def.responsiveDrift.cameraScale,
                () -> cfg.responsiveDrift.cameraScale, v -> cfg.responsiveDrift.cameraScale = v,
                0.0f, 10.0f, 0.1f);
        Option<Float> lerpIn    = floatOpt("Camera Lerp In",
                "How fast the camera moves toward the drift offset.\n\nHigher values = snappy, instant follow.\nLower values = smooth, gradual follow.",
                def.responsiveDrift.cameraLerpIn,
                () -> cfg.responsiveDrift.cameraLerpIn, v -> cfg.responsiveDrift.cameraLerpIn = v,
                0.01f, 1.0f, 0.01f);
        Option<Float> lerpOut   = floatOpt("Camera Lerp Out",
                "How fast the camera returns to centre after drift ends.\n\nHigher values = snaps back instantly.\nLower values = slow settle.",
                def.responsiveDrift.cameraLerpOut,
                () -> cfg.responsiveDrift.cameraLerpOut, v -> cfg.responsiveDrift.cameraLerpOut = v,
                0.01f, 1.0f, 0.01f);

        List<Option<?>> deps = List.of(scale, lerpIn, lerpOut);
        toggle.addListener((opt, val) -> deps.forEach(o -> o.setAvailable(val)));
        deps.forEach(o -> o.setAvailable(cfg.responsiveDrift.cameraEnabled));

        List<Option<?>> all = concat(List.of(toggle), deps);
        return new CameraGroup(toggle, deps, all);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static OptionGroup buildGroupFromList(String name, List<Option<?>> opts) {
        OptionGroup.Builder b = OptionGroup.createBuilder().name(Component.literal(name));
        opts.forEach(b::option);
        return b.build();
    }

    @SafeVarargs
    private static List<Option<?>> concat(List<Option<?>>... lists) {
        List<Option<?>> result = new ArrayList<>();
        for (List<Option<?>> l : lists) result.addAll(l);
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Option<Float> floatOpt(String name, String desc, float def,
            java.util.function.Supplier<Float> get, java.util.function.Consumer<Float> set,
            float min, float max, float step) {
        String defStr = String.format("%.3f", def);
        Component descLine = desc.isEmpty()
                ? Component.literal("Default: " + defStr)
                : Component.literal(desc + "\n\nDefault: " + defStr);
        return Option.<Float>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(descLine))
                .binding(def, get, set)
                .controller(opt -> FloatSliderControllerBuilder.create(opt)
                        .range(min, max).step(step)
                        .formatValue(v -> Component.literal(String.format("%.3f", v))))
                .build();
    }

    private static Option<Integer> intOpt(String name, String desc, int def,
            java.util.function.Supplier<Integer> get, java.util.function.Consumer<Integer> set,
            int min, int max, int step) {
        Component descLine = desc.isEmpty()
                ? Component.literal("Default: " + def)
                : Component.literal(desc + "\n\nDefault: " + def);
        return Option.<Integer>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(descLine))
                .binding(def, get, set)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(min, max).step(step))
                .build();
    }

    private static Option<Boolean> boolOpt(String name, String desc, boolean def,
            java.util.function.Supplier<Boolean> get, java.util.function.Consumer<Boolean> set) {
        Component descLine = desc.isEmpty()
                ? Component.literal("Default: " + def)
                : Component.literal(desc + "\n\nDefault: " + def);
        return Option.<Boolean>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(descLine))
                .binding(def, get, set)
                .controller(BooleanControllerBuilder::create)
                .build();
    }

    private static Option<Color> colorOpt(String name, String desc, int defArgb,
            java.util.function.Supplier<Integer> get, java.util.function.Consumer<Integer> set) {
        String defStr = String.format("#%08X", defArgb);
        Component descLine = desc.isEmpty()
                ? Component.literal("Default: " + defStr)
                : Component.literal(desc + "\nDefault: " + defStr);
        return Option.<Color>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(descLine))
                .binding(new Color(defArgb, true),
                        () -> new Color(get.get(), true),
                        v -> set.accept(v.getRGB()))
                .controller(opt -> ColorControllerBuilder.create(opt).allowAlpha(true))
                .build();
    }

    // ── Records ───────────────────────────────────────────────────────────────

    private record BoostGroup(
            Option<Boolean> toggle,
            List<Option<?>> dependents,
            List<Option<?>> all
    ) {}

    private record CameraGroup(
            Option<Boolean> toggle,
            List<Option<?>> dependents,
            List<Option<?>> all
    ) {}
}
