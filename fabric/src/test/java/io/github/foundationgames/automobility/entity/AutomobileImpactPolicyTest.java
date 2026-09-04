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

    @Test
    void stationaryAutomobileCannotDealRunOverDamageFromPlannedSpeedAlone() {
        assertEquals(
                Vec3.ZERO,
                AutomobileImpactPolicy.boundedVelocityForDamage(new Vec3(0, 0, 1), Vec3.ZERO)
        );
    }

    @Test
    void observedMovementCapsDamageAndSuppliesItsDirection() {
        var bounded = AutomobileImpactPolicy.boundedVelocityForDamage(
                new Vec3(0, 0, 1),
                new Vec3(0.3, 5, 0.4)
        );

        assertEquals(0.36, bounded.x, 1.0E-9);
        assertEquals(0, bounded.y, 1.0E-9);
        assertEquals(0.48, bounded.z, 1.0E-9);
        assertEquals(0.6, bounded.horizontalDistance(), 1.0E-9);
    }

    @Test
    void teleportDiscontinuityCannotProduceRunOverDamage() {
        assertEquals(
                Vec3.ZERO,
                AutomobileImpactPolicy.boundedVelocityForDamage(new Vec3(0, 0, 1), new Vec3(40, 0, 0))
        );
    }
}
