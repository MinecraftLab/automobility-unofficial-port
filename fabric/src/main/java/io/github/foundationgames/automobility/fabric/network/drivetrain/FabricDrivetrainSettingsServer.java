package io.github.foundationgames.automobility.fabric.network.drivetrain;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.api.drivetrain.VehicleMechanicalAccess;
import io.github.foundationgames.automobility.automobile.drivetrain.*;
import io.github.foundationgames.automobility.automobile.garage.GarageTransferAccess;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/** One owner-authorized, stationary, preimage-checked settings action; no client-authored runtime or resources. */
public final class FabricDrivetrainSettingsServer {
    private record Lease(ServerPlayer actor, Object connection, AutomobileEntity car, Object world, long issuedTick, long token, Tag before) { }
    private static final Map<UUID, Lease> leases = new HashMap<>();
    private static final Map<UUID, Long> lastQuery = new HashMap<>();
    private static boolean initialized;
    private FabricDrivetrainSettingsServer() { }
    public static void initialize() {
        if (initialized) throw new IllegalStateException("settings server already initialized"); initialized = true;
        VehicleDrivetrainPreferences.installServerPolicy(VehicleDrivetrainServerPolicyStore::current);
        PayloadTypeRegistry.serverboundPlay().register(DrivetrainSettingsPayload.TYPE, DrivetrainSettingsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(DrivetrainSettingsPayload.TYPE, DrivetrainSettingsPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(DrivetrainSettingsPayload.TYPE, (payload, context) -> context.server().execute(() -> {
            var actor = context.player();
            if (context.server().getPlayerList().getPlayer(actor.getUUID()) != actor || !actor.isAlive()) return;
            try { receive(actor, DrivetrainSettingsMessages.decode(payload.frame())); }
            catch (IllegalArgumentException invalid) { /* Wrong direction, malformed and unknown settings do not mutate NBT. */ }
        }));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            leases.clear(); lastQuery.clear();
            try { VehicleDrivetrainServerPolicyStore.load(FabricLoader.getInstance().getConfigDir().resolve("automobility-drivetrain-server.json")); }
            catch (java.io.IOException invalid) { Automobility.LOG.warn("New drivetrain suspended: server policy could not be read", invalid); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { leases.clear(); lastQuery.clear(); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { leases.remove(handler.player.getUUID()); lastQuery.remove(handler.player.getUUID()); });
    }
    private static void receive(ServerPlayer actor, DrivetrainSettingsMessages.Message action) {
        if (action.kind() == DrivetrainSettingsMessages.Kind.REPLY) return;
        if (!(actor.level().getEntity(action.entityId()) instanceof AutomobileEntity car) || !car.getUUID().equals(action.vehicle())
                || car.isRemoved() || car.level() != actor.level() || actor.distanceToSqr(car) > 64) return;
        long tick = car.level().getGameTime();
        if (action.kind() == DrivetrainSettingsMessages.Kind.QUERY) {
            Long last = lastQuery.get(actor.getUUID()); if (last != null && tick >= last && tick - last < 5) return;
            lastQuery.put(actor.getUUID(), tick); reply(actor, car, action, DrivetrainSettingsMessages.Status.CURRENT); return;
        }
        Lease lease = leases.remove(actor.getUUID());
        var carrier = (CoreDrivetrainSettingsAccess)car;
        if (lease == null || lease.actor() != actor || lease.connection() != actor.connection || lease.car() != car || lease.world() != car.level()
                || lease.token() != action.token() || tick < lease.issuedTick() || tick - lease.issuedTick() > 100
                || !Objects.equals(lease.before(), carrier.drivetrain$rawSettings())) {
            reply(actor, car, action, DrivetrainSettingsMessages.Status.STALE); return;
        }
        if (!car.getVehicleAccess().mayConfigure(actor.getUUID()) || GarageTransferAccess.blocked(car)) {
            reply(actor, car, action, DrivetrainSettingsMessages.Status.FORBIDDEN); return;
        }
        if (!stationary(car)) { reply(actor, car, action, DrivetrainSettingsMessages.Status.MOVING); return; }
        var policy = VehicleDrivetrainPreferences.serverPolicy();
        if (policy.isEmpty()) { reply(actor, car, action, DrivetrainSettingsMessages.Status.POLICY_UNAVAILABLE); return; }
        var envelope = carrier.drivetrain$settingsEnvelope();
        boolean unknown = envelope.isEmpty() || envelope.get().status() != DrivetrainSettingsEnvelope.Status.ABSENT && envelope.get().settings().isEmpty();
        if (unknown && !action.resetUnrecognized()) { reply(actor, car, action, DrivetrainSettingsMessages.Status.UNRECOGNIZED); return; }
        var requested = policy.get().apply(action.settings().orElseThrow());
        // A stationary driver or owner outside the car must not silently queue a control-mode change in D/R.
        boolean requiresNeutral = ((VehicleMechanicalAccess)(Object)car).drivetrainSnapshot()
                .map(snapshot -> snapshot.controlMode() != controlMode(requested) && snapshot.gear() != 0).orElse(false);
        if (requiresNeutral) { reply(actor, car, action, DrivetrainSettingsMessages.Status.NEUTRAL_REQUIRED); return; }
        boolean saved = CoreDrivetrainBridge.replaceSettingsFromAcceptedAction(actor, car, requested);
        reply(actor, car, action, saved ? DrivetrainSettingsMessages.Status.SAVED : DrivetrainSettingsMessages.Status.FORBIDDEN);
    }
    private static PowertrainSimulation.ControlMode controlMode(DrivetrainSettingsEnvelope.Settings settings) {
        return settings.transmissionMode() == DrivetrainSettingsEnvelope.TransmissionMode.AUTOMATIC ? PowertrainSimulation.ControlMode.AUTOMATIC
                : settings.assistanceStrength() > 0 ? PowertrainSimulation.ControlMode.MANUAL_ASSISTED : PowertrainSimulation.ControlMode.MANUAL_CLUTCH;
    }
    private static boolean stationary(AutomobileEntity car) {
        if (Math.abs(car.getHSpeed()) > 0.005 || car.getDeltaMovement().horizontalDistanceSqr() > 0.000025) return false;
        return !((Object)car instanceof VehicleMechanicalAccess access) || access.drivetrainSnapshot()
                .map(snapshot -> Math.abs(snapshot.drivenWheelOmega()) <= 0.75 && snapshot.phase() == AutomaticShiftController.Phase.DRIVE).orElse(true);
    }
    private static void reply(ServerPlayer actor, AutomobileEntity car, DrivetrainSettingsMessages.Message action, DrivetrainSettingsMessages.Status status) {
        if (!ServerPlayNetworking.canSend(actor, DrivetrainSettingsPayload.TYPE)) return;
        var policy = VehicleDrivetrainPreferences.serverPolicy();
        var settings = VehicleDrivetrainPreferences.resolve(car);
        boolean writable = car.getVehicleAccess().mayConfigure(actor.getUUID()) && !GarageTransferAccess.blocked(car) && stationary(car) && policy.isPresent();
        if (status == DrivetrainSettingsMessages.Status.CURRENT) {
            if (policy.isEmpty()) status = DrivetrainSettingsMessages.Status.POLICY_UNAVAILABLE;
            else if (settings.isEmpty()) status = DrivetrainSettingsMessages.Status.UNRECOGNIZED;
            else if (!car.getVehicleAccess().mayConfigure(actor.getUUID()) || GarageTransferAccess.blocked(car)) status = DrivetrainSettingsMessages.Status.FORBIDDEN;
            else if (!stationary(car)) status = DrivetrainSettingsMessages.Status.MOVING;
        }
        long token = 0;
        if (writable) {
            do { token = UUID.randomUUID().getLeastSignificantBits(); } while (token == 0);
            Tag before = ((CoreDrivetrainSettingsAccess)car).drivetrain$rawSettings();
            leases.put(actor.getUUID(), new Lease(actor, actor.connection, car, car.level(), car.level().getGameTime(), token, before));
        }
        var response = new DrivetrainSettingsMessages.Message(DrivetrainSettingsMessages.Kind.REPLY, action.request(), car.getUUID(), car.getId(), token,
                status, false, writable, settings);
        ServerPlayNetworking.send(actor, new DrivetrainSettingsPayload(DrivetrainSettingsMessages.encode(response)));
    }
}
