package io.github.milkucha.momentum.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MomentumConfigTest {
    @AfterEach
    void clearServerConfig() {
        MomentumConfig.clearServerGameplayConfig();
    }

    @Test
    void sanitizeRepairsUnsafeNumericValues() {
        var config = new MomentumConfig();
        config.movement.accelerationScale = 0f;
        config.barHud.maxSpeedKmh = Float.NaN;
        config.barHud.barWidth = 0;
        config.responsiveDrift.slipConvergeRate = Float.POSITIVE_INFINITY;
        config.sound.enginePitchCeiling = 0f;

        config.sanitize();

        assertTrue(config.movement.accelerationScale > 0f);
        assertTrue(Float.isFinite(config.barHud.maxSpeedKmh));
        assertEquals(1, config.barHud.barWidth);
        assertTrue(Float.isFinite(config.responsiveDrift.slipConvergeRate));
        assertTrue(config.sound.enginePitchCeiling > 0f);
    }

    @Test
    void sanitizeRestoresMissingGroups() {
        var config = new MomentumConfig();
        config.movement = null;
        config.barHud = null;
        config.oDrift = null;

        config.sanitize();

        assertTrue(config.movement.accelerationScale > 0f);
        assertTrue(config.barHud.maxSpeedKmh > 0f);
        assertTrue(config.barHud.compactDashboard);
        assertEquals(MomentumConfig.ODrift.Profile.RESPONSIVE, config.oDrift.profile);
    }

    @Test
    void compactDashboardIsEnabledForExistingConfigsWithoutTheNewField() {
        var config = new Gson().fromJson("""
                {
                  "barHud": {
                    "enabled": true,
                    "totalWidth": 90,
                    "totalHeight": 15
                  }
                }
                """, MomentumConfig.class);

        config.sanitize();

        assertTrue(config.barHud.compactDashboard);
    }

    @Test
    void compactDashboardSanitizesToOneStableBridgeContract() {
        var config = new MomentumConfig();
        config.barHud.compactDashboard = true;
        config.barHud.totalWidth = 60;
        config.barHud.totalHeight = 8;

        config.sanitize();

        assertEquals(MomentumConfig.BarHud.COMPACT_MIN_WIDTH, config.barHud.totalWidth);
        assertEquals(MomentumConfig.BarHud.COMPACT_MIN_HEIGHT, config.barHud.totalHeight);
    }

    @Test
    void exactPreviousRosenvikCompactPresetMigratesToTheNewCluster() {
        var config = new MomentumConfig();
        config.barHud.compactDashboard = true;
        config.barHud.x = -1;
        config.barHud.y = -1;
        config.barHud.xFraction = 0.5f;
        config.barHud.marginBottom = 29;
        config.barHud.totalWidth = 90;
        config.barHud.totalHeight = 15;

        config.sanitize();

        assertEquals(12, config.barHud.x);
        assertEquals(-1, config.barHud.y);
        assertEquals(0.016f, config.barHud.xFraction);
        assertEquals(12, config.barHud.marginBottom);
        assertEquals(104, config.barHud.totalWidth);
        assertEquals(17, config.barHud.totalHeight);
    }

    @Test
    void exactPreviousRightDockedClusterMigratesToBottomLeft() {
        var config = new MomentumConfig();
        config.barHud.compactDashboard = true;
        config.barHud.x = -1;
        config.barHud.y = -1;
        config.barHud.xFraction = 0.016f;
        config.barHud.marginBottom = 12;
        config.barHud.totalWidth = 104;
        config.barHud.totalHeight = 17;

        config.sanitize();

        assertEquals(12, config.barHud.x);
        assertEquals(-1, config.barHud.y);
        assertEquals(0.016f, config.barHud.xFraction);
        assertEquals(12, config.barHud.marginBottom);
        assertEquals(104, config.barHud.totalWidth);
        assertEquals(17, config.barHud.totalHeight);
    }

    @Test
    void persistedMigrationMarkerLetsTheUserMoveBackToTheRight() {
        var config = new MomentumConfig();
        config.barHud.x = -1;

        config.sanitize();
        assertEquals(12, config.barHud.x);

        String persisted = new Gson().toJson(config);
        var reloaded = new Gson().fromJson(persisted, MomentumConfig.class);
        reloaded.barHud.x = -1;
        reloaded.sanitize();

        assertEquals(-1, reloaded.barHud.x);
    }

    @Test
    void explicitlyPositionedPreviousPresetIsNotPositionMigrated() {
        var config = new MomentumConfig();
        config.barHud.compactDashboard = true;
        config.barHud.x = 120;
        config.barHud.y = 240;
        config.barHud.xFraction = 0.5f;
        config.barHud.marginBottom = 29;
        config.barHud.totalWidth = 90;
        config.barHud.totalHeight = 15;

        config.sanitize();

        assertEquals(120, config.barHud.x);
        assertEquals(240, config.barHud.y);
        assertEquals(0.5f, config.barHud.xFraction);
        assertEquals(29, config.barHud.marginBottom);
    }

    @Test
    void legacyDashboardUsesTheBridgeSafeMinimumWidth() {
        var config = new MomentumConfig();
        config.barHud.compactDashboard = false;
        config.barHud.totalWidth = 10;
        config.barHud.totalHeight = 1;

        config.sanitize();

        assertEquals(24, config.barHud.totalWidth);
        assertEquals(1, config.barHud.totalHeight);
    }

    @Test
    void serverGameplayConfigOverridesPhysicsValues() {
        assertTrue(MomentumConfig.applyServerGameplayConfig("""
                {
                  "movement": {"accelerationScale": 7.25},
                  "oDrift": {"profile": "ARCADE"}
                }
                """));

        assertEquals(7.25f, MomentumConfig.gameplay().movement.accelerationScale);
        assertEquals(MomentumConfig.ODrift.Profile.ARCADE, MomentumConfig.gameplay().oDrift.profile);
    }
}
