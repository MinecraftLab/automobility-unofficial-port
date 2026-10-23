package io.github.foundationgames.automobility.automobile.drivetrain;

import io.github.foundationgames.automobility.automobile.garage.GarageTransferAccess;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.entity.DrivetrainGarageCommit;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import io.github.foundationgames.automobility.automobile.drivetrain.metadata.RuntimeMetadata018;
import io.github.foundationgames.automobility.automobile.drivetrain.profile.*;

/** Control/presentation transport only. A compatible session never claims longitudinal physics ownership. */
public final class CoreDrivetrainBridge {
    public record InputSample(DrivetrainProtocol.Identity identity, long serverTick,
                              DrivetrainServerSession.HeldInput held, int keyEdges,
                              DrivetrainProtocol.RangeRequest rangeRequest, boolean fresh) { }
    /** Exact already-sampled outbound intent, consumed locally once; never a second keyboard read. */
    public record ClientInputSample(DrivetrainProtocol.Intent intent, long clientTick, boolean controlsAvailable) { }
    public interface ServerAdapter {
        boolean maySimulateMechanics(AutomobileEntity car);
        Optional<InputSample> consumeInput(AutomobileEntity car);
        Optional<DrivetrainProtocol.Identity> identity(AutomobileEntity car);
        void publish(AutomobileEntity car, DrivetrainProtocol.Snapshot snapshot);
        default void publish(AutomobileEntity car, DrivetrainProtocol.Snapshot snapshot, RuntimeMetadata018 metadata) {
            if (!Objects.requireNonNull(metadata).pairs(snapshot)) throw new IllegalArgumentException("unpaired mechanical metadata");
            throw new IllegalStateException("mechanical metadata transport is not installed");
        }
        void committedGarage(AutomobileEntity car, DrivetrainSettingsEnvelope.GarageReceipt receipt);
    }
    public interface ClientAdapter {
        boolean maySimulateMechanics(AutomobileEntity car);
        Optional<DrivetrainProtocol.Snapshot> snapshot(AutomobileEntity car);
        Optional<ClientInputSample> consumeLocalInput(AutomobileEntity car);
        default Optional<RuntimeMetadata018> metadata(AutomobileEntity car) { return Optional.empty(); }
        default Optional<PhysicalDefinitions.Selection> profile(AutomobileEntity car, ProfilePayloadApproval approval,
                String frame, String wheel, String engine, Optional<UUID> physicalMotor) { return Optional.empty(); }
        /** Read-only instrument view; this does not authorize prediction or create an owner binding. */
        default Optional<PhysicalDefinitions.Selection> definition(AutomobileEntity car, RuntimeMetadata018 metadata) { return Optional.empty(); }
    }
    private static ServerAdapter server;
    private static ClientAdapter client;
    private static final Map<AutomobileEntity, UUID> resetTransactions = new WeakHashMap<>();
    private static BiConsumer<AutomobileEntity, DrivetrainSettingsEnvelope.GarageReceipt> resetRuntime = (car, receipt) -> {};
    private CoreDrivetrainBridge() { }
    public static void installServer(ServerAdapter adapter) {
        if (server != null) throw new IllegalStateException("drivetrain server transport already installed");
        server = Objects.requireNonNull(adapter);
    }
    public static void installClient(ClientAdapter adapter) {
        if (client != null) throw new IllegalStateException("drivetrain client transport already installed");
        client = Objects.requireNonNull(adapter);
    }
    /** Called by the sole mechanical owner. This bridge never calls VehicleIgnition or EngineController. */
    public static void installRuntimeReset(BiConsumer<AutomobileEntity, DrivetrainSettingsEnvelope.GarageReceipt> handler) {
        resetRuntime = Objects.requireNonNull(handler);
    }
    public static Optional<InputSample> consumeInput(AutomobileEntity car) {
        return server == null || car.level().isClientSide() ? Optional.empty() : server.consumeInput(car);
    }
    /** Read-only transport readiness only; profile, physics and energy ownership remain the mechanical owner's decision. */
    public static boolean maySimulateMechanics(AutomobileEntity car) {
        return car.level().isClientSide() ? client != null && client.maySimulateMechanics(car)
                : server != null && server.maySimulateMechanics(car);
    }
    public static Optional<DrivetrainProtocol.Identity> serverIdentity(AutomobileEntity car) {
        return server == null || car.level().isClientSide() ? Optional.empty() : server.identity(car);
    }
    public static void publishServerSnapshot(AutomobileEntity car, DrivetrainProtocol.Snapshot snapshot) {
        if (server != null && !car.level().isClientSide()) server.publish(car, snapshot);
    }
    public static void publishServerSnapshot(AutomobileEntity car, DrivetrainProtocol.Snapshot snapshot, RuntimeMetadata018 metadata) {
        if (!Objects.requireNonNull(metadata).pairs(snapshot)) throw new IllegalArgumentException("unpaired mechanical metadata");
        if (server != null && !car.level().isClientSide()) server.publish(car, snapshot, metadata);
    }
    public static Optional<RuntimeMetadata018> clientMetadata(AutomobileEntity car) {
        return client == null || !car.level().isClientSide() ? Optional.empty() : client.metadata(car);
    }
    public static Optional<PhysicalDefinitions.Selection> clientProfile(AutomobileEntity car, ProfilePayloadApproval approval,
            String frame, String wheel, String engine, Optional<UUID> physicalMotor) {
        return client == null || !car.level().isClientSide() ? Optional.empty() : client.profile(car, approval, frame, wheel, engine, physicalMotor);
    }
    public static Optional<PhysicalDefinitions.Selection> clientDefinition(AutomobileEntity car, RuntimeMetadata018 metadata) {
        return client == null || !car.level().isClientSide() ? Optional.empty() : client.definition(car, metadata);
    }
    public static Optional<DrivetrainProtocol.Snapshot> clientSnapshot(AutomobileEntity car) {
        return client == null || !car.level().isClientSide() ? Optional.empty() : client.snapshot(car);
    }
    public static Optional<ClientInputSample> consumeLocalInput(AutomobileEntity car) {
        return client == null || !car.level().isClientSide() ? Optional.empty() : client.consumeLocalInput(car);
    }
    /** Public entry is harmless without the exact package-private durable checkpoint publication scope. */
    public static void committedGarage(AutomobileEntity car, DrivetrainSettingsEnvelope.GarageReceipt receipt) {
        if (!DrivetrainGarageCommit.permits(car, receipt)) throw new IllegalStateException("not a trusted durable garage publication");
        if (receipt.transaction().equals(resetTransactions.get(car))) return;
        if (server != null) server.committedGarage(car, receipt);
        resetRuntime.accept(car, receipt);
        resetTransactions.put(car, receipt.transaction());
    }
    public static boolean replaceSettingsFromAcceptedAction(ServerPlayer actor, AutomobileEntity car,
                                                            DrivetrainSettingsEnvelope.Settings settings) {
        if (actor == null || car == null || settings == null || car.level().isClientSide() || actor.level() != car.level()
                || !actor.isAlive() || car.isRemoved() || actor.distanceToSqr(car) > 64
                || GarageTransferAccess.blocked(car) || !car.getVehicleAccess().mayConfigure(actor.getUUID())) return false;
        ((CoreDrivetrainSettingsAccess)car).drivetrain$replaceSettings(DrivetrainSettingsEnvelope.of(settings), new SettingsPermit(car));
        return true;
    }
    public static final class SettingsPermit {
        private final AutomobileEntity car;
        private final long tick;
        private SettingsPermit(AutomobileEntity car) { this.car = car; this.tick = car.level().getGameTime(); }
        public void require(AutomobileEntity actual) {
            if (actual != car || actual.level().isClientSide() || actual.level().getGameTime() != tick || GarageTransferAccess.blocked(actual))
                throw new IllegalStateException("settings mutation outside accepted action");
        }
    }
}
