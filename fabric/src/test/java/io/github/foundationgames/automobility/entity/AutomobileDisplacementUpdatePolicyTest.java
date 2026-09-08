package io.github.foundationgames.automobility.entity;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutomobileDisplacementUpdatePolicyTest {
    @Test
    void pendingPacketPositionRefreshesAStationaryAutomobile() {
        assertTrue(shouldUpdate(false, true, Vec3.ZERO, Vec3.ZERO, false));
    }

    @Test
    void stationaryAutomobileWithoutPacketPositionDoesNotRescan() {
        assertFalse(shouldUpdate(false, false, Vec3.ZERO, Vec3.ZERO, false));
    }

    @Test
    void existingFirstTickMovementAndRotationTriggersRemainIntact() {
        assertTrue(shouldUpdate(true, false, Vec3.ZERO, Vec3.ZERO, false));
        assertTrue(shouldUpdate(false, false, Vec3.ZERO, new Vec3(0.25, 0, 0), false));
        assertTrue(shouldUpdate(false, false, Vec3.ZERO, Vec3.ZERO, true));
    }

    private static boolean shouldUpdate(
            boolean firstTick,
            boolean packetPositionRefreshPending,
            Vec3 beforeSimulation,
            Vec3 afterSimulation,
            boolean yawChanged
    ) {
        return AutomobileDisplacementUpdatePolicy.shouldUpdate(
                firstTick,
                packetPositionRefreshPending,
                beforeSimulation,
                afterSimulation,
                yawChanged
        );
    }
}
