package io.github.foundationgames.automobility.util.network;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.attachment.rear.BannerPostRearAttachment;
import io.github.foundationgames.automobility.automobile.attachment.rear.ExtendableRearAttachment;
import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.platform.Platform;
import io.github.foundationgames.automobility.util.TriCons;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.HashMap;
import java.util.Map;

public enum CommonPackets {;
    static final int AUTOMOBILE_SYNC_STATE_SIZE = 86;
    private static final int AUTOMOBILE_CLIENT_INPUT_OFFSET = 52;
    private static final int COMPONENT_SYNC_REQUESTS_PER_TICK = 128;
    private static final int COMPONENT_SYNC_REQUESTS_PER_SECOND = 512;
    private static final int COMPONENT_SYNC_WINDOW_TICKS = 20;
    private static final int COMPONENT_SYNC_LIMITER_STALE_TICKS = 20 * 60;
    private static final double COMPONENT_SYNC_MAX_DISTANCE_SQUARED = 192 * 192;
    private static final PlayerPacketRateLimiter COMPONENT_SYNC_RATE_LIMITER = new PlayerPacketRateLimiter(
            COMPONENT_SYNC_REQUESTS_PER_TICK,
            COMPONENT_SYNC_REQUESTS_PER_SECOND,
            COMPONENT_SYNC_WINDOW_TICKS,
            COMPONENT_SYNC_LIMITER_STALE_TICKS
    );
    static final int VEHICLE_LIGHT_MODE_PAYLOAD_SIZE = Integer.BYTES + Byte.BYTES;
    static final int VEHICLE_SIGNAL_PAYLOAD_SIZE = Integer.BYTES + Byte.BYTES;
    static final int VEHICLE_SIGNAL_AUTO_CANCEL_PAYLOAD_SIZE = Integer.BYTES + Byte.BYTES;
    static final int VEHICLE_SIGNAL_SETTINGS_PAYLOAD_SIZE = Integer.BYTES + 3 * Byte.BYTES;
    private static final PlayerPacketRateLimiter VEHICLE_SIGNAL_PREFERENCES_RATE_LIMITER = new PlayerPacketRateLimiter(4, 16, 20, 1200);
    private static final PlayerPacketRateLimiter VEHICLE_SIGNAL_SETTINGS_RATE_LIMITER = new PlayerPacketRateLimiter(4, 16, 20, 1200);
    private static final PlayerPacketRateLimiter VEHICLE_SIGNAL_RATE_LIMITER = new PlayerPacketRateLimiter(4, 16, 20, 1200);
    private static final PlayerPacketRateLimiter VEHICLE_LIGHT_MODE_RATE_LIMITER = new PlayerPacketRateLimiter(
            4, 16, 20, 20 * 60
    );
    public static final Map<Identifier, TriCons<MinecraftServer, ServerPlayer, FriendlyByteBuf>> SERVERBOUND_HANDLERS = new HashMap<>();
    static final int VEHICLE_IGNITION_PAYLOAD_SIZE = 5;
    static final int VEHICLE_ENGINE_SETTINGS_PAYLOAD_SIZE = 10;
    private static final PlayerPacketRateLimiter VEHICLE_ENGINE_RATE_LIMITER = new PlayerPacketRateLimiter(4, 16, 20, 1200);

    public static void registerReceiver(Identifier rl, TriCons<MinecraftServer, ServerPlayer, FriendlyByteBuf> run) {
        SERVERBOUND_HANDLERS.put(rl, run);
    }

    public static void sendClientboundAutomobileSyncPacket(AutomobileEntity entity, ServerPlayer player) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(entity.getId());
        entity.writeSyncStateData(buf);
        Platform.get().serverSendPacket(player, Automobility.rl("sync_automobile_data"), buf);
    }

    public static void sendSyncAutomobileAttachmentsPacket(AutomobileEntity entity, ServerPlayer player) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(entity.getId());
        buf.writeUtf(entity.getRearAttachmentType().id().toString());
        buf.writeUtf(entity.getFrontAttachmentType().id().toString());
        Platform.get().serverSendPacket(player, Automobility.rl("sync_automobile_attachments"), buf);
    }

    public static void sendBannerPostAttachmentUpdatePacket(AutomobileEntity entity, DyeColor baseColor, BannerPatternLayers layers, ServerPlayer player) {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), entity.level().registryAccess());

        if (entity.getRearAttachment() instanceof BannerPostRearAttachment) {
            buf.writeInt(entity.getId());
            if (baseColor != null && layers != null) {
                buf.writeBoolean(true);
                DyeColor.STREAM_CODEC.encode(buf, baseColor);
                BannerPatternLayers.STREAM_CODEC.encode(buf, layers);
            } else {
                buf.writeBoolean(false);
            }
            Platform.get().serverSendPacket(player, Automobility.rl("update_banner_post"), buf);
        }
    }

    public static void sendExtendableAttachmentUpdatePacket(AutomobileEntity entity, boolean extended, ServerPlayer player) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());

        if (entity.getRearAttachment() instanceof ExtendableRearAttachment) {
            buf.writeInt(entity.getId());
            buf.writeBoolean(extended);
            Platform.get().serverSendPacket(player, Automobility.rl("update_extendable_attachment"), buf);
        }
    }

    public static void init() {
        VehicleAccessPackets.init();
        VehicleUnattendedLightPackets.initServer();
        CommonPackets.registerReceiver(Automobility.rl("set_vehicle_signal_settings"), (server, player, buf) -> {
            if (!isValidVehicleSignalSettingsPayload(buf)) return;
            int entityId = buf.readInt();
            boolean autoCancel = buf.readBoolean(), comfort = buf.readBoolean(), lockFlash = buf.readBoolean();
            server.execute(() -> {
                if (!VEHICLE_SIGNAL_PREFERENCES_RATE_LIMITER.tryAcquire(player.getUUID(), server.getTickCount())) return;
                if (player.level().getEntity(entityId) instanceof AutomobileEntity car
                        && canChangeVehicleSignalSettings(car.isRemoved(), car.level() == player.level(),
                        player.isAlive() && car.isDriving(player), car.getVehicleAccess().mayConfigure(player.getUUID()))) {
                    car.setVehicleSignalAutoCancelEnabled(autoCancel);
                    car.setVehicleComfortSignalsEnabled(comfort);
                    car.setVehicleLockSignalConfirmationEnabled(lockFlash);
                }
            });
        });
        CommonPackets.registerReceiver(Automobility.rl("set_vehicle_signal_auto_cancel"), (server, player, buf) -> {
            if (!isValidVehicleSignalAutoCancelPayload(buf)) return;
            int entityId = buf.readInt();
            boolean enabled = buf.readBoolean();
            server.execute(() -> {
                if (!VEHICLE_SIGNAL_SETTINGS_RATE_LIMITER.tryAcquire(player.getUUID(), server.getTickCount())) return;
                if (player.level().getEntity(entityId) instanceof AutomobileEntity car
                        && canChangeVehicleSignalAutoCancel(car.isRemoved(), car.level() == player.level(),
                        player.isAlive() && car.isDriving(player), car.getVehicleAccess().mayConfigure(player.getUUID())))
                    car.setVehicleSignalAutoCancelEnabled(enabled);
            });
        });
        CommonPackets.registerReceiver(Automobility.rl("vehicle_signal"), (server, player, buf) -> {
            if (!isValidVehicleSignalPayload(buf)) return;
            int entityId = buf.readInt(), request = buf.readUnsignedByte();
            server.execute(() -> {
                if (!VEHICLE_SIGNAL_RATE_LIMITER.tryAcquire(player.getUUID(), server.getTickCount())) return;
                // Signals are driver operation, not permission to alter vehicle configuration.
                // A revoked key does not eject an existing driver or disable their driving controls.
                if (player.level().getEntity(entityId) instanceof AutomobileEntity car
                        && canChangeVehicleSignal(car.isRemoved(), car.level() == player.level(),
                        player.isAlive() && car.isDriving(player))) car.applyVehicleSignalRequest(request);
            });
        });
        CommonPackets.registerReceiver(Automobility.rl("set_vehicle_ignition"), (server, player, buf) -> {
            if (!isValidVehicleIgnitionPayload(buf)) return;
            int entityId = buf.readInt();
            boolean start = buf.readBoolean();
            server.execute(() -> {
                if (!VEHICLE_ENGINE_RATE_LIMITER.tryAcquire(player.getUUID(), server.getTickCount())) return;
                if (player.level().getEntity(entityId) instanceof AutomobileEntity car
                        && canChangeVehicleEngine(car.isRemoved(), car.level() == player.level(), player.isAlive() && car.isDriving(player)))
                    car.requestIgnition(start);
            });
        });
        CommonPackets.registerReceiver(Automobility.rl("set_vehicle_engine_settings"), (server, player, buf) -> {
            if (!isValidVehicleEngineSettingsPayload(buf)) return;
            int entityId = buf.readInt();
            var settings = new io.github.foundationgames.automobility.automobile.engine.VehicleEngineSettings(
                    buf.readBoolean(), buf.readInt(), buf.readBoolean());
            server.execute(() -> {
                if (!VEHICLE_ENGINE_RATE_LIMITER.tryAcquire(player.getUUID(), server.getTickCount())) return;
                if (player.level().getEntity(entityId) instanceof AutomobileEntity car
                        && canChangeVehicleEngine(car.isRemoved(), car.level() == player.level(), player.isAlive() && car.isDriving(player)))
                    if (car.getVehicleAccess().mayConfigure(player.getUUID())) car.setVehicleEngineSettings(settings);
            });
        });
        CommonPackets.registerReceiver(Automobility.rl("set_vehicle_light_mode"), (server, player, buf) -> {
            if (!isValidVehicleLightModePayload(buf)) {
                return;
            }
            int entityId = buf.readInt();
            var mode = VehicleLightMode.fromId(buf.readUnsignedByte());
            server.execute(() -> {
                if (!VEHICLE_LIGHT_MODE_RATE_LIMITER.tryAcquire(player.getUUID(), server.getTickCount())) {
                    return;
                }
                // Recheck the driver on the server thread: the sender may have dismounted or
                // changed dimension while this request waited in the execution queue.
                if (player.level().getEntity(entityId) instanceof AutomobileEntity automobile
                        && canChangeVehicleLightMode(automobile.isRemoved(), automobile.level() == player.level(),
                        player.isAlive() && automobile.isDriving(player))) {
                    automobile.requestVehicleLightMode(mode);
                }
            });
        });
        CommonPackets.registerReceiver(Automobility.rl("sync_automobile_data"), (server, player, buf) -> {
            if (buf.readableBytes() != Integer.BYTES + AUTOMOBILE_SYNC_STATE_SIZE) {
                return;
            }

            int entityId = buf.readInt();
            if (!isValidAutomobileSyncState(buf)) {
                return;
            }

            var clientInput = readClientAutomobileInput(buf);
            server.execute(() -> {
                if (player.level().getEntity(entityId) instanceof AutomobileEntity automobile
                        && automobile.isDriving(player)
                        && automobile.tryAcquireClientSyncSlot()) {
                    automobile.applyClientInputState(
                            clientInput.accelerating(),
                            clientInput.braking(),
                            clientInput.inputSteering(),
                            clientInput.holdingDrift(),
                            clientInput.holdingHorn()
                    );
                    // Even unchanged input is a periodic request to publish the server-computed
                    // state to observers (including reverse/coasting states where hSpeed <= 0).
                    automobile.markDirty();
                }
            });
        });
        CommonPackets.registerReceiver(Automobility.rl("request_sync_automobile_components"), (server, player, buf) -> {
            if (!isExactEntityIdPayload(buf)) {
                return;
            }

            int entityId = buf.readInt();
            server.execute(() -> {
                if (!COMPONENT_SYNC_RATE_LIMITER.tryAcquire(player.getUUID(), player.level().getGameTime())) {
                    return;
                }

                if (player.level().getEntity(entityId) instanceof AutomobileEntity automobile
                        && canRequestAutomobileComponents(player, automobile)) {
                    sendSyncAutomobileAttachmentsPacket(automobile, player);

                    var fAtt = automobile.getFrontAttachment();
                    if (fAtt != null) fAtt.updatePacketRequested(player);

                    var rAtt = automobile.getRearAttachment();
                    if (rAtt != null) rAtt.updatePacketRequested(player);
                }
            });
        });
    }

    static boolean isExactEntityIdPayload(FriendlyByteBuf buf) {
        return buf.readableBytes() == Integer.BYTES;
    }

    static boolean canChangeVehicleEngine(boolean removed, boolean sameLevel, boolean currentDriver) {
        return !removed && sameLevel && currentDriver;
    }

    static boolean isValidVehicleIgnitionPayload(FriendlyByteBuf buf) {
        return buf.readableBytes() == VEHICLE_IGNITION_PAYLOAD_SIZE && buf.getUnsignedByte(buf.readerIndex() + 4) <= 1;
    }

    static boolean isValidVehicleEngineSettingsPayload(FriendlyByteBuf buf) {
        int offset = buf.readerIndex();
        return buf.readableBytes() == VEHICLE_ENGINE_SETTINGS_PAYLOAD_SIZE
                && buf.getUnsignedByte(offset + 4) <= 1 && buf.getUnsignedByte(offset + 9) <= 1
                && io.github.foundationgames.automobility.automobile.engine.VehicleEngineSettings.validDelay(buf.getInt(offset + 5));
    }

    static boolean isValidVehicleLightModePayload(FriendlyByteBuf buf) {
        return buf.readableBytes() == VEHICLE_LIGHT_MODE_PAYLOAD_SIZE
                && VehicleLightMode.isValidId(buf.getUnsignedByte(buf.readerIndex() + Integer.BYTES));
    }

    static boolean canChangeVehicleLightMode(boolean removed, boolean sameLevel, boolean currentDriver) {
        return !removed && sameLevel && currentDriver;
    }

    static boolean isValidVehicleSignalPayload(FriendlyByteBuf buf) {
        return buf.readableBytes() == VEHICLE_SIGNAL_PAYLOAD_SIZE
                && io.github.foundationgames.automobility.automobile.light.VehicleSignalController.isValidRequest(
                buf.getUnsignedByte(buf.readerIndex() + Integer.BYTES));
    }

    static boolean canChangeVehicleSignal(boolean removed, boolean sameLevel, boolean currentDriver) {
        return !removed && sameLevel && currentDriver;
    }

    static boolean isValidVehicleSignalAutoCancelPayload(FriendlyByteBuf buf) {
        return buf.readableBytes() == VEHICLE_SIGNAL_AUTO_CANCEL_PAYLOAD_SIZE
                && buf.getUnsignedByte(buf.readerIndex() + Integer.BYTES) <= 1;
    }

    static boolean canChangeVehicleSignalAutoCancel(boolean removed, boolean sameLevel,
                                                    boolean currentDriver, boolean mayConfigure) {
        return !removed && sameLevel && currentDriver && mayConfigure;
    }

    static boolean isValidVehicleSignalSettingsPayload(FriendlyByteBuf buf) {
        int offset = buf.readerIndex();
        return buf.readableBytes() == VEHICLE_SIGNAL_SETTINGS_PAYLOAD_SIZE
                && buf.getUnsignedByte(offset + 4) <= 1
                && buf.getUnsignedByte(offset + 5) <= 1
                && buf.getUnsignedByte(offset + 6) <= 1;
    }

    static boolean canChangeVehicleSignalSettings(boolean removed, boolean sameLevel,
                                                  boolean currentDriver, boolean mayConfigure) {
        return !removed && sameLevel && currentDriver && mayConfigure;
    }

    private static boolean canRequestAutomobileComponents(ServerPlayer player, AutomobileEntity automobile) {
        if (automobile.isRemoved() || automobile.level() != player.level()) {
            return false;
        }
        if (automobile.isDriving(player)) {
            return true;
        }

        var automobileChunk = ChunkPos.containing(automobile.blockPosition());
        return player.getChunkTrackingView().contains(automobileChunk)
                && player.distanceToSqr(automobile) <= COMPONENT_SYNC_MAX_DISTANCE_SQUARED;
    }

    static ClientAutomobileInput readClientAutomobileInput(FriendlyByteBuf buf) {
        int readerIndex = buf.readerIndex();
        try {
            // The legacy payload remains 86 bytes for .30 clients and ecosystem mixins. Skip all
            // client-authored physics and position fields: only input intent crosses the trust boundary.
            buf.skipBytes(AUTOMOBILE_CLIENT_INPUT_OFFSET);
            boolean accelerating = buf.readBoolean();
            boolean braking = buf.readBoolean();
            float inputSteering = buf.readFloat();
            boolean holdingDrift = buf.readBoolean();
            boolean holdingHorn = buf.readBoolean();
            return new ClientAutomobileInput(
                    accelerating,
                    braking,
                    inputSteering,
                    holdingDrift,
                    holdingHorn
            );
        } finally {
            buf.readerIndex(readerIndex);
        }
    }

    static boolean isValidAutomobileSyncState(FriendlyByteBuf buf) {
        if (buf.readableBytes() != AUTOMOBILE_SYNC_STATE_SIZE) {
            return false;
        }

        int readerIndex = buf.readerIndex();
        try {
            int boostTimer = buf.readInt();
            float steering = buf.readFloat();
            float wheelAngle = buf.readFloat();
            int turboCharge = buf.readInt();
            float engineSpeed = buf.readFloat();
            float boostSpeed = buf.readFloat();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            float yaw = buf.readFloat();
            buf.readBoolean();
            buf.readBoolean();
            float inputSteering = buf.readFloat();
            buf.readBoolean();
            buf.readBoolean();
            buf.readBoolean();
            buf.readBoolean();
            double vertical = buf.readDouble();
            float qx = buf.readFloat();
            float qy = buf.readFloat();
            float qz = buf.readFloat();
            float qw = buf.readFloat();

            double quaternionLength = qx * qx + qy * qy + qz * qz + qw * qw;
            return boostTimer >= 0 && boostTimer <= 1_000_000
                    && turboCharge >= 0 && turboCharge <= 1_000_000
                    && finiteInRange(steering, -1.01, 1.01)
                    && Float.isFinite(wheelAngle)
                    && finiteInRange(engineSpeed, -1000, 1000)
                    && finiteInRange(boostSpeed, -1000, 1000)
                    && finiteInRange(x, -32_000_000, 32_000_000)
                    && finiteInRange(y, -32_000_000, 32_000_000)
                    && finiteInRange(z, -32_000_000, 32_000_000)
                    && Float.isFinite(yaw)
                    && finiteInRange(inputSteering, -1.01, 1.01)
                    && finiteInRange(vertical, -32_000_000, 32_000_000)
                    && Float.isFinite(qx) && Float.isFinite(qy) && Float.isFinite(qz) && Float.isFinite(qw)
                    && quaternionLength >= 0.5 && quaternionLength <= 1.5
                    && !buf.isReadable();
        } catch (IndexOutOfBoundsException ignored) {
            return false;
        } finally {
            buf.readerIndex(readerIndex);
        }
    }

    private static boolean finiteInRange(double value, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }

    record ClientAutomobileInput(
            boolean accelerating,
            boolean braking,
            float inputSteering,
            boolean holdingDrift,
            boolean holdingHorn
    ) {
    }
}
