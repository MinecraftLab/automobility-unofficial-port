package io.github.foundationgames.automobility.util.network;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.access.VehicleAccessState;
import io.github.foundationgames.automobility.automobile.engine.VehicleEngineSettings;
import io.github.foundationgames.automobility.automobile.driving.VehicleDrivingMode;
import io.github.foundationgames.automobility.automobile.driving.VehicleDrivingModeSupport;
import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.platform.Platform;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/** Access operations recheck authority, distance and occupancy on the server thread. */
public final class VehicleAccessPackets {
    private static final PlayerPacketRateLimiter LIMIT = new PlayerPacketRateLimiter(4, 16, 20, 1200);
    public enum Action { CLAIM, RELEASE, LOCK, PUBLIC_DRIVE, PUBLIC_CONFIGURE, SHARED_CONFIGURE, ADD_KEY, REMOVE_KEY, FEEDBACK }
    public record Request(int entity, Action action, int value, String target) {}
    public record ProfileRequest(int entity, boolean automaticStart, int idleSeconds, boolean lightsWithEngine,
                                 VehicleLightMode lights, VehicleAccessState.Feedback feedback, boolean signalAutoCancel,
                                 boolean comfortSignals, boolean lockSignalConfirmation, VehicleDrivingMode drivingMode) {
        public ProfileRequest {
            feedback = java.util.Objects.requireNonNull(feedback).withoutHorn();
            lockSignalConfirmation = false;
        }

        public ProfileRequest(int entity, boolean automaticStart, int idleSeconds, boolean lightsWithEngine,
                              VehicleLightMode lights, VehicleAccessState.Feedback feedback, boolean signalAutoCancel,
                              boolean comfortSignals, boolean lockSignalConfirmation) {
            this(entity, automaticStart, idleSeconds, lightsWithEngine, lights, feedback, signalAutoCancel,
                    comfortSignals, lockSignalConfirmation, null);
        }

        public ProfileRequest(int entity, boolean automaticStart, int idleSeconds, boolean lightsWithEngine,
                              VehicleLightMode lights, VehicleAccessState.Feedback feedback, boolean signalAutoCancel) {
            this(entity, automaticStart, idleSeconds, lightsWithEngine, lights, feedback, signalAutoCancel, true, false);
        }
    }
    private VehicleAccessPackets() {}

    public static Request decode(FriendlyByteBuf buf) {
        if (buf.readableBytes() < 7 || buf.readableBytes() > 128) throw new IllegalArgumentException("Access packet size");
        int entity=buf.readInt(), id=buf.readUnsignedByte(), value=buf.readUnsignedByte();
        String target=buf.readUtf(36);
        if (buf.isReadable() || id>=Action.values().length) throw new IllegalArgumentException("Invalid access packet");
        var action=Action.values()[id];
        if (value > (action==Action.FEEDBACK ? 3 : 1)
                || ((action==Action.CLAIM || action==Action.RELEASE || action==Action.ADD_KEY || action==Action.REMOVE_KEY) && value!=0)
                || ((action!=Action.ADD_KEY && action!=Action.REMOVE_KEY) && !target.isEmpty())
                || ((action==Action.ADD_KEY || action==Action.REMOVE_KEY) && target.isBlank()))
            throw new IllegalArgumentException("Invalid access operation");
        return new Request(entity, action, value, target);
    }

    public static void init() {
        CommonPackets.registerReceiver(Automobility.rl("set_vehicle_driving_mode"), (server, player, buf) -> {
            final DrivingModeRequest request;
            try { request = decodeDrivingMode(buf); } catch (RuntimeException ignored) { return; }
            server.execute(() -> {
                if (!LIMIT.tryAcquire(player.getUUID(), server.getTickCount())) return;
                if (!(player.level().getEntity(request.entity()) instanceof AutomobileEntity car)
                        || !near(player, car) || !car.isDriving(player)
                        || !car.getVehicleAccess().mayConfigure(player.getUUID())
                        || (VehicleDrivingModeSupport.capabilities() & VehicleDrivingModeSupport.SUPPORTED) == 0
                        || !VehicleDrivingModeSupport.mayChange(car, request.mode())) {
                    feedback(player, request.entity(), "denied"); return;
                }
                car.setVehicleDrivingMode(request.mode());
                feedback(player, request.entity(), "updated");
            });
        });
        CommonPackets.registerReceiver(Automobility.rl("vehicle_access"), (server, player, buf) -> {
            final Request request;
            try { request=decode(buf); } catch (RuntimeException ignored) { return; }
            server.execute(() -> {
                if (!LIMIT.tryAcquire(player.getUUID(), server.getTickCount())) return;
                if (player.level().getEntity(request.entity()) instanceof AutomobileEntity car)
                    apply(server, player, car, request);
            });
        });
        CommonPackets.registerReceiver(Automobility.rl("apply_vehicle_profile"), (server, player, buf) -> {
            final ProfileRequest request;
            try { request = decodeProfile(buf); } catch (RuntimeException ignored) { return; }
            int entity = request.entity();
            server.execute(() -> {
                if (!LIMIT.tryAcquire(player.getUUID(), server.getTickCount())) return;
                if (!(player.level().getEntity(entity) instanceof AutomobileEntity car) || !near(player,car)
                        || !car.isDriving(player) || !car.getVehicleAccess().mayConfigure(player.getUUID())) {
                    feedback(player,entity,"denied"); return;
                }
                // Validate the whole reviewed profile before changing any other preference.
                if (request.drivingMode() != null && !VehicleDrivingModeSupport.mayChange(car, request.drivingMode())) {
                    feedback(player, entity, "denied"); return;
                }
                car.setVehicleEngineSettings(new VehicleEngineSettings(request.automaticStart(), request.idleSeconds(), request.lightsWithEngine()));
                car.requestVehicleLightMode(request.lights());
                car.setVehicleAccess(car.getVehicleAccess().withFeedback(request.feedback()));
                car.setVehicleSignalAutoCancelEnabled(request.signalAutoCancel());
                car.setVehicleComfortSignalsEnabled(request.comfortSignals());
                car.setVehicleLockSignalConfirmationEnabled(request.lockSignalConfirmation());
                if (request.drivingMode() != null) car.setVehicleDrivingMode(request.drivingMode());
                feedback(player,entity,"profile_applied");
            });
        });
    }

    public record DrivingModeRequest(int entity, VehicleDrivingMode mode) {}

    public static DrivingModeRequest decodeDrivingMode(FriendlyByteBuf buf) {
        if (buf.readableBytes() != 5) throw new IllegalArgumentException("Driving mode packet size");
        int entity = buf.readInt(), mode = buf.readUnsignedByte();
        if (!VehicleDrivingMode.isValidId(mode)) throw new IllegalArgumentException("Invalid driving mode");
        return new DrivingModeRequest(entity, VehicleDrivingMode.fromId(mode));
    }

    /** Legacy layouts leave the vehicle mode unchanged. New 16-byte profiles append its stable ID. */
    public static ProfileRequest decodeProfile(FriendlyByteBuf buf) {
        int bytes = buf.readableBytes();
        if (bytes != 12 && bytes != 13 && bytes != 15 && bytes != 16) throw new IllegalArgumentException("Profile packet size");
        int entity = buf.readInt(), automatic = buf.readUnsignedByte(), idle = buf.readInt();
        int linked = buf.readUnsignedByte(), lights = buf.readUnsignedByte(), sound = buf.readUnsignedByte();
        int cancel = bytes >= 13 ? buf.readUnsignedByte() : 1;
        int comfort = bytes >= 15 ? buf.readUnsignedByte() : 1;
        int lockFlash = bytes >= 15 ? buf.readUnsignedByte() : 0;
        int driving = bytes == 16 ? buf.readUnsignedByte() : -1;
        if (automatic > 1 || linked > 1 || cancel > 1 || comfort > 1 || lockFlash > 1
                || !VehicleEngineSettings.validDelay(idle)
                || !VehicleLightMode.isValidId(lights) || sound > 3
                || (bytes == 16 && !VehicleDrivingMode.isValidId(driving)))
            throw new IllegalArgumentException("Invalid profile settings");
        return new ProfileRequest(entity, automatic == 1, idle, linked == 1, VehicleLightMode.fromId(lights),
                VehicleAccessState.Feedback.byId(sound), cancel == 1, comfort == 1, lockFlash == 1,
                bytes == 16 ? VehicleDrivingMode.fromId(driving) : null);
    }

    public static boolean near(ServerPlayer player, AutomobileEntity car) {
        return player.isAlive() && !car.isRemoved() && player.level()==car.level() && player.distanceToSqr(car)<=64;
    }

    public static boolean apply(MinecraftServer server, ServerPlayer player, AutomobileEntity car, Request request) {
        if (!near(player,car)) return false;
        var state=car.getVehicleAccess(); var id=player.getUUID(); var next=state;
        boolean flag=request.value()==1;
        try {
            switch (request.action()) {
                case CLAIM -> {
                    if (state.owner()!=null || (!car.getPassengers().isEmpty() && car.getFirstPassenger()!=player)) return denied(player,car);
                    next=state.claim(id);
                }
                case RELEASE -> {
                    if (!state.isOwner(id) || state.locked() || !car.getPassengers().isEmpty()) return denied(player,car);
                    next=VehicleAccessState.DEFAULT;
                }
                case LOCK -> {
                    if (state.owner()==null || !state.hasKey(id)) return denied(player,car);
                    next=state.withLocked(flag);
                }
                case PUBLIC_DRIVE -> {
                    if (!state.isOwner(id)) return denied(player,car);
                    next=state.withPublicDrive(flag);
                }
                case PUBLIC_CONFIGURE -> {
                    if (!state.isOwner(id) || (flag && !state.publicDrive())) return denied(player,car);
                    next=state.withPublicConfigure(flag);
                }
                case SHARED_CONFIGURE -> {
                    if (!state.isOwner(id)) return denied(player,car);
                    next=state.withSharedConfigure(flag);
                }
                case ADD_KEY -> {
                    if (!state.isOwner(id)) return denied(player,car);
                    UUID target; String name=request.target();
                    var online=server.getPlayerList().getPlayers().stream().filter(p -> p.getName().getString().equalsIgnoreCase(request.target())).findFirst();
                    if (online.isPresent()) { target=online.get().getUUID(); name=online.get().getName().getString(); }
                    else { target=UUID.fromString(request.target()); name=target.toString(); }
                    next=state.addKey(target,name);
                }
                case REMOVE_KEY -> {
                    if (!state.isOwner(id)) return denied(player,car);
                    next=state.removeKey(UUID.fromString(request.target()));
                }
                case FEEDBACK -> {
                    if (!state.mayConfigure(id)) return denied(player,car);
                    next=state.withFeedback(VehicleAccessState.Feedback.byId(request.value()));
                }
            }
        } catch (RuntimeException error) { feedback(player,car.getId(),"invalid_target"); return false; }
        if (next.equals(state)) return true;
        if (next.locked()!=state.locked() && !car.mayPlayLockFeedback()) return false;
        car.setVehicleAccess(next);
        if (next.locked()!=state.locked()) car.confirmVehicleLock(next.locked());
        feedback(player,car.getId(),next.locked()!=state.locked() ? (next.locked() ? "locked" : "unlocked") : "updated");
        return true;
    }
    private static boolean denied(ServerPlayer player,AutomobileEntity car) { feedback(player,car.getId(),"denied"); return false; }
    private static void feedback(ServerPlayer player,int entity,String key) {
        player.sendSystemMessage(Component.translatable("vehicle.automobility.access."+key),true);
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        buf.writeInt(entity); buf.writeUtf(key,32);
        Platform.get().serverSendPacket(player,Automobility.rl("vehicle_action_result"),buf);
    }
}
