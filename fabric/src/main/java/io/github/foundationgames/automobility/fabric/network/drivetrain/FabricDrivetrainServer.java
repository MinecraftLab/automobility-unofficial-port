package io.github.foundationgames.automobility.fabric.network.drivetrain;

import io.github.foundationgames.automobility.automobile.drivetrain.*;
import io.github.foundationgames.automobility.automobile.drivetrain.metadata.*;
import io.github.foundationgames.automobility.automobile.drivetrain.profile.*;
import io.github.foundationgames.automobility.api.drivetrain.profile.*;
import io.github.foundationgames.automobility.api.drivetrain.energy.VehicleEnergyProviders;
import net.fabricmc.loader.api.FabricLoader;
import io.github.foundationgames.automobility.automobile.garage.GarageTransferAccess;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Real registered transport; obtaining a lease does not turn on a new movement model. */
public final class FabricDrivetrainServer implements ModInitializer, CoreDrivetrainBridge.ServerAdapter {
    private static final DrivetrainServerSession.Policy POLICY = new DrivetrainServerSession.Policy(20, 10);
    private static final Map<MinecraftServer, State> SERVERS = new IdentityHashMap<>();
    private static final class Capability { final long nonce = nonce(); boolean agreed; long sentAt = Long.MIN_VALUE; }
    private static final class Meta {
        DrivetrainProtocol.Identity identity;
        DrivetrainProtocol.Snapshot latest;
        UUID committedTransaction;
        final Map<Object, Boolean> granted = new IdentityHashMap<>();
        Meta(DrivetrainProtocol.Identity identity) { this.identity = identity; }
    }
    private static final class Lease {
        final ServerPlayer driver; final Object connection; final AutomobileEntity car;
        DrivetrainServerSession session; int edges; DrivetrainProtocol.RangeRequest range = DrivetrainProtocol.RangeRequest.KEEP;
        long echoSent = Long.MIN_VALUE;
        Lease(ServerPlayer driver, AutomobileEntity car, DrivetrainServerSession session) { this.driver = driver; this.connection = driver.connection; this.car = car; this.session = session; }
    }
    private record ProfileAudience(ServerPlayer player, ServerLevel world, ProfileReloadGate.Snapshot authority) { }
    private static final class State {
        final Map<Object, Capability> capabilities = new IdentityHashMap<>();
        final Map<ServerLevel, UUID> worlds = new IdentityHashMap<>();
        final Map<AutomobileEntity, Meta> cars = new IdentityHashMap<>();
        final Map<Object, ProfileAudience> sentProfiles = new IdentityHashMap<>();
        final Map<AutomobileEntity, Lease> leases = new IdentityHashMap<>();
    }
    @Override public void onInitialize() {
        FabricVehicleEnergyDiscovery.install();
        RuntimeProfileRepository018.configure(FabricLoader.getInstance().getConfigDir().resolve("automobility-physical-profiles.json"));
        new VehicleProfileSync015Bootstrap().onInitialize();
        PayloadTypeRegistry.clientboundPlay().register(MetadataPayload018.TYPE, MetadataPayload018.CODEC);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents.LOAD.register((server, level) -> {
            UUID world = state(server).worlds.computeIfAbsent(level, ignored -> UUID.randomUUID());
            RuntimeProfileRepository018.bind(level, world);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents.UNLOAD.register((server, level) -> {
            RuntimeProfileRepository018.unload(level); var found = SERVERS.get(server); if (found != null) found.worlds.remove(level);
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> { if (success) RuntimeProfileRepository018.reload(server); });
        PayloadTypeRegistry.serverboundPlay().register(CoreDrivetrainPayloads.TYPE, CoreDrivetrainPayloads.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CoreDrivetrainPayloads.TYPE, CoreDrivetrainPayloads.CODEC);
        CoreDrivetrainBridge.installServer(this);
        FabricDrivetrainSettingsServer.initialize();
        ServerPlayNetworking.registerGlobalReceiver(CoreDrivetrainPayloads.TYPE, (packet, context) -> {
            var actor = context.player(); var connection = actor.connection;
            context.server().execute(() -> {
                if (actor.connection != connection || context.server().getPlayerList().getPlayer(actor.getUUID()) != actor) return;
                receive(actor, packet);
            });
        });
        ServerTickEvents.START_SERVER_TICK.register(this::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { RuntimeProfileRepository018.close(server); SERVERS.remove(server); });
    }
    private static State state(MinecraftServer server) { return SERVERS.computeIfAbsent(server, ignored -> new State()); }
    private static long nonce() { long value; do { value = UUID.randomUUID().getLeastSignificantBits(); } while (value == 0); return value; }
    private static boolean live(ServerPlayer actor) { return actor.connection != null && actor.connection.isAcceptingMessages() && actor.isAlive(); }
    private static boolean driver(ServerPlayer actor, AutomobileEntity car) {
        return live(actor) && !car.isRemoved() && actor.level() == car.level() && actor.getVehicle() == car && car.getFirstPassenger() == actor
                && car.isDriving(actor) && car.getVehicleAccess().mayDrive(actor.getUUID()) && !GarageTransferAccess.blocked(car);
    }
    private static Meta meta(State state, AutomobileEntity car) {
        UUID world = state.worlds.computeIfAbsent((ServerLevel)car.level(), level -> UUID.randomUUID());
        RuntimeProfileRepository018.bind((ServerLevel)car.level(), world);
        var existing = state.cars.get(car);
        if (existing == null || !existing.identity.worldInstance().equals(world) || !existing.identity.vehicle().equals(car.getUUID())) {
            existing = new Meta(new DrivetrainProtocol.Identity(car.getUUID(), UUID.randomUUID(), world, nonce()));
            state.cars.put(car, existing); state.leases.remove(car);
        }
        return existing;
    }
    private static boolean compatible(State state, ServerPlayer actor) {
        var capability = state.capabilities.get(actor.connection);
        return live(actor) && capability != null && capability.agreed && ServerPlayNetworking.canSend(actor, CoreDrivetrainPayloads.TYPE);
    }
    private void tick(MinecraftServer server) {
        RuntimeProfileRepository018.tick(server);
        var state = state(server); var liveConnections = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        for (var actor : server.getPlayerList().getPlayers()) {
            if (!live(actor)) continue; liveConnections.add(actor.connection);
            var authority = RuntimeProfileRepository018.snapshot((ServerLevel)actor.level()).orElse(null);
            if (authority != null) ensureProfiles(state, actor, authority);
            if (!ServerPlayNetworking.canSend(actor, CoreDrivetrainPayloads.TYPE)) continue;
            var capability = state.capabilities.computeIfAbsent(actor.connection, ignored -> new Capability());
            long tick = actor.level().getGameTime();
            if (!capability.agreed) {
                if (capability.sentAt == Long.MIN_VALUE || tick < capability.sentAt || tick - capability.sentAt >= 20) {
                    ServerPlayNetworking.send(actor, CoreDrivetrainPayloads.capability(CoreDrivetrainPayloads.CHALLENGE, capability.nonce)); capability.sentAt = tick;
                }
                continue;
            }
            if (actor.getVehicle() instanceof AutomobileEntity car && driver(actor, car)) {
                var meta = meta(state, car); var lease = state.leases.get(car);
                if (lease == null || lease.driver != actor || lease.connection != actor.connection) {
                    meta.identity = new DrivetrainProtocol.Identity(car.getUUID(), meta.identity.incarnation(), meta.identity.worldInstance(), nonce());
                    meta.latest = null; meta.granted.clear();
                    lease = new Lease(actor, car, DrivetrainServerSession.open(new DrivetrainServerSession.Binding(meta.identity, actor.getUUID()), POLICY, tick));
                    state.leases.put(car, lease);
                }
                lease.session = lease.session.atTick(tick);
                if (lease.session.acceptedAtTick() < 0 || tick - lease.session.acceptedAtTick() >= POLICY.inputTtlTicks()) { lease.edges = 0; lease.range = DrivetrainProtocol.RangeRequest.KEEP; }
                if (lease.echoSent == Long.MIN_VALUE || tick < lease.echoSent || tick - lease.echoSent >= 5) {
                    sendGrant(state, car, meta, actor, true, tick);
                }
            }
        }
        state.sentProfiles.keySet().removeIf(connection -> !liveConnections.contains(connection));
        state.capabilities.keySet().removeIf(connection -> !liveConnections.contains(connection));
        for (var iterator = state.leases.entrySet().iterator(); iterator.hasNext();) {
            var item = iterator.next(); var lease = item.getValue();
            if (!driver(lease.driver, lease.car) || lease.driver.connection != lease.connection || !compatible(state, lease.driver)) {
                var meta = state.cars.get(item.getKey()); iterator.remove(); if (meta != null) meta.granted.clear();
            }
        }
        state.cars.entrySet().removeIf(item -> {
            boolean removed = item.getKey().isRemoved() || !(item.getKey().level() instanceof ServerLevel level) || level.getServer() != server;
            if (removed) {
                for (var actor : server.getPlayerList().getPlayers()) if (compatible(state, actor)) ServerPlayNetworking.send(actor, CoreDrivetrainPayloads.end(item.getValue().identity));
            } else item.getValue().granted.keySet().removeIf(connection -> !liveConnections.contains(connection));
            return removed;
        });
    }
    private static void receive(ServerPlayer actor, CoreDrivetrainPayloads.Packet packet) {
        if (!live(actor)) return; var state = state(actor.level().getServer()); var capability = state.capabilities.get(actor.connection);
        try {
            if (packet.kind() == CoreDrivetrainPayloads.CAPABILITY) {
                var reply = CoreDrivetrainPayloads.readCapability(packet, CoreDrivetrainPayloads.CAPABILITY);
                if (capability != null && reply.nonce() == capability.nonce) capability.agreed = true;
                return;
            }
            if (packet.kind() != CoreDrivetrainPayloads.INTENT || !compatible(state, actor) || !(actor.getVehicle() instanceof AutomobileEntity car) || !driver(actor, car)) return;
            var lease = state.leases.get(car); if (lease == null || lease.driver != actor || lease.connection != actor.connection) return;
            var identity = lease.session.binding().identity(); long tick = car.level().getGameTime();
            UUID actualWorld = state.worlds.computeIfAbsent((ServerLevel)car.level(), level -> UUID.randomUUID());
            var context = new DrivetrainServerSession.Context(actor.getUUID(), car.getUUID(), identity.incarnation(), actualWorld, tick, true);
            var accepted = lease.session.receiveBytes(context, packet.body()); lease.session = accepted.after();
            if (accepted.accepted()) { lease.edges |= accepted.acceptedIntent().keyEdges();
                if ((lease.edges & 3) == 3) lease.edges &= ~3;
                if (accepted.acceptedIntent().rangeRequest() != DrivetrainProtocol.RangeRequest.KEEP) lease.range = accepted.acceptedIntent().rangeRequest(); }
        } catch (IllegalArgumentException malformed) { /* Invalid packets never become movement/ignition state. */ }
    }
    private static void sendGrant(State state, AutomobileEntity car, Meta meta, ServerPlayer peer, boolean control, long tick) {
        if (!compatible(state, peer) || peer.level() != car.level()) return;
        var lease = state.leases.get(car);
        if (control && (lease == null || lease.driver != peer || lease.connection != peer.connection
                || !lease.session.binding().identity().equals(meta.identity) || !driver(peer, car))) return;
        var capability = state.capabilities.get(peer.connection);
        ServerPlayNetworking.send(peer, CoreDrivetrainPayloads.grant(capability.nonce, car.getId(), car.level().dimension().identifier().toString(), meta.identity, tick, control));
        // Every controlling grant, including a role change during snapshot publication, issues this actual outbound tick.
        if (control) { lease.session = lease.session.issueEcho(tick); lease.echoSent = tick; }
        meta.granted.put(peer.connection, control);
    }
    @Override public boolean maySimulateMechanics(AutomobileEntity car) {
        if (!(car.level() instanceof ServerLevel level)) return false;
        var state = SERVERS.get(level.getServer()); if (state == null) return false;
        var lease = state.leases.get(car); var meta = state.cars.get(car);
        return lease != null && meta != null && lease.car == car && lease.driver.connection == lease.connection
                && driver(lease.driver, car) && compatible(state, lease.driver)
                && meta.identity.equals(lease.session.binding().identity())
                && meta.identity.worldInstance().equals(state.worlds.get(level));
    }
    @Override public Optional<CoreDrivetrainBridge.InputSample> consumeInput(AutomobileEntity car) {
        if (!(car.level() instanceof ServerLevel level)) return Optional.empty(); var state = state(level.getServer()); var lease = state.leases.get(car);
        if (lease == null || !driver(lease.driver, car) || lease.driver.connection != lease.connection || !compatible(state, lease.driver)) return Optional.empty();
        long tick = level.getGameTime(); lease.session = lease.session.atTick(tick);
        boolean fresh = lease.session.acceptedAtTick() >= 0 && tick - lease.session.acceptedAtTick() < POLICY.inputTtlTicks();
        var result = new CoreDrivetrainBridge.InputSample(lease.session.binding().identity(), tick, lease.session.held(), fresh ? lease.edges : 0,
                fresh ? lease.range : DrivetrainProtocol.RangeRequest.KEEP, fresh);
        lease.edges = 0; lease.range = DrivetrainProtocol.RangeRequest.KEEP; return Optional.of(result);
    }
    @Override public Optional<DrivetrainProtocol.Identity> identity(AutomobileEntity car) {
        return car.level() instanceof ServerLevel level && !car.isRemoved() ? Optional.of(meta(state(level.getServer()), car).identity) : Optional.empty();
    }
    @Override public void publish(AutomobileEntity car, DrivetrainProtocol.Snapshot snapshot) {
        throw new IllegalArgumentException("paired physical metadata required");
    }
    @Override public void publish(AutomobileEntity car, DrivetrainProtocol.Snapshot snapshot, RuntimeMetadata018 metadata) {
        if (!(car.level() instanceof ServerLevel level) || car.isRemoved() || GarageTransferAccess.blocked(car)) return;
        var state = state(level.getServer()); var meta = meta(state, car);
        if (!snapshot.identity().equals(meta.identity) || snapshot.serverTick() != level.getGameTime()
                || meta.latest != null && snapshot.sequence() <= meta.latest.sequence()) throw new IllegalArgumentException("snapshot does not match active server incarnation/cursor");
        var actual = RuntimeProfileRepository018.server(car).orElse(null);
        var proof = metadata.approval();
        if (!metadata.pairs(snapshot) || actual == null || !actual.authority().worldSession().equals(meta.identity.worldInstance())
                || actual.authority().epoch() != proof.definition().epoch() || !actual.authority().sha256().equals(proof.definition().sha256())
                || !actual.parts().frameId().equals(proof.frameId()) || !actual.parts().wheelId().equals(proof.wheelId()) || !actual.parts().engineId().equals(proof.engineId())
                || !actual.payload().equals(proof.payload()) || !proof.physicalMotor().equals(actual.parts().engine().electric() == null ? Optional.empty() : VehicleEnergyProviders.physicalMotorId(car)))
            throw new IllegalArgumentException("metadata does not match actual paid-step car/profile/load identity");
        // Validate and encode both before publishing either. No C2S/legacy payload changes.
        byte[] coreBytes = DrivetrainProtocol.encodeServerSnapshot(snapshot), sidecarBytes = RuntimeMetadata018.encode(metadata);
        var metadataPacket = new MetadataPayload018(sidecarBytes);
        meta.latest = snapshot; var peers = new HashSet<>(PlayerLookup.tracking(car));
        if (car.getFirstPassenger() instanceof ServerPlayer actor) peers.add(actor);
        for (var peer : peers) if (compatible(state, peer) && ServerPlayNetworking.canSend(peer, MetadataPayload018.TYPE)) {
            boolean control = state.leases.containsKey(car) && state.leases.get(car).driver == peer;
            if (!Objects.equals(meta.granted.get(peer.connection), control)) sendGrant(state, car, meta, peer, control, level.getGameTime());
            ensureProfiles(state, peer, actual.authority());
            ServerPlayNetworking.send(peer, new CoreDrivetrainPayloads.Packet(CoreDrivetrainPayloads.SNAPSHOT, coreBytes));
            ServerPlayNetworking.send(peer, metadataPacket);
        }
    }
    private static void ensureProfiles(State state, ServerPlayer peer, ProfileReloadGate.Snapshot authority) {
        var audience = new ProfileAudience(peer, (ServerLevel)peer.level(), authority);
        if (!audience.equals(state.sentProfiles.get(peer.connection)) && VehicleProfileSync015Bootstrap.publish(peer, authority)) state.sentProfiles.put(peer.connection, audience);
    }
    @Override public void committedGarage(AutomobileEntity car, DrivetrainSettingsEnvelope.GarageReceipt receipt) {
        if (!(car.level() instanceof ServerLevel level)) return; var state = state(level.getServer()); var meta = meta(state, car);
        if (receipt.transaction().equals(meta.committedTransaction)) return;
        meta.committedTransaction = receipt.transaction(); state.leases.remove(car);
        meta.identity = new DrivetrainProtocol.Identity(car.getUUID(), UUID.randomUUID(), meta.identity.worldInstance(), nonce());
        meta.latest = null; meta.granted.clear();
    }
}
