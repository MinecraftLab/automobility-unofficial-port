package io.github.foundationgames.automobility.fabric.network.drivetrain;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.drivetrain.*;
import io.github.foundationgames.automobility.automobile.drivetrain.metadata.*;
import io.github.foundationgames.automobility.automobile.drivetrain.profile.*;
import io.github.foundationgames.automobility.api.drivetrain.profile.VehicleProfileSync015Client;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.fabric.mixin.ClientContentConnectionAccess;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import org.lwjgl.glfw.GLFW;

/** Raw input and read-only observer state. Does not start/stop either legacy or new engine controller. */
public final class FabricDrivetrainClient implements ClientModInitializer, CoreDrivetrainBridge.ClientAdapter {
    public record RawControls(float throttle, float clutchDepression, ClutchController.PedalMode clutchMode, boolean startHeld) {
        public RawControls {
            if (!Float.isFinite(throttle) || throttle < 0 || throttle > 1 || !Float.isFinite(clutchDepression) || clutchDepression < 0 || clutchDepression > 1)
                throw new IllegalArgumentException("bounded raw pedals required");
            Objects.requireNonNull(clutchMode);
            if (clutchMode == ClutchController.PedalMode.DIGITAL && clutchDepression != 0 && clutchDepression != 1) throw new IllegalArgumentException("digital clutch travel");
        }
    }
    @FunctionalInterface public interface RawSampler { RawControls sample(Minecraft client, AutomobileEntity car); }
    private static RawSampler analogSampler;
    /** Optional controller adapter supplies actual raw travel, never a field temporarily masked by an energy owner. */
    public static void installRawSampler(RawSampler sampler) { analogSampler = Objects.requireNonNull(sampler); }
    private static final class Observation {
        final Object world;
        final AutomobileEntity car;
        CoreDrivetrainPayloads.Grant grant;
        DrivetrainProtocol.SnapshotGate gate;
        DrivetrainProtocol.Snapshot snapshot;
        final RuntimeMetadataPairing018 pairing = new RuntimeMetadataPairing018();
        CoreDrivetrainBridge.ClientInputSample pendingLocalInput;
        long sequence;
        Observation(Object world, AutomobileEntity car, CoreDrivetrainPayloads.Grant grant) { this.world = world; this.car = car; this.grant = grant; gate = DrivetrainProtocol.SnapshotGate.open(grant.identity()); }
    }
    private final Map<UUID, Observation> observations = new LinkedHashMap<>();
    private long connectionNonce;
    private Connection connection;
    private KeyMapping clutch, up, down, drive, neutral, reverse, park, starter, ignition;
    @Override public void onInitializeClient() {
        CoreDrivetrainBridge.installClient(this);
        FabricDrivetrainSettingsClient.initialize();
        new VehicleProfileSync015Client().onInitializeClient();
        ClientPlayNetworking.registerGlobalReceiver(MetadataPayload018.TYPE, (packet, context) -> {
            var client = context.client(); var handler = client.getConnection(); if (handler == null) return;
            var actual = ((ClientContentConnectionAccess)(Object)handler).automobility$getContentConnection();
            var receivedWorld = client.level;
            client.execute(() -> {
                if (handler != client.getConnection() || actual != connection || !actual.isConnected()
                        || actual.getPacketListener() != handler || receivedWorld != client.level) return;
                var metadata = RuntimeMetadata018.decode(packet.body()); var approval = metadata.approval();
                var observed = observations.get(approval.identity().vehicle());
                if (observed == null || observed.world != client.level || observed.car.isRemoved()
                        || client.level.getEntity(observed.grant.entityId()) != observed.car || !observed.grant.identity().equals(approval.identity())
                        ) return;
                observed.pairing.bind(connection, observed.world, observed.grant.identity());
                observed.pairing.metadata(connection, observed.world, metadata, client.level.getGameTime());
            });
        });
        var category = KeyMapping.Category.register(Automobility.rl("drivetrain"));
        clutch = binding("clutch", category); up = binding("shift_up", category); down = binding("shift_down", category);
        drive = binding("range_drive", category); neutral = binding("range_neutral", category); reverse = binding("range_reverse", category); park = binding("range_park", category);
        starter = binding("starter_hold", category); ignition = binding("ignition_request", category);
        ClientPlayNetworking.registerGlobalReceiver(CoreDrivetrainPayloads.TYPE, (packet, context) -> {
            var client = context.client(); var handler = client.getConnection(); if (handler == null) return;
            var actual = ((ClientContentConnectionAccess)(Object)handler).automobility$getContentConnection();
            client.execute(() -> {
                if (client.getConnection() != handler || !actual.isConnected() || actual.getPacketListener() != handler) return;
                if (connection != actual) { clear(); connection = actual; }
                receive(client, packet);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            var ended = ((ClientContentConnectionAccess)(Object)handler).automobility$getContentConnection();
            client.execute(() -> { if (connection == ended) clear(); });
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private static KeyMapping binding(String action, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.automobility.drivetrain." + action, InputConstants.Type.KEYSYM, switch(action) { case "clutch" -> GLFW.GLFW_KEY_LEFT_ALT; case "shift_up" -> GLFW.GLFW_KEY_X; case "shift_down" -> GLFW.GLFW_KEY_Z; case "starter_hold" -> GLFW.GLFW_KEY_G; default -> GLFW.GLFW_KEY_UNKNOWN; }, category));
    }
    /** Read the original controller directly, before any vehicle energy owner can mask forward input. */
    private RawControls defaultRawControls(Minecraft client) {
        var controller = io.github.foundationgames.automobility.platform.Platform.get().controller();
        boolean accelerate = controller.inControllerMode() ? controller.accelerating() : client.options.keyUp.isDown();
        return new RawControls(accelerate ? 1 : 0, clutch.isDown() ? 1 : 0,
                ClutchController.PedalMode.DIGITAL, starter.isDown());
    }
    private void clear() { observations.clear(); connectionNonce = 0; connection = null; drain(); }
    private void drain() {
        if (up == null) return;
        for (var key : new KeyMapping[]{clutch, up, down, drive, neutral, reverse, park, starter, ignition}) while (key.consumeClick()) { }
    }
    private void receive(Minecraft client, CoreDrivetrainPayloads.Packet packet) {
        try {
            if (packet.kind() == CoreDrivetrainPayloads.CHALLENGE) {
                var capability = CoreDrivetrainPayloads.readCapability(packet, CoreDrivetrainPayloads.CHALLENGE);
                if (!ClientPlayNetworking.canSend(CoreDrivetrainPayloads.TYPE)) return;
                if (connectionNonce != capability.nonce()) { observations.clear(); connectionNonce = capability.nonce(); }
                ClientPlayNetworking.send(CoreDrivetrainPayloads.capability(CoreDrivetrainPayloads.CAPABILITY, connectionNonce)); return;
            }
            if (client.level == null || connectionNonce == 0) return;
            if (packet.kind() == CoreDrivetrainPayloads.GRANT) {
                var grant = CoreDrivetrainPayloads.readGrant(packet);
                if (grant.connectionNonce() != connectionNonce || !grant.dimension().equals(client.level.dimension().identifier().toString())
                        || !(client.level.getEntity(grant.entityId()) instanceof AutomobileEntity car) || !car.getUUID().equals(grant.identity().vehicle())) return;
                var previous = observations.get(grant.identity().vehicle());
                if (previous == null || previous.world != client.level || previous.car != car || !previous.grant.identity().equals(grant.identity()))
                    observations.put(grant.identity().vehicle(), new Observation(client.level, car, grant));
                else if (grant.echoTick() >= previous.grant.echoTick()) {
                    previous.grant = grant;
                    if (!grant.controlling()) previous.pendingLocalInput = null;
                }
                while (observations.size() > 256) observations.remove(observations.keySet().iterator().next());
            } else if (packet.kind() == CoreDrivetrainPayloads.SNAPSHOT) {
                var snapshot = DrivetrainProtocol.decodeServerSnapshot(packet.body()); var observed = observations.get(snapshot.identity().vehicle());
                if (observed == null || observed.world != client.level) return;
                var accepted = observed.gate.receive(snapshot);
                if (accepted.accepted()) { observed.gate = accepted.after(); observed.snapshot = accepted.snapshot(); observed.pairing.bind(connection, observed.world, observed.grant.identity()); observed.pairing.snapshot(connection, observed.world, observed.snapshot, client.level.getGameTime()); }
            } else if (packet.kind() == CoreDrivetrainPayloads.END) {
                var identity = CoreDrivetrainPayloads.readEnd(packet); var observed = observations.get(identity.vehicle());
                if (observed != null && observed.grant.identity().equals(identity)) observations.remove(identity.vehicle());
            }
        } catch (IllegalArgumentException malformed) { /* No fallback decoding into authoritative state. */ }
    }
    private void tick(Minecraft client) {
        if (client.getConnection() == null || connection != null && (!connection.isConnected() || connection.getPacketListener() != client.getConnection())) clear();
        observations.entrySet().removeIf(entry -> entry.getValue().world != client.level || client.level == null
                || client.level.getEntity(entry.getValue().grant.entityId()) != entry.getValue().car || !entry.getValue().car.getUUID().equals(entry.getKey()));
        for (var observed : observations.values()) {
            if (client.player == null || client.player.getVehicle() != observed.car || observed.car.getFirstPassenger() != client.player
                    || !observed.car.isDriving(client.player)) observed.pendingLocalInput = null;
        }
        if (client.player == null || client.level == null || !ClientPlayNetworking.canSend(CoreDrivetrainPayloads.TYPE)
                || !(client.player.getVehicle() instanceof AutomobileEntity car) || car.getFirstPassenger() != client.player || !car.isDriving(client.player)) { drain(); return; }
        var observed = observations.get(car.getUUID());
        if (observed == null || !observed.grant.controlling() || observed.sequence == Long.MAX_VALUE) { drain(); return; }
        boolean controls = client.gui.screen() == null && client.isWindowActive();
        int edges = 0; var range = DrivetrainProtocol.RangeRequest.KEEP;
        RawControls raw;
        if (!controls) { drain(); raw = new RawControls(0, 1, ClutchController.PedalMode.DIGITAL, false); }
        else {
            raw = analogSampler != null ? analogSampler.sample(client, car)
                    : defaultRawControls(client);
            if (up.consumeClick()) edges |= DrivetrainProtocol.SHIFT_UP_EDGE;
            if (down.consumeClick()) edges |= DrivetrainProtocol.SHIFT_DOWN_EDGE;
            if ((edges & 3) == 3) edges &= ~3;
            if (ignition.consumeClick()) edges |= DrivetrainProtocol.IGNITION_TOGGLE_EDGE;
            int requests = 0;
            if (drive.consumeClick()) { range = DrivetrainProtocol.RangeRequest.DRIVE; requests++; }
            if (neutral.consumeClick()) { range = DrivetrainProtocol.RangeRequest.NEUTRAL; requests++; }
            if (reverse.consumeClick()) { range = DrivetrainProtocol.RangeRequest.REVERSE; requests++; }
            if (park.consumeClick()) { range = DrivetrainProtocol.RangeRequest.PARK; requests++; }
            if (requests > 1) range = DrivetrainProtocol.RangeRequest.KEEP;
        }
        var intent = new DrivetrainProtocol.Intent(observed.grant.identity(), ++observed.sequence, observed.grant.echoTick(),
                raw.throttle(), raw.clutchDepression(), raw.clutchMode(), edges, range, raw.startHeld());
        observed.pendingLocalInput = new CoreDrivetrainBridge.ClientInputSample(intent, client.level.getGameTime(), controls);
        ClientPlayNetworking.send(new CoreDrivetrainPayloads.Packet(CoreDrivetrainPayloads.INTENT, DrivetrainProtocol.encodeIntent(intent)));
    }
    @Override public boolean maySimulateMechanics(AutomobileEntity car) {
        var client = Minecraft.getInstance(); var observed = observations.get(car.getUUID());
        return observed != null && observed.world == client.level && observed.car == car && !car.isRemoved()
                && observed.grant.controlling() && observed.grant.connectionNonce() == connectionNonce
                && observed.grant.identity().vehicle().equals(car.getUUID())
                && client.player != null && client.player.isAlive() && client.player.getVehicle() == car
                && car.getFirstPassenger() == client.player && car.isDriving(client.player)
                && client.getConnection() != null && connection != null && connection.isConnected()
                && connection.getPacketListener() == client.getConnection() && ClientPlayNetworking.canSend(CoreDrivetrainPayloads.TYPE);
    }
    @Override public Optional<DrivetrainProtocol.Snapshot> snapshot(AutomobileEntity car) {
        var observed = observations.get(car.getUUID());
        return observed == null || observed.world != car.level() || observed.car != car ? Optional.empty() : Optional.ofNullable(observed.snapshot);
    }
    @Override public Optional<RuntimeMetadata018> metadata(AutomobileEntity car) {
        var observed = observations.get(car.getUUID()); var client = Minecraft.getInstance();
        if (observed == null || observed.world != client.level || observed.car != car || client.level == null
                || client.level.getEntity(observed.grant.entityId()) != car || car.isRemoved() || connection == null
                || !connection.isConnected() || connection.getPacketListener() != client.getConnection()) return Optional.empty();
        return observed.pairing.pair(connection, observed.world, client.level.getGameTime()).map(RuntimeMetadataPairing018.Pair::metadata);
    }

    @Override public Optional<PhysicalDefinitions.Selection> profile(AutomobileEntity car, ProfilePayloadApproval approval,
            String frame, String wheel, String engine, Optional<UUID> physicalMotor) {
        var paired = metadata(car).orElse(null); if (paired == null || paired.approval() != approval) return Optional.empty();
        return VehicleProfileSync015Client.approvedPrediction(approval, paired.approval().identity(), paired.approval().snapshotSequence(), frame, wheel, engine, physicalMotor);
    }
    @Override public Optional<PhysicalDefinitions.Selection> definition(AutomobileEntity car, RuntimeMetadata018 metadata) {
        var paired = metadata(car).orElse(null); if (paired != metadata) return Optional.empty();
        var actual = car.garageComponents(); if (actual == null || !actual.complete()) return Optional.empty();
        String frame = actual.frame().unwrapKey().orElseThrow().identifier().toString(), wheel = actual.wheel().unwrapKey().orElseThrow().identifier().toString(), engine = actual.engine().unwrapKey().orElseThrow().identifier().toString();
        var approval = paired.approval();
        if (!frame.equals(approval.frameId()) || !wheel.equals(approval.wheelId()) || !engine.equals(approval.engineId())) return Optional.empty();
        return VehicleProfileSync015Client.approved(approval.definition(), frame, wheel, engine);
    }
    @Override public Optional<CoreDrivetrainBridge.ClientInputSample> consumeLocalInput(AutomobileEntity car) {
        var client = Minecraft.getInstance(); var observed = observations.get(car.getUUID());
        if (observed == null || observed.world != client.level || observed.car != car || !observed.grant.controlling()
                || client.player == null || client.player.getVehicle() != car || car.getFirstPassenger() != client.player || !car.isDriving(client.player)) return Optional.empty();
        var sample = observed.pendingLocalInput; observed.pendingLocalInput = null;
        if (sample == null || sample.clientTick() > client.level.getGameTime() || client.level.getGameTime() - sample.clientTick() > 1) return Optional.empty();
        // A menu/focus change after sampling still neutralizes local prediction immediately.
        if (client.gui.screen() != null || !client.isWindowActive()) {
            var sent = sample.intent();
            sample = new CoreDrivetrainBridge.ClientInputSample(new DrivetrainProtocol.Intent(sent.identity(), sent.sequence(), sent.echoedServerTick(),
                    0, 1, ClutchController.PedalMode.DIGITAL, 0, DrivetrainProtocol.RangeRequest.KEEP, false), sample.clientTick(), false);
        }
        return Optional.of(sample);
    }
}
