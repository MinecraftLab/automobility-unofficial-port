package io.github.milkucha.momentum.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.milkucha.momentum.Momentum;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

public class MomentumConfig {

    private static final int CURRENT_CONFIG_REVISION = 1;

    /** Persisted so one-time preset migrations never override later user choices. */
    public int configRevision = 0;

    public boolean enabled   = true;

    /**
     * A high-level chassis tune. Drift, camera, HUD, sound, keys and cruise control are deliberately
     * independent so choosing a driving preset never rewrites unrelated player preferences.
     */
    public DrivingPreset drivingPreset = DrivingPreset.BALANCED;
    /** Last hand-tuned chassis values, retained while a named preset is active. */
    public DrivingSettings customDrivingSettings;

    /** GLFW key code for the brake action. Default: S (83). Edit in momentum.json to remap. */
    public int brakeKey = GLFW.GLFW_KEY_S;
    /** GLFW key code for the handbrake/drift action. Default: Space (32). Edit in momentum.json to remap. */
    public int driftKey = GLFW.GLFW_KEY_SPACE;

    public Movement movement = new Movement();
    public Steering steering = new Steering();
    public Camera  camera   = new Camera();
    public BarHud  barHud   = new BarHud();
    public Cruise  cruise   = new Cruise();
    public ArcadeDrift    arcadeDrift    = new ArcadeDrift();
    public ResponsiveDrift responsiveDrift = new ResponsiveDrift();
    public ODrift  oDrift   = new ODrift();
    public Sound   sound    = new Sound();

    // ── Groups ────────────────────────────────────────────────────────────────

    public enum DrivingPreset {
        TOURING(0.005f, 6.5f, 0.024f, 1.10f, 0.08f, 0.28f, 0.75f, 2.5f),
        BALANCED(0.007f, 5.5f, 0.030f, 1.25f, 0.12f, 0.42f, 0.50f, 3.0f),
        SPORT(0.009f, 4.2f, 0.040f, 1.50f, 0.20f, 0.58f, 0.25f, 3.5f),
        CUSTOM;

        private final DrivingSettings settings;

        DrivingPreset(float coastDecay, float accelerationScale, float brakeDecay,
                      float comfortableSpeedMultiplier, float rampRate, float centerRate,
                      float understeer, float understeerCurve) {
            settings = new DrivingSettings(coastDecay, accelerationScale, brakeDecay,
                    comfortableSpeedMultiplier, rampRate, centerRate, understeer, understeerCurve);
        }

        DrivingPreset() {
            settings = null;
        }

        boolean isNamed() {
            return settings != null;
        }

        void applyTo(MomentumConfig config) {
            if (settings != null) settings.applyTo(config);
        }

        boolean matches(MomentumConfig config) {
            return settings != null && settings.matches(config);
        }
    }

    /** Only the eight values owned by a chassis preset; feature toggles stay untouched. */
    public static class DrivingSettings {
        public float coastDecay;
        public float accelerationScale;
        public float brakeDecay;
        public float comfortableSpeedMultiplier;
        public float rampRate;
        public float centerRate;
        public float understeer;
        public float understeerCurve;

        public DrivingSettings() {
        }

        private DrivingSettings(float coastDecay, float accelerationScale, float brakeDecay,
                                float comfortableSpeedMultiplier, float rampRate, float centerRate,
                                float understeer, float understeerCurve) {
            this.coastDecay = coastDecay;
            this.accelerationScale = accelerationScale;
            this.brakeDecay = brakeDecay;
            this.comfortableSpeedMultiplier = comfortableSpeedMultiplier;
            this.rampRate = rampRate;
            this.centerRate = centerRate;
            this.understeer = understeer;
            this.understeerCurve = understeerCurve;
        }

        static DrivingSettings capture(MomentumConfig config) {
            return new DrivingSettings(
                    config.movement.coastDecay,
                    config.movement.accelerationScale,
                    config.movement.brakeDecay,
                    config.movement.comfortableSpeedMultiplier,
                    config.steering.rampRate,
                    config.steering.centerRate,
                    config.steering.understeer,
                    config.steering.understeerCurve);
        }

        void applyTo(MomentumConfig config) {
            config.movement.coastDecay = coastDecay;
            config.movement.accelerationScale = accelerationScale;
            config.movement.brakeDecay = brakeDecay;
            config.movement.comfortableSpeedMultiplier = comfortableSpeedMultiplier;
            config.steering.rampRate = rampRate;
            config.steering.centerRate = centerRate;
            config.steering.understeer = understeer;
            config.steering.understeerCurve = understeerCurve;
        }

        boolean matches(MomentumConfig config) {
            return same(coastDecay, config.movement.coastDecay)
                    && same(accelerationScale, config.movement.accelerationScale)
                    && same(brakeDecay, config.movement.brakeDecay)
                    && same(comfortableSpeedMultiplier, config.movement.comfortableSpeedMultiplier)
                    && same(rampRate, config.steering.rampRate)
                    && same(centerRate, config.steering.centerRate)
                    && same(understeer, config.steering.understeer)
                    && same(understeerCurve, config.steering.understeerCurve);
        }
    }

    public static class Movement {
        public boolean enabled                = true;
        public float coastDecay               = 0.007f;
        public float accelerationScale        = 5.5f;
        public float brakeDecay               = 0.03f;
        public float comfortableSpeedMultiplier = 1.25f;
    }

    public static class Steering {
        public boolean enabled       = true;
        public float rampRate       = 0.12f;
        public float centerRate     = 0.42f;  // rate back to center when no steering key held
        public float understeer     = 0.5f;
        public float understeerCurve = 3.0f;
    }

    public static class Camera {
        public boolean enabled            = true;
        public boolean lock               = false;
        public float   pitch              = 10f;
        // Steering lean: camera yaw offset proportional to steering (−1..1).
        // At full lock the camera swings steeringTilt degrees toward the turn direction.
        public float   steeringTilt       = 5f;    // max degrees of yaw offset at full lock
        public float   steeringTiltLerp   = 0.1f;  // lerp factor per tick (higher = snappier)
        // Reverse camera: flips 180° when engineSpeed < 0, returns when moving forward.
        public boolean reverseFlip        = true;
        public float   reverseFlipLerp    = 0.2f;  // lerp factor per tick (ease-out toward 180°)
        public float   brakeZoomFov       = 10f;   // max FOV reduction clamp (degrees)
        // Spring-damper brake zoom: deceleration (hSpeed delta/tick) drives a mass-spring camera.
        // When the vehicle stops, accumulated velocity carries the zoom briefly - inertia feel.
        public float   brakeZoomInputScale = 10f;  // decel units → zoom force multiplier
        public float   brakeZoomSpring     = 0.02f; // spring constant (return-to-zero pull)
        public float   brakeZoomDamping    = 0.90f; // velocity decay per tick (0=none,1=freeze)
    }

    public static class BarHud {
        public static final int COMPACT_MIN_WIDTH = 104;
        public static final int COMPACT_MIN_HEIGHT = 17;

        public boolean enabled    = true;
        // Draw a stable speed panel plus an aligned cruise pod in compact dashboard chrome.
        // Disable only to retain the legacy text-above-bars layout.
        public boolean compactDashboard = true;
        // Explicit default keeps the complete dashboard cluster at the bottom left.
        // A negative X remains available for legacy/right-edge anchoring in custom configs.
        public int   x            = 12;
        public int   y            = -1;
        public float xFraction    = 0.016f;  // fraction of screenW from right edge; resolution-independent
        public int   marginBottom = 12;

        // Overall size of the velocimeter area in pixels.
        public int   totalWidth   = 104;
        public int   totalHeight  = 17;

        // Size of each individual bar segment and the gap between them.
        // numBars = floor((totalWidth + barSpacing) / (barWidth + barSpacing))
        public int   barWidth     = 5;
        public int   barSpacing   = 2;

        // Speed (km/h) at which all bar segments are filled.
        public float maxSpeedKmh  = 150.0f;

        // ARGB color of filled bar segments (e.g. 0xFFFFFFFF = opaque white).
        public int   barColor     = 0xFFFFFFFF;
        // ARGB color of bar segments that represent the boost contribution (hSpeed - engineSpeed).
        // These segments sit above the normal bars and revert to barColor when boost ends.
        public int   boostBarColor = 0xFFFFD831;

        // Speed text position relative to the bar's top-left corner.
        // Negative textOffsetY places the text above the bar.
        public int   textOffsetX  = 0;
        public int   textOffsetY  = -10;
        // ARGB color of the speed text.
        public int   textColor    = 0xFFFFFFFF;

        // Debug overlay.
        public boolean debug          = false;
        public int     debugX         = -1;
        public int     debugY         = 10;
        public float   debugXFraction = 0.016f; // fraction of screenW from right edge
    }

    public static class Cruise {
        public boolean enabled = true;
        public float minActivationKmh = 8.0f;
        public float maxTargetKmh = 150.0f;
        public float resumeThrottleBelowTargetKmh = 1.5f;
        public float cutThrottleBelowTargetKmh = 0.2f;
        public float impactCancelMinSpeedKmh = 15.0f;
        public float impactCancelDropKmh = 8.0f;
        public int activeColor = 0xFF55FFFF;
        public int acceleratingColor = 0xFF55FF99;
        public int coastColor = 0xFFB7FFF8;
    }

    public static class ArcadeDrift {
        public float   slipAngle         = 3f;
        public float   slipConvergeRate  = 4f;    // deg/tick the offset converges toward target while held
        public float   slipDecay         = 0.9f;
        public float   slipDecaySpeedRef = 0.41f;
        public float   boost             = 0.04f;
        public int     boostDuration     = 44;
        public int     minTicks          = 60;
        public boolean boostEnabled      = true;
        public boolean brakeEnabled      = true;
        public float   steerThreshold    = 0.1f;  // minimum |steering| to start drift (0 = any non-zero)
        public int     minHoldTicks      = 0;     // ticks drift key must be held before drift can start
        public int     autoTriggerTicks  = 8;     // ticks before auto-start in random direction (0 = disabled)
        public float   minSpeedKmh       = 45.0f; // minimum speed to start drift
        public boolean cameraEnabled     = true;
        public float   cameraScale       = 10.0f;
        public float   cameraLerpIn      = 0.1f;
        public float   cameraLerpOut     = 0.1f;
    }

    public static class ResponsiveDrift {
        public float   slipAngle         = 45f;
        public float   slipConvergeRate  = 0.18f; // fraction of remaining distance closed per tick (exponential ease-out toward target)
        public float   slipDecay         = 4.4f;  // deg/tick removed on release (linear, same formula as Arcade drift)
        public float   slipDecaySpeedRef = 0.6f;  // reference speed for speed-adjusted decay
        public float   boost             = 0.04f; // engine speed bonus on clean release
        public int     boostDuration     = 40;    // ticks the boost animation plays (20 ticks = 1 s)
        public int     minTicks          = 60;    // minimum ticks held to earn boost
        public float   steerSensitivity  = 2.0f;
        // How fast the steering accumulator (0..1) climbs per tick while steering is held.
        public float   steerBuildRate    = 0.08f;
        // How fast the accumulator falls per tick when steering is released mid-drift.
        public float   steerDecayRate    = 0.118f;
        public boolean constantAngle     = false;
        public int     minHoldTicks      = 0;
        public int     autoTriggerTicks  = 10;
        public float   steerThreshold    = 0.7f;
        public float   minSpeedKmh       = 45.0f;
        public boolean boostEnabled      = true;
        public boolean brakeEnabled      = true;
        public boolean cameraEnabled     = true;
        public float   cameraScale       = 1.8f;
        public float   cameraLerpIn      = 0.04f;
        public float   cameraLerpOut     = 0.25f;
    }

    public static class Sound {
        // km/h at which the engine sound reaches its pitch ceiling (Minecraft clamps pitch at 2.0).
        // Original Automobility value: ~91.7. Raise to hear pitch-bend continue to higher speeds.
        public float enginePitchCeiling = 120.0f;
    }

    public static class ODrift {
        public enum Profile { VANILLA, ARCADE, RESPONSIVE }
        public Profile profile = Profile.RESPONSIVE;
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("momentum.json");

    private static MomentumConfig instance;
    private static volatile MomentumConfig serverGameplayConfig;

    public static MomentumConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public static MomentumConfig gameplay() {
        var serverConfig = serverGameplayConfig;
        return serverConfig != null ? serverConfig : get();
    }

    public static boolean hasServerGameplayConfig() {
        return serverGameplayConfig != null;
    }

    public static void reload() {
        instance = load();
    }

    public static String serializeForSync() {
        return GSON.toJson(get());
    }

    public static boolean applyServerGameplayConfig(String json) {
        try {
            var loaded = GSON.fromJson(json, MomentumConfig.class);
            if (loaded == null) {
                return false;
            }
            loaded.sanitize();
            serverGameplayConfig = loaded;
            return true;
        } catch (RuntimeException e) {
            Momentum.LOGGER.warn("Ignoring malformed Momentum gameplay config from server", e);
            return false;
        }
    }

    public static void clearServerGameplayConfig() {
        serverGameplayConfig = null;
    }

    public static MomentumConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                MomentumConfig loaded = GSON.fromJson(json, MomentumConfig.class);
                if (loaded != null) {
                    loaded.sanitize();
                    loaded.save();
                    return loaded;
                }
                backupMalformedConfig();
                Momentum.LOGGER.warn("Momentum config was empty; using defaults");
            } catch (IOException e) {
                Momentum.LOGGER.warn("Failed to read Momentum config; using defaults", e);
            } catch (RuntimeException e) {
                backupMalformedConfig();
                Momentum.LOGGER.warn("Momentum config is malformed; using defaults", e);
            }
        }
        MomentumConfig defaults = new MomentumConfig();
        defaults.sanitize();
        defaults.save();
        return defaults;
    }

    public void save() {
        sanitize();
        Path temporary = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(temporary, GSON.toJson(this));
            try {
                Files.move(temporary, CONFIG_PATH, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Momentum.LOGGER.warn("Failed to save Momentum config", e);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Applies one coherent chassis tune. Moving away from Custom snapshots the current hand-tuned
     * values; returning to Custom restores them exactly.
     */
    public void applyDrivingPreset(DrivingPreset preset) {
        sanitize();
        DrivingPreset requested = preset != null ? preset : DrivingPreset.BALANCED;

        if (drivingPreset == DrivingPreset.CUSTOM) {
            customDrivingSettings = DrivingSettings.capture(this);
        }

        if (requested == DrivingPreset.CUSTOM) {
            if (customDrivingSettings != null) {
                customDrivingSettings.applyTo(this);
            } else {
                customDrivingSettings = DrivingSettings.capture(this);
            }
        } else {
            requested.applyTo(this);
        }

        drivingPreset = requested;
        sanitize();
    }

    /** Marks direct slider or JSON-style chassis edits as a restorable Custom tune. */
    public void markDrivingSettingsCustom() {
        drivingPreset = DrivingPreset.CUSTOM;
        customDrivingSettings = DrivingSettings.capture(this);
    }

    void sanitize() {
        var defaults = new MomentumConfig();

        if (movement == null) movement = new Movement();
        if (steering == null) steering = new Steering();
        if (camera == null) camera = new Camera();
        if (barHud == null) barHud = new BarHud();
        if (cruise == null) cruise = new Cruise();
        if (arcadeDrift == null) arcadeDrift = new ArcadeDrift();
        if (responsiveDrift == null) responsiveDrift = new ResponsiveDrift();
        if (oDrift == null) oDrift = new ODrift();
        if (oDrift.profile == null) oDrift.profile = defaults.oDrift.profile;
        if (sound == null) sound = new Sound();

        brakeKey = validKey(brakeKey, defaults.brakeKey);
        driftKey = validKey(driftKey, defaults.driftKey);

        movement.coastDecay = finite(movement.coastDecay, 0f, 1f, defaults.movement.coastDecay);
        movement.accelerationScale = finite(movement.accelerationScale, 0.01f, 100f, defaults.movement.accelerationScale);
        movement.brakeDecay = finite(movement.brakeDecay, 0f, 1f, defaults.movement.brakeDecay);
        movement.comfortableSpeedMultiplier = finite(movement.comfortableSpeedMultiplier, 0.1f, 10f, defaults.movement.comfortableSpeedMultiplier);

        steering.rampRate = finite(steering.rampRate, 0f, 1f, defaults.steering.rampRate);
        steering.centerRate = finite(steering.centerRate, 0f, 1f, defaults.steering.centerRate);
        steering.understeer = finite(steering.understeer, 0f, 100f, defaults.steering.understeer);
        steering.understeerCurve = finite(steering.understeerCurve, 0.01f, 20f, defaults.steering.understeerCurve);

        sanitizeDrivingPreset();

        camera.pitch = finite(camera.pitch, -90f, 90f, defaults.camera.pitch);
        camera.steeringTilt = finite(camera.steeringTilt, -180f, 180f, defaults.camera.steeringTilt);
        camera.steeringTiltLerp = finite(camera.steeringTiltLerp, 0f, 1f, defaults.camera.steeringTiltLerp);
        camera.reverseFlipLerp = finite(camera.reverseFlipLerp, 0f, 1f, defaults.camera.reverseFlipLerp);
        camera.brakeZoomFov = finite(camera.brakeZoomFov, 0f, 179f, defaults.camera.brakeZoomFov);
        camera.brakeZoomInputScale = finite(camera.brakeZoomInputScale, 0f, 1000f, defaults.camera.brakeZoomInputScale);
        camera.brakeZoomSpring = finite(camera.brakeZoomSpring, 0f, 10f, defaults.camera.brakeZoomSpring);
        camera.brakeZoomDamping = finite(camera.brakeZoomDamping, 0f, 1f, defaults.camera.brakeZoomDamping);

        migrateRosenvikCompactPreset();
        barHud.x = bounded(barHud.x, -1, 1_000_000);
        barHud.y = bounded(barHud.y, -1, 1_000_000);
        barHud.xFraction = finite(barHud.xFraction, 0f, 1f, defaults.barHud.xFraction);
        barHud.marginBottom = bounded(barHud.marginBottom, 0, 1_000_000);
        int minimumHudWidth = barHud.compactDashboard ? BarHud.COMPACT_MIN_WIDTH : 24;
        int minimumHudHeight = barHud.compactDashboard ? BarHud.COMPACT_MIN_HEIGHT : 1;
        barHud.totalWidth = bounded(barHud.totalWidth, minimumHudWidth, 4096);
        barHud.totalHeight = bounded(barHud.totalHeight, minimumHudHeight, 4096);
        barHud.barWidth = bounded(barHud.barWidth, 1, 4096);
        barHud.barSpacing = bounded(barHud.barSpacing, 0, 4096);
        barHud.maxSpeedKmh = finite(barHud.maxSpeedKmh, 1f, 10_000f, defaults.barHud.maxSpeedKmh);
        barHud.textOffsetX = bounded(barHud.textOffsetX, -4096, 4096);
        barHud.textOffsetY = bounded(barHud.textOffsetY, -4096, 4096);
        barHud.debugX = bounded(barHud.debugX, -1, 1_000_000);
        barHud.debugY = bounded(barHud.debugY, 0, 1_000_000);
        barHud.debugXFraction = finite(barHud.debugXFraction, 0f, 1f, defaults.barHud.debugXFraction);

        cruise.minActivationKmh = finite(cruise.minActivationKmh, 0f, 10_000f, defaults.cruise.minActivationKmh);
        cruise.maxTargetKmh = finite(cruise.maxTargetKmh, cruise.minActivationKmh, 10_000f, defaults.cruise.maxTargetKmh);
        cruise.resumeThrottleBelowTargetKmh = finite(cruise.resumeThrottleBelowTargetKmh, 0f, 1000f, defaults.cruise.resumeThrottleBelowTargetKmh);
        cruise.cutThrottleBelowTargetKmh = finite(cruise.cutThrottleBelowTargetKmh, 0f, 1000f, defaults.cruise.cutThrottleBelowTargetKmh);
        cruise.impactCancelMinSpeedKmh = finite(cruise.impactCancelMinSpeedKmh, 0f, 10_000f, defaults.cruise.impactCancelMinSpeedKmh);
        cruise.impactCancelDropKmh = finite(cruise.impactCancelDropKmh, 0f, 10_000f, defaults.cruise.impactCancelDropKmh);

        sanitizeArcade(defaults.arcadeDrift);
        sanitizeResponsive(defaults.responsiveDrift);
        sound.enginePitchCeiling = finite(sound.enginePitchCeiling, 1f, 10_000f, defaults.sound.enginePitchCeiling);
    }

    private void sanitizeDrivingPreset() {
        if (drivingPreset == null) {
            drivingPreset = DrivingPreset.BALANCED;
        }

        if (customDrivingSettings != null) {
            customDrivingSettings.coastDecay = finite(customDrivingSettings.coastDecay, 0f, 1f, movement.coastDecay);
            customDrivingSettings.accelerationScale = finite(customDrivingSettings.accelerationScale, 0.01f, 100f, movement.accelerationScale);
            customDrivingSettings.brakeDecay = finite(customDrivingSettings.brakeDecay, 0f, 1f, movement.brakeDecay);
            customDrivingSettings.comfortableSpeedMultiplier = finite(customDrivingSettings.comfortableSpeedMultiplier, 0.1f, 10f, movement.comfortableSpeedMultiplier);
            customDrivingSettings.rampRate = finite(customDrivingSettings.rampRate, 0f, 1f, steering.rampRate);
            customDrivingSettings.centerRate = finite(customDrivingSettings.centerRate, 0f, 1f, steering.centerRate);
            customDrivingSettings.understeer = finite(customDrivingSettings.understeer, 0f, 100f, steering.understeer);
            customDrivingSettings.understeerCurve = finite(customDrivingSettings.understeerCurve, 0.01f, 20f, steering.understeerCurve);
        }

        // A named preset describes exact values; it never overwrites a hand edit during load/save.
        if (!drivingPreset.isNamed() || !drivingPreset.matches(this)) {
            drivingPreset = DrivingPreset.CUSTOM;
            customDrivingSettings = DrivingSettings.capture(this);
        }
    }

    private void migrateRosenvikCompactPreset() {
        if (configRevision >= CURRENT_CONFIG_REVISION) {
            return;
        }

        boolean legacyCenteredPreset = barHud.compactDashboard && barHud.x == -1 && barHud.y == -1
                && barHud.totalWidth == 90 && barHud.totalHeight == 15
                && Float.compare(barHud.xFraction, 0.5f) == 0
                && barHud.marginBottom == 29;
        boolean previousRightDockedPreset = barHud.compactDashboard && barHud.x == -1 && barHud.y == -1
                && barHud.totalWidth == 104 && barHud.totalHeight == 17
                && Float.compare(barHud.xFraction, 0.016f) == 0
                && barHud.marginBottom == 12;
        if (legacyCenteredPreset || previousRightDockedPreset) {
            var current = new BarHud();
            barHud.x = current.x;
            barHud.y = current.y;
            barHud.xFraction = current.xFraction;
            barHud.marginBottom = current.marginBottom;
            barHud.totalWidth = current.totalWidth;
            barHud.totalHeight = current.totalHeight;
        }

        configRevision = CURRENT_CONFIG_REVISION;
    }

    private void sanitizeArcade(ArcadeDrift defaults) {
        arcadeDrift.slipAngle = finite(arcadeDrift.slipAngle, 0f, 180f, defaults.slipAngle);
        arcadeDrift.slipConvergeRate = finite(arcadeDrift.slipConvergeRate, 0f, 180f, defaults.slipConvergeRate);
        arcadeDrift.slipDecay = finite(arcadeDrift.slipDecay, 0f, 180f, defaults.slipDecay);
        arcadeDrift.slipDecaySpeedRef = finite(arcadeDrift.slipDecaySpeedRef, 0f, 100f, defaults.slipDecaySpeedRef);
        arcadeDrift.boost = finite(arcadeDrift.boost, 0f, 10f, defaults.boost);
        arcadeDrift.boostDuration = bounded(arcadeDrift.boostDuration, 0, 12_000);
        arcadeDrift.minTicks = bounded(arcadeDrift.minTicks, 0, 12_000);
        arcadeDrift.steerThreshold = finite(arcadeDrift.steerThreshold, 0f, 1f, defaults.steerThreshold);
        arcadeDrift.minHoldTicks = bounded(arcadeDrift.minHoldTicks, 0, 12_000);
        arcadeDrift.autoTriggerTicks = bounded(arcadeDrift.autoTriggerTicks, 0, 12_000);
        arcadeDrift.minSpeedKmh = finite(arcadeDrift.minSpeedKmh, 0f, 10_000f, defaults.minSpeedKmh);
        arcadeDrift.cameraScale = finite(arcadeDrift.cameraScale, 0f, 100f, defaults.cameraScale);
        arcadeDrift.cameraLerpIn = finite(arcadeDrift.cameraLerpIn, 0f, 1f, defaults.cameraLerpIn);
        arcadeDrift.cameraLerpOut = finite(arcadeDrift.cameraLerpOut, 0f, 1f, defaults.cameraLerpOut);
    }

    private void sanitizeResponsive(ResponsiveDrift defaults) {
        responsiveDrift.slipAngle = finite(responsiveDrift.slipAngle, 0f, 180f, defaults.slipAngle);
        responsiveDrift.slipConvergeRate = finite(responsiveDrift.slipConvergeRate, 0f, 1f, defaults.slipConvergeRate);
        responsiveDrift.slipDecay = finite(responsiveDrift.slipDecay, 0f, 180f, defaults.slipDecay);
        responsiveDrift.slipDecaySpeedRef = finite(responsiveDrift.slipDecaySpeedRef, 0f, 100f, defaults.slipDecaySpeedRef);
        responsiveDrift.boost = finite(responsiveDrift.boost, 0f, 10f, defaults.boost);
        responsiveDrift.boostDuration = bounded(responsiveDrift.boostDuration, 0, 12_000);
        responsiveDrift.minTicks = bounded(responsiveDrift.minTicks, 0, 12_000);
        responsiveDrift.steerSensitivity = finite(responsiveDrift.steerSensitivity, 0.01f, 100f, defaults.steerSensitivity);
        responsiveDrift.steerBuildRate = finite(responsiveDrift.steerBuildRate, 0f, 1f, defaults.steerBuildRate);
        responsiveDrift.steerDecayRate = finite(responsiveDrift.steerDecayRate, 0f, 1f, defaults.steerDecayRate);
        responsiveDrift.minHoldTicks = bounded(responsiveDrift.minHoldTicks, 0, 12_000);
        responsiveDrift.autoTriggerTicks = bounded(responsiveDrift.autoTriggerTicks, 0, 12_000);
        responsiveDrift.steerThreshold = finite(responsiveDrift.steerThreshold, 0f, 1f, defaults.steerThreshold);
        responsiveDrift.minSpeedKmh = finite(responsiveDrift.minSpeedKmh, 0f, 10_000f, defaults.minSpeedKmh);
        responsiveDrift.cameraScale = finite(responsiveDrift.cameraScale, 0f, 100f, defaults.cameraScale);
        responsiveDrift.cameraLerpIn = finite(responsiveDrift.cameraLerpIn, 0f, 1f, defaults.cameraLerpIn);
        responsiveDrift.cameraLerpOut = finite(responsiveDrift.cameraLerpOut, 0f, 1f, defaults.cameraLerpOut);
    }

    private static int validKey(int key, int fallback) {
        return key >= GLFW.GLFW_KEY_UNKNOWN && key <= GLFW.GLFW_KEY_LAST ? key : fallback;
    }

    private static float finite(float value, float min, float max, float fallback) {
        return Math.clamp(Float.isFinite(value) ? value : fallback, min, max);
    }

    private static int bounded(int value, int min, int max) {
        return Math.clamp(value, min, max);
    }

    private static boolean same(float left, float right) {
        return Float.compare(left, right) == 0;
    }

    private static void backupMalformedConfig() {
        try {
            String backupName = CONFIG_PATH.getFileName() + ".broken-" + Instant.now().toEpochMilli();
            Files.copy(CONFIG_PATH, CONFIG_PATH.resolveSibling(backupName));
        } catch (IOException e) {
            Momentum.LOGGER.warn("Failed to back up malformed Momentum config", e);
        }
    }
}
