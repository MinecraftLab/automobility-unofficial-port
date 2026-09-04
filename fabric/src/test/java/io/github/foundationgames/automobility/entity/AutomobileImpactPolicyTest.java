package io.github.foundationgames.automobility.entity;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutomobileImpactPolicyTest {
    @Test
    void rejectsStationaryNonFiniteAndVerticalOnlyImpactVelocity() {
        assertEquals(0f, AutomobileImpactPolicy.speedForDamage(Vec3.ZERO));
        assertEquals(0f, AutomobileImpactPolicy.speedForDamage(new Vec3(0.2, 20, 0)));
        assertEquals(0f, AutomobileImpactPolicy.speedForDamage(new Vec3(Double.NaN, 0, 0)));
        assertEquals(0f, AutomobileImpactPolicy.speedForDamage(new Vec3(Double.POSITIVE_INFINITY, 0, 0)));
    }

    @Test
    void derivesImpactSpeedFromServerPlannedHorizontalVelocity() {
        assertEquals(0.5f, AutomobileImpactPolicy.speedForDamage(new Vec3(0.3, 100, 0.4)));
    }
}
