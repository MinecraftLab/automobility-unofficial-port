package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.entity.GarageTestDrivePolicy;
import io.github.foundationgames.automobility.entity.GarageTestDriveService;
import io.github.foundationgames.automobility.screen.VehicleSettingsScreen;
import io.github.foundationgames.automobility.util.network.GarageTestDrivePackets;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.function.Predicate;

/** A server-confirmed, connection/level-bound session; no client teleport or menu open is performed. */
public final class GarageTestDriveClient {
    private static final long REPLY_TIMEOUT = TimeUnit.SECONDS.toNanos(8), FEEDBACK_TIME = TimeUnit.SECONDS.toNanos(5);
    private static KeyMapping returnKey;
    private static Predicate<GarageTestDrivePackets.Return> sender = request -> false;
    private static GarageTestDrivePackets.State session;
    private static Object connection, level, player;
    private static boolean returning;
    private static long requestedAt, feedbackUntil;
    private static Component feedback;
    private static UUID retiredNonce;
    private static Object retiredConnection;
    private static long retiredUntil;

    private GarageTestDriveClient() {}

    public record ReturnAvailability(boolean enabled, Component reason) {}

    public static void initialize(KeyMapping mapping, Predicate<GarageTestDrivePackets.Return> send) {
        returnKey = mapping;
        sender = send;
        clear();
    }

    /** Called on the client thread after the network receiver checks the originating connection. */
    public static void receive(GarageTestDrivePackets.State state) {
        var client = Minecraft.getInstance();
        expire(client);
        if (client.getConnection() == null || client.level == null || client.player == null || !client.player.isAlive()) return;
        if (!state.active()) {
            boolean activeMatch = session != null && session.nonce().equals(state.nonce());
            boolean retiredMatch = session == null && retiredConnection == client.getConnection()
                    && state.nonce().equals(retiredNonce) && System.nanoTime() - retiredUntil < 0;
            if (activeMatch || retiredMatch) {
                clear();
                if (state.reason() != GarageTestDriveService.Reason.RETURNED)
                    client.player.sendOverlayMessage(Component.translatable(state.reason().key()));
            }
            return;
        }
        if (!state.dimension().equals(client.level.dimension().identifier())) return;
        // A denial for an older nonce cannot replace a newer confirmed drive.
        if (session != null && !session.nonce().equals(state.nonce())
                && state.reason() != GarageTestDriveService.Reason.STARTED) return;
        session = state;
        connection = client.getConnection(); level = client.level; player = client.player;
        retiredNonce = null; retiredConnection = null; retiredUntil = 0;
        returning = false;
        if (state.reason() != GarageTestDriveService.Reason.STARTED && state.reason() != GarageTestDriveService.Reason.READY) {
            feedback = Component.translatable(state.reason().key());
            feedbackUntil = System.nanoTime() + FEEDBACK_TIME;
        } else { feedback = null; feedbackUntil = 0; }
    }

    public static void tick(Minecraft client) {
        expire(client);
        boolean pressed = false;
        if (returnKey != null) while (returnKey.consumeClick()) pressed = true;
        if (pressed && client.gui.screen() == null && client.player != null
                && client.player.getVehicle() instanceof AutomobileEntity auto && matchesVehicle(auto)
                && VehicleClientControls.isDriver(client, auto)) requestReturn();
    }

    public static void disconnected(Object oldConnection) {
        if (connection == oldConnection || retiredConnection == oldConnection) clear();
    }

    private static void expire(Minecraft client) {
        if (session != null && (client.getConnection() != connection || client.level != level || client.player != player
                || client.player == null || !client.player.isAlive() || client.player.isRemoved()
                || !session.dimension().equals(client.level.dimension().identifier()))) {
            var previous = session;
            Object previousConnection = connection;
            clear();
            // A same-connection terminal ACK can still explain a recent dimension/death invalidation.
            if (client.getConnection() == previousConnection) {
                retiredNonce = previous.nonce(); retiredConnection = previousConnection;
                retiredUntil = System.nanoTime() + FEEDBACK_TIME;
            }
            return;
        }
        long now = System.nanoTime();
        if (returning && now - requestedAt >= REPLY_TIMEOUT) {
            returning = false;
            feedback = text("return_retry"); feedbackUntil = now + FEEDBACK_TIME;
        }
        if (feedback != null && now - feedbackUntil >= 0) feedback = null;
        if (retiredNonce != null && now - retiredUntil >= 0) {
            retiredNonce = null; retiredConnection = null; retiredUntil = 0;
        }
    }

    private static void clear() {
        session = null; connection = level = player = null; returning = false;
        requestedAt = feedbackUntil = 0; feedback = null;
        retiredNonce = null; retiredConnection = null; retiredUntil = 0;
    }

    public static @Nullable GarageTestDrivePackets.State session() {
        expire(Minecraft.getInstance()); return session;
    }
    public static boolean returning() { expire(Minecraft.getInstance()); return returning; }
    public static @Nullable Component feedback() { expire(Minecraft.getInstance()); return feedback; }
    public static KeyMapping returnKey() { return returnKey; }
    public static Component binding() { return VehicleClientControls.binding(returnKey); }

    public static boolean matchesVehicle(@Nullable AutomobileEntity auto) {
        var client = Minecraft.getInstance(); expire(client);
        return session != null && auto != null && auto.level() == client.level && auto.isAlive() && !auto.isRemoved()
                && session.vehicle().equals(auto.getUUID()) && client.level.getEntity(auto.getId()) == auto;
    }

    public static ReturnAvailability availability(@Nullable AutomobileEntity auto) {
        var client = Minecraft.getInstance();
        if (!matchesVehicle(auto)) return denied(GarageTestDriveService.Reason.NO_SESSION);
        if (!VehicleClientControls.isDriver(client, auto)) return denied(GarageTestDriveService.Reason.DRIVER_REQUIRED);
        if (auto.getPassengers().size() != 1) return denied(GarageTestDriveService.Reason.OCCUPIED);
        if (!auto.automobileOnGround()) return denied(GarageTestDriveService.Reason.NOT_GROUNDED);
        if (!GarageTestDrivePolicy.stopped(auto.getHSpeed(), auto.getVSpeed(), auto.getDeltaMovement().lengthSqr(), true))
            return new ReturnAvailability(false, text("stop_first"));
        if (returning) return new ReturnAvailability(false, text("return_waiting"));
        return new ReturnAvailability(true, feedback == null ? text("return_help") : feedback);
    }

    /** Shared by the real R control, settings button and native QA; never bypasses server validation. */
    public static boolean requestReturn() {
        var client = Minecraft.getInstance();
        if (client.gui.screen() != null && !(client.gui.screen() instanceof VehicleSettingsScreen)) return false;
        AutomobileEntity auto = client.player != null && client.player.getVehicle() instanceof AutomobileEntity car ? car : null;
        var availability = availability(auto);
        if (!availability.enabled()) {
            if (matchesVehicle(auto)) {
                feedback = availability.reason();
                feedbackUntil = System.nanoTime() + FEEDBACK_TIME;
                if (client.player != null) client.player.sendOverlayMessage(feedback);
            }
            return false;
        }
        var request = new GarageTestDrivePackets.Return(session.nonce());
        returning = true; requestedAt = System.nanoTime(); feedback = null;
        if (!sender.test(request)) {
            returning = false; feedback = text("return_retry"); feedbackUntil = System.nanoTime() + FEEDBACK_TIME;
            return false;
        }
        return true;
    }

    private static ReturnAvailability denied(GarageTestDriveService.Reason reason) {
        return new ReturnAvailability(false, Component.translatable(reason.key()));
    }
    private static Component text(String key) { return Component.translatable("garage.automobility.test_drive." + key); }
}
