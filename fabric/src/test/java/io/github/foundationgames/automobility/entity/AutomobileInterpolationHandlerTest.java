package io.github.foundationgames.automobility.entity;

import net.minecraft.core.PositionAndRotation;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class AutomobileInterpolationHandlerTest {
    @Test
    void queuesAndAdvancesOnlyAtTheAutomobileTrackingPhase() {
        var host = new Host();
        assertTrue(host.queue(new Vec3(8, 4, -4), 40, 8));
        host.handler.interpolate();
        assertEquals(Vec3.ZERO, host.pose.position(), "vanilla commonTick must not move the car");
        assertEquals(0, host.applied);
        host.handler.tick();
        assertEquals(new Vec3(2, 1, -1), host.pose.position());
        assertEquals(10, host.pose.yRot());
        assertEquals(2, host.pose.xRot());
        assertEquals(1, host.applied);
        for (int tick = 0; tick < 3; tick++) host.handler.tick();
        assertEquals(new Vec3(8, 4, -4), host.pose.position());
        assertFalse(host.handler.hasActiveInterpolation());
        assertNull(host.handler.target());
        host.handler.tick();
        assertEquals(4, host.applied);
    }

    @Test
    void rotationOnlyRetainsTheQueuedPositionInsteadOfTheVisualPosition() {
        var host = new Host();
        host.queue(new Vec3(12, 3, 8), 50, 12);
        host.handler.tick();
        host.handler.interpolateTo(null, 90, -12, true);
        assertEquals(new Vec3(12, 3, 8), host.handler.target().position());
        assertEquals(90, host.handler.target().yRot());
        assertEquals(-12, host.handler.target().xRot());
    }

    @Test
    void positionOnlyRetainsTheQueuedRotationInsteadOfZeroOrVisualRotation() {
        var host = new Host();
        host.queue(new Vec3(12, 3, 8), 50, 12);
        host.handler.tick();
        host.handler.interpolateTo(PositionPath.of(new Vec3(16, 4, 9)), 0, 0, false);
        assertEquals(new Vec3(16, 4, 9), host.handler.target().position());
        assertEquals(50, host.handler.target().yRot());
        assertEquals(12, host.handler.target().xRot());
    }

    @Test
    void missingComponentsUseTheCurrentPoseWhenNoTargetExists() {
        var host = new Host();
        host.pose = PositionAndRotation.of(new Vec3(4, 5, 6), 25, 7);
        host.handler.interpolateTo(null, 50, 9, true);
        assertEquals(new Vec3(4, 5, 6), host.handler.target().position());
        host.handler.cancel();
        host.handler.interpolateTo(PositionPath.of(new Vec3(10, 11, 12)), 0, 0, false);
        assertEquals(25, host.handler.target().yRot());
        assertEquals(7, host.handler.target().xRot());
    }

    @Test
    void yawCrossesTheWrapBoundaryByTheShortestRoute() {
        var host = new Host();
        host.pose = PositionAndRotation.of(Vec3.ZERO, 179, 0);
        host.queue(Vec3.ZERO, -179, 0);
        host.handler.tick();
        assertEquals(179.5f, host.pose.yRot());
        for (int tick = 0; tick < 3; tick++) host.handler.tick();
        assertEquals(181, host.pose.yRot());
    }

    @Test
    void repeatedIdenticalEndpointDoesNotRestartItsProgress() {
        var host = new Host();
        host.queue(new Vec3(8, 0, 0), 20, 0);
        host.handler.tick();
        host.queue(new Vec3(8, 0, 0), 20, 0);
        for (int tick = 0; tick < 3; tick++) host.handler.tick();
        assertEquals(new Vec3(8, 0, 0), host.pose.position());
        assertFalse(host.handler.hasActiveInterpolation());
    }

    @Test
    void hardPacketLeavesRelativeTeleportBaseAvailableUntilVanillaHasResolvedIt() {
        var host = new Host();
        host.queue(new Vec3(12, 4, 8), 60, 10);
        host.handler.tick();
        host.hardPacket = true;
        PositionAndRotation base = host.handler.target();
        assertNotNull(base, "beginning a hard scope must not cancel the relative teleport base");
        var resolved = PositionMoveRotation.calculateAbsolute(
                new PositionMoveRotation(base.position(), Vec3.ZERO, base.yRot(), base.xRot()),
                new PositionMoveRotation(new Vec3(3, 6, -2), Vec3.ZERO, 15, -4),
                Set.of(Relative.X, Relative.Y, Relative.Z, Relative.Y_ROT, Relative.X_ROT));
        assertEquals(new Vec3(15, 10, 6), resolved.position());
        assertEquals(75, resolved.yRot());
        assertFalse(host.queue(resolved.position(), resolved.yRot(), resolved.xRot()),
                "vanilla must immediately apply the resolved hard correction");
        host.pose = PositionAndRotation.of(resolved.position(), resolved.yRot(), resolved.xRot());
        host.hardPacket = false;
        host.handler.tick();
        assertEquals(new Vec3(15, 10, 6), host.pose.position());
        assertFalse(host.handler.hasActiveInterpolation());
    }

    @Test
    void samePositionHardYawCorrectionCancelsAnOlderMovementTarget() {
        var host = new Host();
        host.queue(new Vec3(20, 0, 0), 60, 0);
        host.hardPacket = true;
        assertFalse(host.queue(Vec3.ZERO, 90, 0));
        host.pose = PositionAndRotation.of(Vec3.ZERO, 90, 0);
        host.hardPacket = false;
        host.handler.tick();
        assertEquals(Vec3.ZERO, host.pose.position());
        assertEquals(90, host.pose.yRot());
        assertNull(host.handler.target());
    }

    @Test
    void cancellationAlsoWorksWhenVanillaBypassesTheHandler() {
        var host = new Host();
        host.queue(new Vec3(10, 0, 0), 30, 0);
        host.hardPacket = true;
        host.pose = PositionAndRotation.of(new Vec3(1000, 0, 0), 90, 0);
        // Automobile.clientEndPositionUpdate covers snapTo's non-ticking / >64-block branch.
        host.handler.cancel();
        host.hardPacket = false;
        host.handler.tick();
        assertEquals(new Vec3(1000, 0, 0), host.pose.position());
        assertNull(host.handler.target());
    }

    @Test
    void takeoverClearsTheRemoteEndpointBeforeTheFirstLocalTick() {
        var host = new Host();
        host.queue(new Vec3(10, 0, 0), 30, 0);
        host.local = true;
        assertNull(host.handler.target(), "getClientPositionAndRotation must see the local pose");
        assertFalse(host.handler.hasActiveInterpolation());
        host.handler.tick();
        assertEquals(Vec3.ZERO, host.pose.position());
        assertFalse(host.queue(new Vec3(20, 0, 0), 90, 0),
                "the explicit teleport path remains available to a local driver");
    }

    @Test
    void laterRemoteAuthorityStartsFromTheNewLocalPose() {
        var host = new Host();
        host.queue(new Vec3(100, 0, 0), 30, 0);
        host.local = true;
        host.handler.tick();
        host.pose = PositionAndRotation.of(new Vec3(20, 0, 0), 45, 0);
        host.local = false;
        host.queue(new Vec3(24, 0, 0), 65, 0);
        host.handler.tick();
        assertEquals(new Vec3(21, 0, 0), host.pose.position());
        assertEquals(50, host.pose.yRot());
    }

    @Test
    void predictedExternalMovementTranslatesOnlyThePendingEndpoint() {
        var host = new Host();
        host.queue(new Vec3(12, 4, 8), 60, 10);
        PositionAndRotation earlierSnapshot = host.handler.target();
        host.handler.applyPredictedMovement(new Vec3(2, 1, -3));
        assertEquals(new Vec3(14, 5, 5), host.handler.target().position());
        assertEquals(new Vec3(12, 4, 8), earlierSnapshot.position(), "target snapshots are immutable");
        assertEquals(Vec3.ZERO, host.pose.position());
        host.handler.cancel();
        host.handler.applyPredictedMovement(new Vec3(50, 0, 0));
        assertNull(host.handler.target());
    }

    @Test
    void nonfinitePredictedMovementCannotPoisonTheQueuedEndpoint() {
        var host = new Host();
        host.queue(new Vec3(12, 4, 8), 60, 10);
        host.handler.applyPredictedMovement(new Vec3(Double.NaN, 0, 0));
        host.handler.applyPredictedMovement(new Vec3(0, Double.POSITIVE_INFINITY, 0));
        host.handler.applyPredictedMovement(new Vec3(0, 0, Double.NEGATIVE_INFINITY));
        assertEquals(new Vec3(12, 4, 8), host.handler.target().position());
        host.handler.tick();
        assertEquals(new Vec3(3, 1, 2), host.pose.position());
    }

    private static final class Host {
        private PositionAndRotation pose = PositionAndRotation.of(Vec3.ZERO, 0, 0);
        private boolean local;
        private boolean hardPacket;
        private int applied;
        private final AutomobileInterpolationHandler handler = new AutomobileInterpolationHandler(
                () -> pose, () -> local, () -> hardPacket, next -> { pose = next; applied++; }, 4);

        private boolean queue(Vec3 position, float yaw, float pitch) {
            return handler.interpolateTo(PositionPath.of(position), yaw, pitch, true);
        }
    }
}
