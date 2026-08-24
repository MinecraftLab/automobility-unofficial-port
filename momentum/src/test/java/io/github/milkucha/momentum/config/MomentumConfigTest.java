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
