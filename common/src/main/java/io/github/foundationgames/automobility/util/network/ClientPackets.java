package io.github.foundationgames.automobility.util.network;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.attachment.FrontAttachmentType;
import io.github.foundationgames.automobility.automobile.attachment.RearAttachmentType;
import io.github.foundationgames.automobility.automobile.attachment.rear.BannerPostRearAttachment;
import io.github.foundationgames.automobility.automobile.attachment.rear.ExtendableRearAttachment;
import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.platform.Platform;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

public enum ClientPackets {;
    public static final Map<Identifier, BiConsumer<Minecraft, FriendlyByteBuf>> CLIENTBOUND_HANDLERS = new HashMap<>();

    public static void registerReceiver(Identifier rl, BiConsumer<Minecraft, FriendlyByteBuf> run) {
        CLIENTBOUND_HANDLERS.put(rl, run);
    }

    public static void sendServerboundAutomobileSyncPacket(AutomobileEntity entity) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(entity.getId());
        entity.writeSyncStateData(buf);
        Platform.get().clientSendPacket(Automobility.rl("sync_automobile_data"), buf);
    }

    public static void requestSyncAutomobileComponentsPacket(AutomobileEntity entity) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(entity.getId());
        Platform.get().clientSendPacket(Automobility.rl("request_sync_automobile_components"), buf);
    }

    public static void sendVehicleLightModePacket(AutomobileEntity entity, VehicleLightMode mode) {
        Objects.requireNonNull(mode, "mode");
        var buf = new FriendlyByteBuf(Unpooled.buffer(CommonPackets.VEHICLE_LIGHT_MODE_PAYLOAD_SIZE));
        buf.writeInt(entity.getId());
        buf.writeByte(mode.id());
        Platform.get().clientSendPacket(Automobility.rl("set_vehicle_light_mode"), buf);
    }

    public static void sendVehicleSignalPacket(AutomobileEntity entity, int request) {
        Objects.requireNonNull(entity, "entity");
        if (!io.github.foundationgames.automobility.automobile.light.VehicleSignalController.isValidRequest(request)) return;
        var buf = new FriendlyByteBuf(Unpooled.buffer(CommonPackets.VEHICLE_SIGNAL_PAYLOAD_SIZE));
        buf.writeInt(entity.getId());
        buf.writeByte(request);
        Platform.get().clientSendPacket(Automobility.rl("vehicle_signal"), buf);
    }

    public static void sendVehicleSignalAutoCancelPacket(AutomobileEntity entity, boolean enabled) {
        var buf = new FriendlyByteBuf(Unpooled.buffer(CommonPackets.VEHICLE_SIGNAL_AUTO_CANCEL_PAYLOAD_SIZE));
        buf.writeInt(entity.getId());
        buf.writeBoolean(enabled);
        Platform.get().clientSendPacket(Automobility.rl("set_vehicle_signal_auto_cancel"), buf);
    }

    public static void sendVehicleSignalSettingsPacket(AutomobileEntity entity, boolean autoCancel,
                                                       boolean comfort, boolean lockFlash) {
        Objects.requireNonNull(entity, "entity");
        var buf = new FriendlyByteBuf(Unpooled.buffer(CommonPackets.VEHICLE_SIGNAL_SETTINGS_PAYLOAD_SIZE));
        buf.writeInt(entity.getId());
        buf.writeBoolean(autoCancel); buf.writeBoolean(comfort); buf.writeBoolean(lockFlash);
        Platform.get().clientSendPacket(Automobility.rl("set_vehicle_signal_settings"), buf);
    }

    public static void sendVehicleIgnitionPacket(AutomobileEntity entity, boolean start) {
        var buf = new FriendlyByteBuf(Unpooled.buffer(CommonPackets.VEHICLE_IGNITION_PAYLOAD_SIZE));
        buf.writeInt(entity.getId());
        buf.writeBoolean(start);
        Platform.get().clientSendPacket(Automobility.rl("set_vehicle_ignition"), buf);
    }

    public static void sendVehicleEngineSettingsPacket(AutomobileEntity entity,
            io.github.foundationgames.automobility.automobile.engine.VehicleEngineSettings settings) {
        Objects.requireNonNull(settings, "settings");
        if (!io.github.foundationgames.automobility.automobile.engine.VehicleEngineSettings.validDelay(settings.idleShutdownSeconds())) return;
        var buf = new FriendlyByteBuf(Unpooled.buffer(CommonPackets.VEHICLE_ENGINE_SETTINGS_PAYLOAD_SIZE));
        buf.writeInt(entity.getId());
        buf.writeBoolean(settings.automaticStart());
        buf.writeInt(settings.idleShutdownSeconds());
        buf.writeBoolean(settings.lightsWithEngine());
        Platform.get().clientSendPacket(Automobility.rl("set_vehicle_engine_settings"), buf);
    }

    public static void sendVehicleAccessPacket(AutomobileEntity entity, VehicleAccessPackets.Action action, int value, String target) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(entity.getId()); buf.writeByte(action.ordinal()); buf.writeByte(value); buf.writeUtf(target, 36);
        Platform.get().clientSendPacket(Automobility.rl("vehicle_access"), buf);
    }

    public static void sendVehicleProfilePacket(AutomobileEntity entity,
            io.github.foundationgames.automobility.client.vehicle.VehicleConfigurationProfile profile) {
        boolean includeMode = io.github.foundationgames.automobility.client.vehicle.MomentumVehicleCapabilities.drivingModeSupported(entity);
        var buf = new FriendlyByteBuf(Unpooled.buffer(includeMode ? 16 : 15));
        buf.writeInt(entity.getId()); buf.writeBoolean(profile.engine().automaticStart());
        buf.writeInt(profile.engine().idleShutdownSeconds()); buf.writeBoolean(profile.engine().lightsWithEngine());
        buf.writeByte(profile.lights().id()); buf.writeByte(profile.feedback().ordinal());
        buf.writeBoolean(profile.signalAutoCancel());
        buf.writeBoolean(profile.comfortSignals());
        buf.writeBoolean(profile.lockSignalConfirmation());
        if (includeMode) buf.writeByte(profile.drivingMode().id());
        Platform.get().clientSendPacket(Automobility.rl("apply_vehicle_profile"), buf);
    }

    public static void sendVehicleDrivingModePacket(AutomobileEntity entity,
            io.github.foundationgames.automobility.automobile.driving.VehicleDrivingMode mode) {
        var buf = new FriendlyByteBuf(Unpooled.buffer(5));
        buf.writeInt(entity.getId()); buf.writeByte(mode.id());
        Platform.get().clientSendPacket(Automobility.rl("set_vehicle_driving_mode"), buf);
    }

    public static void initClient() {
        io.github.foundationgames.automobility.client.vehicle.VehicleUnattendedLightsClient.init();
        ClientPackets.registerReceiver(Automobility.rl("vehicle_action_result"), (client, buf) -> {
            if (buf.readableBytes() < 5 || buf.readableBytes() > 64) return;
            int entity = buf.readInt(); String key = buf.readUtf(32);
            if (buf.isReadable() || !java.util.Set.of("denied", "invalid_target", "locked", "unlocked", "updated", "profile_applied").contains(key)) return;
            client.execute(() -> io.github.foundationgames.automobility.client.vehicle.VehicleClientControls.receiveActionFeedback(entity, key));
        });
        ClientPackets.registerReceiver(Automobility.rl("sync_automobile_data"), (client, buf) -> {
            var dup = new FriendlyByteBuf(buf.copy());
            int entityId = dup.readInt();
            client.execute(() -> {
                if (client.player.level().getEntity(entityId) instanceof AutomobileEntity automobile) {
                    automobile.readSyncStateData(dup);
                }
            });
        });
        ClientPackets.registerReceiver(Automobility.rl("sync_automobile_attachments"), (client, buf) -> {
            int entityId = buf.readInt();
            var rearAtt = RearAttachmentType.REGISTRY.getOrDefault(Identifier.tryParse(buf.readUtf()));
            var frontAtt = FrontAttachmentType.REGISTRY.getOrDefault(Identifier.tryParse(buf.readUtf()));
            client.execute(() -> {
                if (client.player.level().getEntity(entityId) instanceof AutomobileEntity automobile) {
                    automobile.setRearAttachment(rearAtt);
                    automobile.setFrontAttachment(frontAtt);
                }
            });
        });
        ClientPackets.registerReceiver(Automobility.rl("update_banner_post"), (client, buf) -> {
            var rbuf = new RegistryFriendlyByteBuf(buf, client.level.registryAccess());

            int entityId = buf.readInt();
            final DyeColor baseColor;
            final BannerPatternLayers layers;
            if (buf.readBoolean()) {
                baseColor = DyeColor.STREAM_CODEC.decode(buf);
                layers = BannerPatternLayers.STREAM_CODEC.decode(rbuf);
            } else {
                baseColor = null;
                layers = null;
            }
            client.execute(() -> {
                if (client.player.level().getEntity(entityId) instanceof AutomobileEntity automobile &&
                        automobile.getRearAttachment() instanceof BannerPostRearAttachment bannerPost) {
                    bannerPost.setBanner(baseColor, layers);
                }
            });
        });
        ClientPackets.registerReceiver(Automobility.rl("update_extendable_attachment"), (client, buf) -> {
            int entityId = buf.readInt();
            boolean extended = buf.readBoolean();
            client.execute(() -> {
                if (client.player.level().getEntity(entityId) instanceof AutomobileEntity automobile &&
                        automobile.getRearAttachment() instanceof ExtendableRearAttachment att) {
                    att.setExtended(extended);
                }
            });
        });
    }
}
