package io.github.foundationgames.automobility.qa;

import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import io.github.foundationgames.automobility.automobile.AutomobileWheel;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Set;

/** Opt-in client-object/packet-handler regression, never a second physical player. */
final class RemoteInterpolationQa {
    private static final Logger LOGGER = LoggerFactory.getLogger("RemoteInterpolationQa");
    private static boolean attempted;
    private static int readyTicks;
    private static int checks;

    static void initialize() {
        if (!"1".equals(System.getenv("AUTOMOBILITY_QA_REMOTE_INTERPOLATION"))) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (attempted || client.level == null || client.player == null || client.getConnection() == null) return;
            if (++readyTicks < 30) return;
            attempted = true;
            try {
                var address = client.getConnection().getConnection().getRemoteAddress();
                require(address instanceof InetSocketAddress socket && socket.getAddress() != null
                        && socket.getAddress().isLoopbackAddress(), "loopback-only fixture");
                require(client.player.getGameProfile().name().equals("Auto263QA"), "isolated QA player");
                run(client);
                LOGGER.info("AUTO263_REMOTE_INTERPOLATION_PASS checks={} realPacketHandlers=true physicalTwoPlayerAcceptance=NOT_PROVEN", checks);
            } catch (Throwable failure) {
                LOGGER.error("AUTO263_REMOTE_INTERPOLATION_FAILED checks={}", checks, failure);
            }
        });
    }

    private static void run(Minecraft client) {
        var level = client.level;
        var listener = client.getConnection();
        // A temporary client-only entity, above the loaded QA chunk, removed within this callback.
        // No server messages, actual riders, existing entities, inventory, blocks or inputs change.
        var auto = new AuthorityProbe(level, client.player);
        int id = -1_900_000_263;
        require(level.getEntity(id) == null, "reserved fixture ID unused");
        auto.setId(id);
        auto.setComponents(level.registryAccess().lookupOrThrow(AutomobileFrame.REGISTRY).getOrThrow(AutomobileFrame.STANDARD_RED),
                level.registryAccess().lookupOrThrow(AutomobileWheel.REGISTRY).getOrThrow(AutomobileWheel.STANDARD),
                level.registryAccess().lookupOrThrow(AutomobileEngine.REGISTRY).getOrThrow(AutomobileEngine.IRON));
        var origin = new Vec3(Math.floor(client.player.getX()) + 0.25, 280,
                Math.floor(client.player.getZ()) + 0.25);
        reset(auto, origin, 0);
        level.addEntity(auto);
        try {
            require(level.getEntity(id) == auto && level.isTickingEntity(auto), "real ticking client entity registered");
            int steps = auto.getType().updateInterval() + 1;

            auto.moveOrInterpolateTo(PositionPath.of(origin.add(6, 0, 0)), 90, 0);
            close(origin, auto.position(), "actual 26.3 entrypoint queues instead of snapping");
            require(auto.getInterpolation().hasActiveInterpolation(), "real handler is active");
            auto.commonTick();
            close(origin, auto.position(), "commonTick does not double-step interpolation");
            auto.tick();
            close(origin.add(6.0 / steps, 0, 0), auto.position(), "normal automobile tick advances exactly once");
            close(90.0 / steps, auto.getYRot(), "normal automobile tick interpolates yaw");

            reset(auto, origin, 0);
            relative(listener, auto, origin.add(6, 0, 0));
            close(origin, auto.position(), "relative packet queues remote position");
            close(origin.add(6, 0, 0), auto.getPositionCodec().getBase(), "packet advances codec to endpoint");
            listener.handleMoveEntity(new ClientboundMoveEntityPacket.Rot(id, (byte)64, (byte)0, true));
            close(origin.add(6, 0, 0), auto.getInterpolation().target().position(), "rotation-only preserves queued position");
            close(90, auto.getInterpolation().target().yRot(), "rotation-only targets ninety degrees");
            relative(listener, auto, origin.add(7, 0, 0));
            close(90, auto.getInterpolation().target().yRot(), "position-only preserves queued yaw");
            auto.positionTrackingTick();
            close(origin.add(7, 0, 0), auto.getPositionCodec().getBase(), "visual step never rewrites packet codec");
            settle(auto, steps);
            close(origin.add(7, 0, 0), auto.position(), "mixed packet sequence reaches endpoint");

            reset(auto, origin, 179);
            auto.moveOrInterpolateTo(-179, 0);
            auto.positionTrackingTick();
            close(179 + 2.0 / steps, auto.getYRot(), "yaw wrap takes short path");

            reset(auto, origin, 0);
            listener.handleEntityPositionSync(new ClientboundEntityPositionSyncPacket(id,
                    PositionPath.of(origin.add(0, 17, 0)), 45, 0, true));
            close(origin.add(0, 17, 0), auto.position(), "hard +17Y sync snaps immediately");
            close(origin.y + 17, auto.getDisplacement().getVertical(1), "hard sync rebases visual height");
            require(!auto.getInterpolation().hasActiveInterpolation(), "hard sync leaves no stale target");
            relative(listener, auto, origin.add(0, 23, 0));
            close(origin.add(0, 17, 0), auto.position(), "following +6Y stays interpolated");
            settle(auto, steps);
            close(origin.add(0, 23, 0), auto.position(), "following +6Y reaches correct codec-relative endpoint");

            reset(auto, origin, 0);
            auto.moveOrInterpolateTo(PositionPath.of(origin.add(6, 0, 0)), 60, 0);
            auto.positionTrackingTick();
            Vec3 current = auto.position();
            teleport(listener, auto, current, 12, Set.of());
            require(!auto.getInterpolation().hasActiveInterpolation(), "same-position teleport cancels old movement");
            settle(auto, steps);
            close(current, auto.position(), "same-position teleport cannot drift to stale target");
            close(12, auto.getYRot(), "same-position teleport retains new yaw");

            reset(auto, origin, 0);
            auto.moveOrInterpolateTo(PositionPath.of(origin.add(6, 0, 0)), 60, 0);
            auto.positionTrackingTick();
            teleport(listener, auto, new Vec3(3, origin.y, origin.z), 0, Set.of(Relative.X));
            close(origin.add(9, 0, 0), auto.position(), "relative teleport is based on queued endpoint, not visual intermediate");

            reset(auto, origin, 0);
            auto.moveOrInterpolateTo(PositionPath.of(origin.add(6, 0, 0)), 90, 0);
            auto.localAuthority = true;
            require(auto.isLocalInstanceAuthoritative(), "real local-authority path selected without mounting player");
            require(auto.getInterpolation().target() == null, "takeover cannot expose old remote endpoint before next tick");
            auto.positionTrackingTick();
            require(!auto.getInterpolation().hasActiveInterpolation(), "taking driver authority cancels queued remote move");
            relative(listener, auto, origin.add(2, 0, 0));
            close(origin, auto.position(), "local driver ignores routine relative packet");
            teleport(listener, auto, origin.add(0, -23, 0), 15, Set.of());
            close(origin.add(0, -23, 0), auto.position(), "local-authority -23Y teleport still applies");
            close(origin.y - 23, auto.getDisplacement().getVertical(1), "local-authority teleport keeps body attached");
            // Vanilla's teleport packet does not update the relative codec. Establish the new
            // base with a real position-sync packet before testing another relative update.
            listener.handleEntityPositionSync(new ClientboundEntityPositionSyncPacket(id,
                    PositionPath.of(origin.add(0, -23, 0)), 15, 0, true));
            auto.localAuthority = false;
            relative(listener, auto, origin.add(1, -23, 0));
            require(auto.getInterpolation().hasActiveInterpolation(), "remote interpolation resumes after relinquishing authority");

            reset(auto, origin, 0);
            auto.moveOrInterpolateTo(PositionPath.of(origin.add(70, 0, 0)), 90, 0);
            auto.syncPacketPositionCodec(origin.x + 69, origin.y, origin.z);
            listener.handleEntityPositionSync(new ClientboundEntityPositionSyncPacket(id,
                    PositionPath.of(origin.add(70, 0, 0)), 30, 0, true));
            close(origin.add(70, 0, 0), auto.position(), "vanilla far-distance snap applies despite small codec delta");
            require(!auto.getInterpolation().hasActiveInterpolation(), "far-distance snap cancels old interpolation");
            settle(auto, steps);
            close(origin.add(70, 0, 0), auto.position(), "far-distance snap has no delayed pullback");

            var unloaded = origin.add(10_000, 0, 10_000);
            reset(auto, unloaded, 0);
            require(!level.isTickingEntity(auto), "fixture outside loaded chunks is not ticking");
            auto.moveOrInterpolateTo(PositionPath.of(unloaded.add(6, 0, 0)), 90, 0);
            listener.handleEntityPositionSync(new ClientboundEntityPositionSyncPacket(id,
                    PositionPath.of(unloaded.add(1, 0, 0)), 20, 0, true));
            close(unloaded.add(1, 0, 0), auto.position(), "non-ticking entity follows vanilla immediate snap");
            require(!auto.getInterpolation().hasActiveInterpolation(), "non-ticking snap cancels old interpolation");
            settle(auto, steps);
            close(unloaded.add(1, 0, 0), auto.position(), "non-ticking snap has no delayed pullback");
        } finally {
            auto.localAuthority = false;
            level.removeEntity(id, Entity.RemovalReason.DISCARDED);
            require(level.getEntity(id) == null, "temporary fixture entity removed");
        }
    }

    private static void reset(AuthorityProbe auto, Vec3 position, float yaw) {
        auto.localAuthority = false;
        auto.getInterpolation().cancel();
        auto.setPos(position);
        auto.setYRot(yaw);
        auto.setXRot(0);
        auto.setOldPosAndRot();
        auto.syncPacketPositionCodec(position.x, position.y, position.z);
        auto.getDisplacement().snapTo(position.y, new Quaternionf());
    }

    private static void relative(ClientPacketListener listener, AutomobileEntity auto, Vec3 target) {
        var delta = auto.getPositionCodec().tryEncode(target);
        require(delta != null, "relative packet encodable");
        listener.handleMoveEntity(new ClientboundMoveEntityPacket.Pos(auto.getId(), delta, true));
    }

    private static void teleport(ClientPacketListener listener, AutomobileEntity auto, Vec3 target,
                                 float yaw, Set<Relative> relatives) {
        listener.handleTeleportEntity(new ClientboundTeleportEntityPacket(auto.getId(),
                new PositionMoveRotation(target, Vec3.ZERO, yaw, 0), relatives, true));
    }

    private static void settle(AutomobileEntity auto, int steps) {
        for (int i = 0; i <= steps; i++) auto.positionTrackingTick();
    }

    private static void close(Vec3 expected, Vec3 actual, String label) {
        require(expected.distanceToSqr(actual) < 1.0E-8, label + " expected=" + expected + " actual=" + actual);
    }

    private static void close(double expected, double actual, String label) {
        require(Math.abs(expected - actual) < 1.0E-4, label + " expected=" + expected + " actual=" + actual);
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new IllegalStateException(label);
        checks++;
    }

    private static final class AuthorityProbe extends AutomobileEntity {
        private final LivingEntity localPlayer;
        boolean localAuthority;

        AuthorityProbe(ClientLevel level, LivingEntity localPlayer) {
            super(level);
            this.localPlayer = localPlayer;
        }

        @Override
        public LivingEntity getControllingPassenger() {
            return localAuthority ? localPlayer : super.getControllingPassenger();
        }
    }
}
