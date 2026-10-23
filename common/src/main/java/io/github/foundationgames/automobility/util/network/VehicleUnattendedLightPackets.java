package io.github.foundationgames.automobility.util.network;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.light.VehicleUnattendedLightPolicy;
import io.github.foundationgames.automobility.automobile.light.VehicleUnattendedLightSettings;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.platform.Platform;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.Objects;
import java.util.UUID;

/** Optional inner packets. No changes to physics contracts, old packet layouts or entity data slots. */
public final class VehicleUnattendedLightPackets {
    public static final int QUERY_SIZE = 21, SET_SIZE = 25, STATE_SIZE = 35;
    private static final PlayerPacketRateLimiter LIMIT = new PlayerPacketRateLimiter(4,16,20,1200);
    private VehicleUnattendedLightPackets() {}

    public record Request(int entity, UUID identity, boolean set, int seconds) {
        public Request {
            Objects.requireNonNull(identity);
            if (set && !VehicleUnattendedLightSettings.valid(seconds)) throw new IllegalArgumentException("Invalid light delay");
        }
    }
    public record State(int entity, UUID identity, int selectedSeconds, int effectiveSeconds, int maximumSeconds,
                        boolean allowUnlimited, boolean suppressed) {
        public State {
            Objects.requireNonNull(identity);
            if (!VehicleUnattendedLightSettings.valid(selectedSeconds)
                    || !VehicleUnattendedLightSettings.valid(effectiveSeconds)
                    || maximumSeconds < 0 || maximumSeconds > VehicleUnattendedLightSettings.MAX_SECONDS
                    || new VehicleUnattendedLightSettings(selectedSeconds).restricted(maximumSeconds, allowUnlimited).seconds() != effectiveSeconds)
                throw new IllegalArgumentException("Invalid server light state");
        }
    }
    public static Request decodeRequest(FriendlyByteBuf buf) {
        int size = buf.readableBytes();
        if (size != QUERY_SIZE && size != SET_SIZE) throw new IllegalArgumentException("Light request size");
        int entity = buf.readInt(); UUID identity = buf.readUUID(); int operation = buf.readUnsignedByte();
        if (operation > 1 || (operation == 0 ? size != QUERY_SIZE : size != SET_SIZE))
            throw new IllegalArgumentException("Light operation");
        return new Request(entity, identity, operation == 1, operation == 1 ? buf.readInt() : 0);
    }
    public static FriendlyByteBuf encodeRequest(Request request) {
        var buf = new FriendlyByteBuf(Unpooled.buffer(request.set() ? SET_SIZE : QUERY_SIZE));
        buf.writeInt(request.entity()); buf.writeUUID(request.identity()); buf.writeByte(request.set() ? 1 : 0);
        if (request.set()) buf.writeInt(request.seconds());
        return buf;
    }
    public static State decodeState(FriendlyByteBuf buf) {
        if (buf.readableBytes() != STATE_SIZE || buf.readUnsignedByte() != 1) throw new IllegalArgumentException("Light state version/size");
        int entity = buf.readInt(); UUID identity = buf.readUUID();
        int selected = buf.readInt(), effective = buf.readInt(), maximum = buf.readInt();
        int allow = buf.readUnsignedByte(), suppressed = buf.readUnsignedByte();
        if (allow > 1 || suppressed > 1) throw new IllegalArgumentException("Light state boolean");
        return new State(entity, identity, selected, effective, maximum, allow == 1, suppressed == 1);
    }
    public static FriendlyByteBuf encodeState(State state) {
        var buf = new FriendlyByteBuf(Unpooled.buffer(STATE_SIZE));
        buf.writeByte(1); buf.writeInt(state.entity()); buf.writeUUID(state.identity());
        buf.writeInt(state.selectedSeconds()); buf.writeInt(state.effectiveSeconds()); buf.writeInt(state.maximumSeconds());
        buf.writeBoolean(state.allowUnlimited()); buf.writeBoolean(state.suppressed());
        return buf;
    }
    public static void initServer() {
        CommonPackets.registerReceiver(Automobility.rl("vehicle_unattended_lights"), (server, player, buf) -> {
            final Request request;
            try { request = decodeRequest(buf); } catch (RuntimeException ignored) { return; }
            server.execute(() -> {
                if (!LIMIT.tryAcquire(player.getUUID(), server.getTickCount())) return;
                if (!(player.level().getEntity(request.entity()) instanceof AutomobileEntity car)
                        || !car.getUUID().equals(request.identity()) || !VehicleAccessPackets.near(player, car)
                        || !car.isDriving(player) || !car.getVehicleAccess().mayConfigure(player.getUUID())) return;
                if (request.set()) car.setUnattendedLightSettings(new VehicleUnattendedLightSettings(request.seconds()));
                var limits = VehicleUnattendedLightPolicy.limits();
                var state = new State(car.getId(), car.getUUID(), car.getUnattendedLightSettings().seconds(),
                        car.getEffectiveUnattendedLightSettings().seconds(), limits.maximumSeconds(), limits.allowUnlimited(),
                        car.areUnattendedLightsSuppressed());
                Platform.get().serverSendPacket(player, Automobility.rl("vehicle_unattended_lights_state"), encodeState(state));
            });
        });
    }
}
