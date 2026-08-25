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
                .title(text("title"))
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
                .name(text("category.movement"))
                .option(floatOpt("movement.coast_decay",
                        def.movement.coastDecay,
                        () -> cfg.movement.coastDecay, v -> cfg.movement.coastDecay = v,
                        0.001f, 0.05f, 0.001f))
                .option(floatOpt("movement.acceleration_scale",
                        def.movement.accelerationScale,
                        () -> cfg.movement.accelerationScale, v -> cfg.movement.accelerationScale = v,
                        1.0f, 10.0f, 0.1f))
                .option(floatOpt("movement.brake_decay",
                        def.movement.brakeDecay,
                        () -> cfg.movement.brakeDecay, v -> cfg.movement.brakeDecay = v,
                        0.001f, 0.1f, 0.001f))
                .option(floatOpt("movement.comfortable_speed_multiplier",
                        def.movement.comfortableSpeedMultiplier,
                        () -> cfg.movement.comfortableSpeedMultiplier, v -> cfg.movement.comfortableSpeedMultiplier = v,
                        0.5f, 5.0f, 0.05f))
                .build();
    }

    // ── Steering ─────────────────────────────────────────────────────────────

    private static ConfigCategory steering(MomentumConfig cfg, MomentumConfig def) {
        return ConfigCategory.createBuilder()
                .name(text("category.steering"))
                .option(floatOpt("steering.ramp_rate",
                        def.steering.rampRate,
                        () -> cfg.steering.rampRate, v -> cfg.steering.rampRate = v,
                        0.01f, 1.0f, 0.01f))
                .option(floatOpt("steering.center_rate",
                        def.steering.centerRate,
                        () -> cfg.steering.centerRate, v -> cfg.steering.centerRate = v,
                        0.01f, 1.0f, 0.01f))
                .option(floatOpt("steering.understeer",
                        def.steering.understeer,
                        () -> cfg.steering.understeer, v -> cfg.steering.understeer = v,
                        0.0f, 10.0f, 0.1f))
                .option(floatOpt("steering.understeer_curve",
                        def.steering.understeerCurve,
                        () -> cfg.steering.understeerCurve, v -> cfg.steering.understeerCurve = v,
                        0.5f, 5.0f, 0.1f))
                .build();
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private static ConfigCategory camera(MomentumConfig cfg, MomentumConfig def) {
        return ConfigCategory.createBuilder()
                .name(text("category.camera"))
                .group(OptionGroup.createBuilder()
                        .name(text("group.camera_lock"))
                        .option(boolOpt("camera.lock",
                                def.camera.lock,
                                () -> cfg.camera.lock, v -> cfg.camera.lock = v))
                        .option(floatOpt("camera.lock_pitch",
                                def.camera.pitch,
                                () -> cfg.camera.pitch, v -> cfg.camera.pitch = v,
                                -45.0f, 45.0f, 0.5f))
                        .option(floatOpt("camera.steering_tilt",
                                def.camera.steeringTilt,
                                () -> cfg.camera.steeringTilt, v -> cfg.camera.steeringTilt = v,
                                0.0f, 20.0f, 0.5f))
                        .option(floatOpt("camera.steering_tilt_lerp",
                                def.camera.steeringTiltLerp,
                                () -> cfg.camera.steeringTiltLerp, v -> cfg.camera.steeringTiltLerp = v,
                                0.01f, 1.0f, 0.01f))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(text("group.reverse_camera"))
                        .option(boolOpt("camera.reverse",
                                def.camera.reverseFlip,
                                () -> cfg.camera.reverseFlip, v -> cfg.camera.reverseFlip = v))
                        .option(floatOpt("camera.reverse_flip_speed",
                                def.camera.reverseFlipLerp,
                                () -> cfg.camera.reverseFlipLerp, v -> cfg.camera.reverseFlipLerp = v,
                                0.01f, 1.0f, 0.01f))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(text("group.brake_zoom"))
                        .option(floatOpt("camera.brake_zoom_fov",
                                def.camera.brakeZoomFov,
                                () -> cfg.camera.brakeZoomFov, v -> cfg.camera.brakeZoomFov = v,
                                0.0f, 30.0f, 0.5f))
                        .option(floatOpt("camera.brake_zoom_input_scale",
                                def.camera.brakeZoomInputScale,
                                () -> cfg.camera.brakeZoomInputScale, v -> cfg.camera.brakeZoomInputScale = v,
                                1.0f, 100.0f, 1.0f))
                        .option(floatOpt("camera.brake_zoom_spring",
                                def.camera.brakeZoomSpring,
                                () -> cfg.camera.brakeZoomSpring, v -> cfg.camera.brakeZoomSpring = v,
                                0.001f, 0.5f, 0.001f))
                        .option(floatOpt("camera.brake_zoom_damping",
                                def.camera.brakeZoomDamping,
                                () -> cfg.camera.brakeZoomDamping, v -> cfg.camera.brakeZoomDamping = v,
                                0.5f, 1.0f, 0.01f))
                        .build())
                .build();
    }

    // ── HUD ───────────────────────────────────────────────────────────────────

    private static ConfigCategory hud(MomentumConfig cfg, MomentumConfig def) {
        return ConfigCategory.createBuilder()
                .name(text("category.hud"))
                .group(OptionGroup.createBuilder()
                        .name(text("group.debug_overlay_position"))
                        .option(intOpt("hud.debug_x",
                                def.barHud.debugX, () -> cfg.barHud.debugX, v -> cfg.barHud.debugX = v, -1, 1920, 1))
                        .option(intOpt("hud.debug_y", def.barHud.debugY,
                                () -> cfg.barHud.debugY, v -> cfg.barHud.debugY = v, 0, 1080, 1))
                        .option(floatOpt("hud.debug_x_fraction", def.barHud.debugXFraction,
                                () -> cfg.barHud.debugXFraction, v -> cfg.barHud.debugXFraction = v, 0.0f, 1.0f, 0.001f))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(text("group.bar_position"))
                        .option(intOpt("hud.x",
                                def.barHud.x, () -> cfg.barHud.x, v -> cfg.barHud.x = v, -1, 1920, 1))
                        .option(intOpt("hud.y",
                                def.barHud.y, () -> cfg.barHud.y, v -> cfg.barHud.y = v, -1, 1080, 1))
                        .option(floatOpt("hud.x_fraction", def.barHud.xFraction,
                                () -> cfg.barHud.xFraction, v -> cfg.barHud.xFraction = v, 0.0f, 1.0f, 0.001f))
                        .option(intOpt("hud.margin_bottom", def.barHud.marginBottom,
                                () -> cfg.barHud.marginBottom, v -> cfg.barHud.marginBottom = v, 0, 500, 1))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(text("group.bar_size"))
                        .option(boolOpt("hud.compact_dashboard",
                                def.barHud.compactDashboard,
                                () -> cfg.barHud.compactDashboard, v -> cfg.barHud.compactDashboard = v))
                        .option(intOpt("hud.total_width",
                                def.barHud.totalWidth,
                                () -> cfg.barHud.totalWidth, v -> cfg.barHud.totalWidth = v, 24, 500, 1))
                        .option(intOpt("hud.total_height",
                                def.barHud.totalHeight,
                                () -> cfg.barHud.totalHeight, v -> cfg.barHud.totalHeight = v, 1, 100, 1))
                        .option(intOpt("hud.bar_width",
                                def.barHud.barWidth,
                                () -> cfg.barHud.barWidth, v -> cfg.barHud.barWidth = v, 1, 50, 1))
                        .option(intOpt("hud.bar_spacing",
                                def.barHud.barSpacing,
                                () -> cfg.barHud.barSpacing, v -> cfg.barHud.barSpacing = v, 0, 20, 1))
                        .option(floatOpt("hud.max_speed",
                                def.barHud.maxSpeedKmh,
                                () -> cfg.barHud.maxSpeedKmh, v -> cfg.barHud.maxSpeedKmh = v,
                                50.0f, 500.0f, 10.0f))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(text("group.bar_colors"))
                        .option(colorOpt("hud.bar_color",
                                def.barHud.barColor,
                                () -> cfg.barHud.barColor, v -> cfg.barHud.barColor = v))
                        .option(colorOpt("hud.boost_bar_color",
                                def.barHud.boostBarColor,
                                () -> cfg.barHud.boostBarColor, v -> cfg.barHud.boostBarColor = v))
                        .option(colorOpt("hud.text_color",
                                def.barHud.textColor,
                                () -> cfg.barHud.textColor, v -> cfg.barHud.textColor = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(text("group.bar_text_position"))
                        .option(intOpt("hud.text_offset_x",
                                def.barHud.textOffsetX,
                                () -> cfg.barHud.textOffsetX, v -> cfg.barHud.textOffsetX = v, -100, 100, 1))
                        .option(intOpt("hud.text_offset_y",
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
                        .name(text("option.general.drift_profile.name"))
                        .description(OptionDescription.of(text(
                                "option.general.drift_profile.description",
                                text("profile." + def.oDrift.profile.name().toLowerCase(java.util.Locale.ROOT)))))
                        .binding(def.oDrift.profile,
                                () -> cfg.oDrift.profile,
                                v  -> cfg.oDrift.profile = v)
                        .controller(opt -> EnumControllerBuilder.create(opt)
                                .enumClass(MomentumConfig.ODrift.Profile.class)
                                .formatValue(v -> text("profile." + v.name().toLowerCase(java.util.Locale.ROOT))))
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
        Option<Boolean> movOpt = boolOpt("general.movement",
                def.movement.enabled,
                () -> cfg.movement.enabled, v -> cfg.movement.enabled = v);
        movOpt.addListener((opt, val) -> cfg.movement.enabled = val);

        Option<Boolean> steerOpt = boolOpt("general.steering",
                def.steering.enabled,
                () -> cfg.steering.enabled, v -> cfg.steering.enabled = v);
        steerOpt.addListener((opt, val) -> cfg.steering.enabled = val);

        Option<Boolean> camOpt = boolOpt("general.camera",
                def.camera.enabled,
                () -> cfg.camera.enabled, v -> cfg.camera.enabled = v);
        camOpt.addListener((opt, val) -> cfg.camera.enabled = val);

        Option<Boolean> hudOpt = boolOpt("general.hud",
                def.barHud.enabled,
                () -> cfg.barHud.enabled, v -> cfg.barHud.enabled = v);
        hudOpt.addListener((opt, val) -> cfg.barHud.enabled = val);

        return ConfigCategory.createBuilder()
                .name(text("category.general"))
                .option(boolOpt("general.enabled",
                        def.enabled,
                        () -> cfg.enabled, v -> cfg.enabled = v))
                .group(OptionGroup.createBuilder()
                        .name(text("group.features"))
                        .option(profileOpt)
                        .option(movOpt)
                        .option(steerOpt)
                        .option(camOpt)
                        .option(hudOpt)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(text("group.key_bindings"))
                        .option(LabelOption.create(text("key_binding.brake",
                                InputConstants.Type.KEYSYM.getOrCreate(cfg.brakeKey).getDisplayName())))
                        .option(LabelOption.create(text("key_binding.handbrake",
                                InputConstants.Type.KEYSYM.getOrCreate(cfg.driftKey).getDisplayName())))
                        .option(LabelOption.create(text("key_binding.remap_hint")))
                        .build())
                .build();
    }

    // ── Drift ─────────────────────────────────────────────────────────────────

    private static ConfigCategory drift(MomentumConfig cfg, MomentumConfig def, Screen parent) {

        ConfigCategory.Builder b = ConfigCategory.createBuilder()
                .name(text("category.drift"));

        switch (cfg.oDrift.profile) {
            case ARCADE -> {
                BoostGroup  kg = arcadeDriftBoostGroup(cfg, def);
                CameraGroup kc = arcadeDriftCameraGroup(cfg, def);
                b.group(buildGroupFromList("slip",    arcadeDriftSlipOptions(cfg, def)));
                b.group(buildGroupFromList("trigger", arcadeDriftTriggerOptions(cfg, def)));
                b.group(buildGroupFromList("boost",   kg.all()));
                b.group(buildGroupFromList("camera",  kc.all()));
            }
            case RESPONSIVE -> {
                List<Option<?>> mSlip = responsiveDriftSlipOptions(cfg, def);
                BoostGroup      mg    = responsiveDriftBoostGroup(cfg, def);
                CameraGroup     mc2   = responsiveDriftCameraGroup(cfg, def);
                b.group(buildGroupFromList("slip",     mSlip));
                b.group(buildGroupFromList("steering", responsiveDriftSteeringOptions(cfg, def)));
                b.group(buildGroupFromList("trigger",  responsiveDriftTriggerOptions(cfg, def)));
                b.group(buildGroupFromList("boost",    mg.all()));
                b.group(buildGroupFromList("camera",   mc2.all()));
            }
            case VANILLA -> { /* No groups - Vanilla Drift uses Automobility defaults */ }
        }

        return b.build();
    }

    // ── Arcade Drift group builders ───────────────────────────────────────────

    private static List<Option<?>> arcadeDriftSlipOptions(MomentumConfig cfg, MomentumConfig def) {
        return List.of(
                floatOpt("drift.slip_angle",
                        def.arcadeDrift.slipAngle,
                        () -> cfg.arcadeDrift.slipAngle, v -> cfg.arcadeDrift.slipAngle = v,
                        0.0f, 45.0f, 0.5f),
                floatOpt("drift.arcade_slip_converge_rate",
                        def.arcadeDrift.slipConvergeRate,
                        () -> cfg.arcadeDrift.slipConvergeRate, v -> cfg.arcadeDrift.slipConvergeRate = v,
                        0.1f, 20.0f, 0.1f),
                floatOpt("drift.slip_decay",
                        def.arcadeDrift.slipDecay,
                        () -> cfg.arcadeDrift.slipDecay, v -> cfg.arcadeDrift.slipDecay = v,
                        0.0f, 5.0f, 0.05f),
                floatOpt("drift.slip_decay_speed_ref",
                        def.arcadeDrift.slipDecaySpeedRef,
                        () -> cfg.arcadeDrift.slipDecaySpeedRef, v -> cfg.arcadeDrift.slipDecaySpeedRef = v,
                        0.0f, 2.0f, 0.01f)
        );
    }

    private static List<Option<?>> arcadeDriftTriggerOptions(MomentumConfig cfg, MomentumConfig def) {
        return List.of(
                floatOpt("drift.min_speed",
                        def.arcadeDrift.minSpeedKmh,
                        () -> cfg.arcadeDrift.minSpeedKmh, v -> cfg.arcadeDrift.minSpeedKmh = v,
                        0.0f, 200.0f, 5.0f),
                floatOpt("drift.trigger_steer_threshold",
                        def.arcadeDrift.steerThreshold,
                        () -> cfg.arcadeDrift.steerThreshold, v -> cfg.arcadeDrift.steerThreshold = v,
                        0.0f, 1.0f, 0.05f),
                intOpt("drift.min_hold_ticks",
                        def.arcadeDrift.minHoldTicks,
                        () -> cfg.arcadeDrift.minHoldTicks, v -> cfg.arcadeDrift.minHoldTicks = v,
                        0, 100, 1),
                intOpt("drift.auto_trigger_ticks",
                        def.arcadeDrift.autoTriggerTicks,
                        () -> cfg.arcadeDrift.autoTriggerTicks, v -> cfg.arcadeDrift.autoTriggerTicks = v,
                        0, 200, 1),
                boolOpt("drift.brake_enabled",
                        def.arcadeDrift.brakeEnabled,
                        () -> cfg.arcadeDrift.brakeEnabled, v -> cfg.arcadeDrift.brakeEnabled = v)
        );
    }

    private static BoostGroup arcadeDriftBoostGroup(MomentumConfig cfg, MomentumConfig def) {
        Option<Boolean> toggle   = boolOpt("drift.boost_enabled",
                def.arcadeDrift.boostEnabled,
                () -> cfg.arcadeDrift.boostEnabled, v -> cfg.arcadeDrift.boostEnabled = v);
        Option<Float>   boost    = floatOpt("drift.boost",
                def.arcadeDrift.boost,
                () -> cfg.arcadeDrift.boost, v -> cfg.arcadeDrift.boost = v,
                0.0f, 0.5f, 0.005f);
        Option<Integer> duration = intOpt("drift.boost_duration",
                def.arcadeDrift.boostDuration,
                () -> cfg.arcadeDrift.boostDuration, v -> cfg.arcadeDrift.boostDuration = v,
                0, 200, 1);
        Option<Integer> minTicks = intOpt("drift.min_ticks",
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
        Option<Boolean> toggle  = boolOpt("drift.camera_enabled",
                def.arcadeDrift.cameraEnabled,
                () -> cfg.arcadeDrift.cameraEnabled, v -> cfg.arcadeDrift.cameraEnabled = v);
        Option<Float> scale     = floatOpt("drift.camera_scale",
                def.arcadeDrift.cameraScale,
                () -> cfg.arcadeDrift.cameraScale, v -> cfg.arcadeDrift.cameraScale = v,
                0.0f, 10.0f, 0.1f);
        Option<Float> lerpIn    = floatOpt("drift.camera_lerp_in",
                def.arcadeDrift.cameraLerpIn,
                () -> cfg.arcadeDrift.cameraLerpIn, v -> cfg.arcadeDrift.cameraLerpIn = v,
                0.01f, 1.0f, 0.01f);
        Option<Float> lerpOut   = floatOpt("drift.camera_lerp_out",
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
        Option<Float>   slipAngle        = floatOpt("drift.slip_angle",
                def.responsiveDrift.slipAngle,
                () -> cfg.responsiveDrift.slipAngle, v -> cfg.responsiveDrift.slipAngle = v,
                0.0f, 90.0f, 0.5f);
        Option<Float>   slipConvergeRate = floatOpt("drift.responsive_slip_converge_rate",
                def.responsiveDrift.slipConvergeRate,
                () -> cfg.responsiveDrift.slipConvergeRate, v -> cfg.responsiveDrift.slipConvergeRate = v,
                0.01f, 1.0f, 0.01f);
        Option<Float>   slipDecay        = floatOpt("drift.slip_decay",
                def.responsiveDrift.slipDecay,
                () -> cfg.responsiveDrift.slipDecay, v -> cfg.responsiveDrift.slipDecay = v,
                0.0f, 10.0f, 0.1f);
        Option<Float>   slipDecaySpeedRef = floatOpt("drift.slip_decay_speed_ref",
                def.responsiveDrift.slipDecaySpeedRef,
                () -> cfg.responsiveDrift.slipDecaySpeedRef, v -> cfg.responsiveDrift.slipDecaySpeedRef = v,
                0.0f, 2.0f, 0.01f);
        Option<Boolean> constantAngle    = boolOpt("drift.constant_angle",
                def.responsiveDrift.constantAngle,
                () -> cfg.responsiveDrift.constantAngle, v -> cfg.responsiveDrift.constantAngle = v);

        constantAngle.addListener((opt, val) -> slipConvergeRate.setAvailable(!val));
        slipConvergeRate.setAvailable(!cfg.responsiveDrift.constantAngle);

        return List.of(slipAngle, slipConvergeRate, slipDecay, slipDecaySpeedRef, constantAngle);
    }

    private static List<Option<?>> responsiveDriftSteeringOptions(MomentumConfig cfg, MomentumConfig def) {
        return List.of(
                floatOpt("drift.steer_sensitivity",
                        def.responsiveDrift.steerSensitivity,
                        () -> cfg.responsiveDrift.steerSensitivity, v -> cfg.responsiveDrift.steerSensitivity = v,
                        0.1f, 10.0f, 0.1f),
                floatOpt("drift.steer_build_rate",
                        def.responsiveDrift.steerBuildRate,
                        () -> cfg.responsiveDrift.steerBuildRate, v -> cfg.responsiveDrift.steerBuildRate = v,
                        0.001f, 0.5f, 0.001f),
                floatOpt("drift.steer_decay_rate",
                        def.responsiveDrift.steerDecayRate,
                        () -> cfg.responsiveDrift.steerDecayRate, v -> cfg.responsiveDrift.steerDecayRate = v,
                        0.001f, 0.5f, 0.001f),
                floatOpt("drift.maintain_steer_threshold",
                        def.responsiveDrift.steerThreshold,
                        () -> cfg.responsiveDrift.steerThreshold, v -> cfg.responsiveDrift.steerThreshold = v,
                        0.0f, 1.0f, 0.05f)
        );
    }

    private static List<Option<?>> responsiveDriftTriggerOptions(MomentumConfig cfg, MomentumConfig def) {
        return List.of(
                floatOpt("drift.min_speed",
                        def.responsiveDrift.minSpeedKmh,
                        () -> cfg.responsiveDrift.minSpeedKmh, v -> cfg.responsiveDrift.minSpeedKmh = v,
                        0.0f, 200.0f, 5.0f),
                intOpt("drift.min_hold_ticks",
                        def.responsiveDrift.minHoldTicks,
                        () -> cfg.responsiveDrift.minHoldTicks, v -> cfg.responsiveDrift.minHoldTicks = v,
                        0, 100, 1),
                intOpt("drift.auto_trigger_ticks",
                        def.responsiveDrift.autoTriggerTicks,
                        () -> cfg.responsiveDrift.autoTriggerTicks, v -> cfg.responsiveDrift.autoTriggerTicks = v,
                        0, 200, 1),
                boolOpt("drift.brake_enabled",
                        def.responsiveDrift.brakeEnabled,
                        () -> cfg.responsiveDrift.brakeEnabled, v -> cfg.responsiveDrift.brakeEnabled = v)
        );
    }

    private static BoostGroup responsiveDriftBoostGroup(MomentumConfig cfg, MomentumConfig def) {
        Option<Boolean> toggle   = boolOpt("drift.boost_enabled",
                def.responsiveDrift.boostEnabled,
                () -> cfg.responsiveDrift.boostEnabled, v -> cfg.responsiveDrift.boostEnabled = v);
        Option<Float>   boost    = floatOpt("drift.boost",
                def.responsiveDrift.boost,
                () -> cfg.responsiveDrift.boost, v -> cfg.responsiveDrift.boost = v,
                0.0f, 0.5f, 0.005f);
        Option<Integer> duration = intOpt("drift.boost_duration",
                def.responsiveDrift.boostDuration,
                () -> cfg.responsiveDrift.boostDuration, v -> cfg.responsiveDrift.boostDuration = v,
                0, 200, 1);
        Option<Integer> minTicks = intOpt("drift.min_ticks",
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
        Option<Boolean> toggle  = boolOpt("drift.camera_enabled",
                def.responsiveDrift.cameraEnabled,
                () -> cfg.responsiveDrift.cameraEnabled, v -> cfg.responsiveDrift.cameraEnabled = v);
        Option<Float> scale     = floatOpt("drift.camera_scale",
                def.responsiveDrift.cameraScale,
                () -> cfg.responsiveDrift.cameraScale, v -> cfg.responsiveDrift.cameraScale = v,
                0.0f, 10.0f, 0.1f);
        Option<Float> lerpIn    = floatOpt("drift.camera_lerp_in",
                def.responsiveDrift.cameraLerpIn,
                () -> cfg.responsiveDrift.cameraLerpIn, v -> cfg.responsiveDrift.cameraLerpIn = v,
                0.01f, 1.0f, 0.01f);
        Option<Float> lerpOut   = floatOpt("drift.camera_lerp_out",
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
        OptionGroup.Builder b = OptionGroup.createBuilder().name(text("group." + name));
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

    private static Component text(String key, Object... args) {
        return Component.translatable("config.momentum." + key, args);
    }

    private static Component optionName(String key) {
        return text("option." + key + ".name");
    }

    private static Component optionDescription(String key, Object defaultValue) {
        return text("option.with_default",
                text("option." + key + ".description"), defaultValue);
    }

    private static Option<Float> floatOpt(String key, float def,
            java.util.function.Supplier<Float> get, java.util.function.Consumer<Float> set,
            float min, float max, float step) {
        String defStr = String.format("%.3f", def);
        return Option.<Float>createBuilder()
                .name(optionName(key))
                .description(OptionDescription.of(optionDescription(key, defStr)))
                .binding(def, get, set)
                .controller(opt -> FloatSliderControllerBuilder.create(opt)
                        .range(min, max).step(step)
                        .formatValue(v -> Component.literal(String.format("%.3f", v))))
                .build();
    }

    private static Option<Integer> intOpt(String key, int def,
            java.util.function.Supplier<Integer> get, java.util.function.Consumer<Integer> set,
            int min, int max, int step) {
        return Option.<Integer>createBuilder()
                .name(optionName(key))
                .description(OptionDescription.of(optionDescription(key, def)))
                .binding(def, get, set)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(min, max).step(step))
                .build();
    }

    private static Option<Boolean> boolOpt(String key, boolean def,
            java.util.function.Supplier<Boolean> get, java.util.function.Consumer<Boolean> set) {
        return Option.<Boolean>createBuilder()
                .name(optionName(key))
                .description(OptionDescription.of(optionDescription(key,
                        text(def ? "value.enabled" : "value.disabled"))))
                .binding(def, get, set)
                .controller(BooleanControllerBuilder::create)
                .build();
    }

    private static Option<Color> colorOpt(String key, int defArgb,
            java.util.function.Supplier<Integer> get, java.util.function.Consumer<Integer> set) {
        String defStr = String.format("#%08X", defArgb);
        return Option.<Color>createBuilder()
                .name(optionName(key))
                .description(OptionDescription.of(optionDescription(key, defStr)))
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
