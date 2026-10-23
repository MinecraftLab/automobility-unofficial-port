package io.github.foundationgames.automobility.fabric.network.drivetrain;

import io.github.foundationgames.automobility.automobile.drivetrain.*;
import io.github.foundationgames.automobility.client.vehicle.VehicleDrivetrainSettingsClient;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.fabric.mixin.ClientContentConnectionAccess;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;

/** Accepted server preference cache, bound to actual connection/world/car/request. No optimistic storage writes. */
public final class FabricDrivetrainSettingsClient implements VehicleDrivetrainSettingsClient.Adapter {
    private static final class Entry {
        final AutomobileEntity car; final Object world;
        UUID request; long requestTick = -100, replyTick = -100; boolean pending;
        DrivetrainSettingsMessages.Message value;
        Entry(AutomobileEntity car) { this.car = car; world = car.level(); }
    }
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private Connection connection;
    private static boolean initialized;
    private FabricDrivetrainSettingsClient() { }
    public static void initialize() {
        if (initialized) throw new IllegalStateException("settings client already initialized"); initialized = true;
        var adapter = new FabricDrivetrainSettingsClient(); VehicleDrivetrainSettingsClient.install(adapter);
        VehicleDrivetrainPreferences.installClientResolver(car -> adapter.view(car).flatMap(DrivetrainSettingsMessages.Message::settings));
        ClientPlayNetworking.registerGlobalReceiver(DrivetrainSettingsPayload.TYPE, (payload, context) -> {
            var client = context.client(); var handler = client.getConnection(); if (handler == null) return;
            var actual = ((ClientContentConnectionAccess)(Object)handler).automobility$getContentConnection();
            client.execute(() -> {
                if (client.getConnection() != handler || !actual.isConnected() || actual.getPacketListener() != handler) return;
                if (adapter.connection != actual) { adapter.entries.clear(); adapter.connection = actual; }
                try {
                    var response = DrivetrainSettingsMessages.decode(payload.frame());
                    if (response.kind() != DrivetrainSettingsMessages.Kind.REPLY || client.level == null) return;
                    var entry = adapter.entries.get(response.vehicle());
                    if (entry == null || entry.world != client.level || entry.car.isRemoved() || client.level.getEntity(response.entityId()) != entry.car
                            || !response.request().equals(entry.request)) return;
                    entry.value = response; entry.replyTick = client.level.getGameTime(); entry.pending = false;
                } catch (IllegalArgumentException malformed) { /* No local preference fallback from unrecognized data. */ }
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            var ended = ((ClientContentConnectionAccess)(Object)handler).automobility$getContentConnection();
            client.execute(() -> { if (adapter.connection == ended) { adapter.entries.clear(); adapter.connection = null; } });
        });
        ClientTickEvents.END_CLIENT_TICK.register(adapter::tick);
    }
    @Override public boolean available(AutomobileEntity car) {
        var client = Minecraft.getInstance();
        if (car == null || car.isRemoved()) return false;
        String engine = car.garageComponents().engine().unwrapKey().map(key -> key.identifier().toString()).orElse("");
        boolean physicalProfile = PhysicalProfileCatalog.engines().containsKey(engine)
                || io.github.foundationgames.automobility.api.drivetrain.VehicleDrivetrainProfiles.electric(engine).isPresent();
        return physicalProfile && client.level == car.level() && client.player != null && client.player.isAlive()
                && client.player.distanceToSqr(car) <= 64 && client.getConnection() != null && ClientPlayNetworking.canSend(DrivetrainSettingsPayload.TYPE);
    }
    private Entry entry(AutomobileEntity car) {
        var entry = entries.get(car.getUUID());
        if (entry == null || entry.car != car || entry.world != car.level()) {
            entry = new Entry(car); entries.put(car.getUUID(), entry);
            while (entries.size() > 16) entries.remove(entries.keySet().iterator().next());
        }
        return entry;
    }
    @Override public void query(AutomobileEntity car) {
        if (!available(car)) return;
        var handler = Minecraft.getInstance().getConnection();
        var actual = ((ClientContentConnectionAccess)(Object)handler).automobility$getContentConnection();
        if (connection != actual) { entries.clear(); connection = actual; }
        var entry = entry(car); long tick = car.level().getGameTime();
        if (entry.pending && tick >= entry.requestTick && tick - entry.requestTick <= 100 || tick >= entry.requestTick && tick - entry.requestTick < 5) return;
        entry.request = UUID.randomUUID(); entry.requestTick = tick; entry.pending = true;
        ClientPlayNetworking.send(new DrivetrainSettingsPayload(DrivetrainSettingsMessages.encode(
                DrivetrainSettingsMessages.query(entry.request, car.getUUID(), car.getId()))));
    }
    @Override public Optional<DrivetrainSettingsMessages.Message> view(AutomobileEntity car) {
        if (!available(car)) return Optional.empty();
        var entry = entries.get(car.getUUID()); long tick = car.level().getGameTime();
        return entry == null || entry.car != car || entry.world != car.level() || tick < entry.replyTick || tick - entry.replyTick > 60
                ? Optional.empty() : Optional.ofNullable(entry.value);
    }
    @Override public boolean save(AutomobileEntity car, DrivetrainSettingsEnvelope.Settings requested, boolean reset) {
        var view = view(car); if (view.isEmpty() || !view.get().mayConfigure() || pending(car)) return false;
        var entry = entry(car); entry.request = UUID.randomUUID(); entry.requestTick = car.level().getGameTime(); entry.pending = true;
        var update = new DrivetrainSettingsMessages.Message(DrivetrainSettingsMessages.Kind.UPDATE, entry.request, car.getUUID(), car.getId(), view.get().token(),
                DrivetrainSettingsMessages.Status.CURRENT, reset, false, Optional.of(requested));
        ClientPlayNetworking.send(new DrivetrainSettingsPayload(DrivetrainSettingsMessages.encode(update))); return true;
    }
    @Override public boolean pending(AutomobileEntity car) {
        var entry = entries.get(car.getUUID()); if (entry == null || entry.car != car || entry.world != car.level()) return false;
        long tick = car.level().getGameTime(); return entry.pending && tick >= entry.requestTick && tick - entry.requestTick <= 100;
    }
    private void tick(Minecraft client) {
        if (client.getConnection() == null || connection != null && (!connection.isConnected() || connection.getPacketListener() != client.getConnection())) {
            entries.clear(); connection = null;
        }
        // Bind before sending the first query; a first response must not clear its own pending entry.
        if (connection == null && client.getConnection() != null) connection = ((ClientContentConnectionAccess)(Object)client.getConnection()).automobility$getContentConnection();
        entries.entrySet().removeIf(value -> value.getValue().world != client.level || client.level == null || value.getValue().car.isRemoved()
                || client.level.getEntity(value.getValue().car.getId()) != value.getValue().car);
        if (client.player != null && client.player.getVehicle() instanceof AutomobileEntity car && available(car)) {
            var entry = entry(car); long tick = car.level().getGameTime();
            if (tick < entry.requestTick || tick - entry.requestTick >= 20) query(car);
        }
    }
}
