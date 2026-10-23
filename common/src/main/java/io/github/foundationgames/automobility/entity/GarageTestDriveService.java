package io.github.foundationgames.automobility.entity;

import io.github.foundationgames.automobility.block.AutomobilityBlocks;
import io.github.foundationgames.automobility.util.network.GarageTestDrivePackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/** Ephemeral server-owned workshop trips. No car save/restore, cargo access or part replacement. */
public final class GarageTestDriveService {
    public enum Reason {
        READY, STARTED, RETURNED, NO_SESSION, INVALID_SESSION, EXPIRED, OUT_OF_RANGE, WRONG_DIMENSION,
        OWNER_REQUIRED, DRIVER_REQUIRED, OCCUPIED, MOVING, NOT_GROUNDED, ORIGIN_MISSING, UNLOADED,
        PROTECTED, BLOCKED, BUSY, MOUNT_FAILED, RETURN_FAILED, ENGINE_RUNNING, REOPEN_FAILED;
        public String key() { return "garage.automobility.test_drive.reason."+name().toLowerCase(Locale.ROOT); }
    }
    public record Session(UUID nonce,UUID player,UUID vehicle,Identifier dimension,BlockPos origin,Vec3 originPlayer,
                          long started,boolean requireAssembler) {
        public Session { origin=origin.immutable(); }
    }
    @FunctionalInterface public interface MenuOpener { boolean open(ServerPlayer player,BlockPos origin,UUID vehicle,boolean resumeTuning); }
    private static final UUID EMPTY=new UUID(0,0);
    private static final Map<MinecraftServer,Map<UUID,Bound>> SESSIONS=new WeakHashMap<>();
    private static final Map<MinecraftServer,Map<UUID,RequestBudget>> REQUESTS=new WeakHashMap<>();
    private static BiConsumer<ServerPlayer,GarageTestDrivePackets.State> sender=(p,s)->{};
    private static MenuOpener opener=(p,o,v,t)->false;
    private static final class Bound {
        final Session session; final Object connection;
        boolean returning;
        Bound(Session session,Object connection) { this.session=session; this.connection=connection; }
    }
    private record RequestBudget(Object connection,long tick) {}
    private GarageTestDriveService() {}
    public static void setStateSender(BiConsumer<ServerPlayer,GarageTestDrivePackets.State> callback) { sender=Objects.requireNonNull(callback); }
    public static void setMenuOpener(MenuOpener callback) { opener=Objects.requireNonNull(callback); }
    private static Map<UUID,Bound> sessions(MinecraftServer server) { return SESSIONS.computeIfAbsent(server,s->new HashMap<>()); }
    private static Bound bound(ServerPlayer player) {
        var value=sessions(player.level().getServer()).get(player.getUUID());
        return value!=null&&value.connection==player.connection?value:null;
    }
    public static Session session(ServerPlayer player) { var value=bound(player); return value==null?null:value.session; }
    public static GarageTestDrivePackets.State state(ServerPlayer player) {
        var value=bound(player); return packet(player,value==null?null:value.session,Reason.READY,value!=null);
    }
    private static GarageTestDrivePackets.State packet(ServerPlayer player,Session session,Reason reason,boolean active) {
        return new GarageTestDrivePackets.State(active,session==null?EMPTY:session.nonce(),session==null?EMPTY:session.vehicle(),
                session==null?player.level().dimension().identifier():session.dimension(),reason);
    }
    private static void send(ServerPlayer player,Session session,Reason reason,boolean active) {
        try { sender.accept(player,packet(player,session,reason,active)); } catch(RuntimeException ignored) { /* Network failure must not roll back a committed world action. */ }
    }
    private static Reason reject(ServerPlayer player,Bound value,Reason reason) { send(player,value==null?null:value.session,reason,value!=null); return reason; }
    public static Reason start(ServerPlayer player,AutomobileEntity car,BlockPos origin) {
        var level=(ServerLevel)player.level(); var server=level.getServer();
        if(!server.isSameThread()) return Reason.BUSY;
        var previous=bound(player);
        if(!player.isAlive()||player.isSpectator()||!player.mayBuild()||car==null||!car.isAlive()||car.level()!=level
                ||car.isDecorative()||(!player.isCreative()&&car.isInvulnerable())) return reject(player,previous,Reason.PROTECTED);
        if(!car.getVehicleAccess().isOwner(player.getUUID())) return reject(player,previous,Reason.OWNER_REQUIRED);
        if(player.isPassenger()||car.isPassenger()||!car.getPassengers().isEmpty()) return reject(player,previous,Reason.OCCUPIED);
        if(player.distanceToSqr(Vec3.atCenterOf(origin))>64||car.distanceToSqr(Vec3.atCenterOf(origin))>36
                ||player.distanceToSqr(car)>64) return reject(player,previous,Reason.OUT_OF_RANGE);
        var originReason=originReason(player,origin,!player.isCreative());
        if(originReason!=Reason.READY) return reject(player,previous,originReason);
        if(!level.hasChunkAt(car.blockPosition())||!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(car.blockPosition().getX()>>4,car.blockPosition().getZ()>>4)))
            return reject(player,previous,Reason.UNLOADED);
        if(!level.mayInteract(player,car.blockPosition())||!player.containerMenu.getCarried().isEmpty()) return reject(player,previous,Reason.PROTECTED);
        if(car.engineRunning()) return reject(player,previous,Reason.ENGINE_RUNNING);
        // Deploy/assemble have already checked placement, but this new entity has not ticked its ground flag.
        boolean grounded=car.automobileOnGround()||GarageReturnPlacement.supported(car);
        if(!GarageTestDrivePolicy.stopped(car.getHSpeed(),car.getVSpeed(),car.getDeltaMovement().lengthSqr(),grounded))
            return reject(player,previous,grounded?Reason.MOVING:Reason.NOT_GROUNDED);
        var session=new Session(UUID.randomUUID(),player.getUUID(),car.getUUID(),level.dimension().identifier(),origin,player.position(),level.getGameTime(),!player.isCreative());
        player.closeContainer();
        if(!player.startRiding(car)||player.getVehicle()!=car||!car.isDriving(player)) {
            if(player.getVehicle()==car) player.stopRiding();
            send(player,previous==null?session:previous.session,Reason.MOUNT_FAILED,previous!=null); reopen(player,session,false); return Reason.MOUNT_FAILED;
        }
        sessions(server).put(player.getUUID(),new Bound(session,player.connection));
        send(player,session,Reason.STARTED,true); return Reason.STARTED;
    }
    public static Reason returnToGarage(ServerPlayer player,UUID nonce) {
        var level=(ServerLevel)player.level(); var server=level.getServer();
        if(!server.isSameThread()) return Reason.BUSY;
        long now=level.getGameTime();
        var budgets=REQUESTS.computeIfAbsent(server,s->new HashMap<>()); var budget=budgets.get(player.getUUID());
        if(budget!=null&&budget.connection()==player.connection&&now>=budget.tick()&&now-budget.tick()<10) return Reason.BUSY;
        budgets.put(player.getUUID(),new RequestBudget(player.connection,now));
        var value=bound(player);
        if(value==null) {
            try { sender.accept(player,new GarageTestDrivePackets.State(false,nonce,EMPTY,level.dimension().identifier(),Reason.NO_SESSION)); } catch(RuntimeException ignored) {}
            return Reason.NO_SESSION;
        }
        // Validate cheap replay/nonce/rate guards before any entity lookup or geometry search.
        if(value.returning) return reject(player,value,Reason.BUSY);
        if(!value.session.nonce().equals(nonce)) return reject(player,value,Reason.INVALID_SESSION);
        var session=value.session;
        if(!GarageTestDrivePolicy.withinLifetime(session.started(),now)) { sessions(server).remove(player.getUUID()); send(player,session,Reason.EXPIRED,false); return Reason.EXPIRED; }
        if(!level.dimension().identifier().equals(session.dimension())) return reject(player,value,Reason.WRONG_DIMENSION);
        if(!player.isAlive()||player.isSpectator()||!player.mayBuild()||!player.containerMenu.getCarried().isEmpty()
                ||player.containerMenu!=player.inventoryMenu) return reject(player,value,Reason.PROTECTED);
        if(!(level.getEntity(session.vehicle()) instanceof AutomobileEntity car)||!car.isAlive()||car.isDecorative()) return reject(player,value,Reason.INVALID_SESSION);
        if(!car.getVehicleAccess().isOwner(player.getUUID())||(!player.isCreative()&&car.isInvulnerable())) return reject(player,value,Reason.OWNER_REQUIRED);
        if(!level.mayInteract(player,car.blockPosition())||!level.mayInteract(player,player.blockPosition())) return reject(player,value,Reason.PROTECTED);
        if(player.getVehicle()!=car||!car.isDriving(player)||car.getControllingPassenger()!=player) return reject(player,value,Reason.DRIVER_REQUIRED);
        if(!GarageTestDrivePolicy.soleDriver(true,true,car.getPassengers().size(),car.isPassenger())) return reject(player,value,Reason.OCCUPIED);
        if(!GarageTestDrivePolicy.withinRadius(car.distanceToSqr(Vec3.atCenterOf(session.origin())))) return reject(player,value,Reason.OUT_OF_RANGE);
        if(!GarageTestDrivePolicy.stopped(car.getHSpeed(),car.getVSpeed(),car.getDeltaMovement().lengthSqr(),car.automobileOnGround()))
            return reject(player,value,car.automobileOnGround()?Reason.MOVING:Reason.NOT_GROUNDED);
        var originReason=originReason(player,session.origin(),session.requireAssembler()||!player.isCreative());
        if(originReason!=Reason.READY) return reject(player,value,originReason);
        var destination=GarageReturnPlacement.find(player,car,session.origin(),session.originPlayer());
        if(destination.isEmpty()) return reject(player,value,Reason.BLOCKED);
        value.returning=true;
        var target=destination.get(); var oldCar=car.position(); var oldPlayer=player.position();
        float oldYaw=car.getYRot(),oldPitch=car.getXRot(),oldPlayerYaw=player.getYRot(),oldPlayerPitch=player.getXRot();
        var oldMotion=car.getDeltaMovement();
        var result=GarageTestDriveReturnTransaction.move(
                ()->{
                    player.stopRiding();
                    return !player.isPassenger()&&car.teleportTo(level,target.car().x,target.car().y,target.car().z,Set.of(),target.yaw(),0,false);
                },
                ()->player.teleportTo(level,target.player().x,target.player().y,target.player().z,Set.of(),target.yaw(),oldPlayerPitch,false),
                ()->car.teleportTo(level,oldCar.x,oldCar.y,oldCar.z,Set.of(),oldYaw,oldPitch,false),
                ()->player.teleportTo(level,oldPlayer.x,oldPlayer.y,oldPlayer.z,Set.of(),oldPlayerYaw,oldPlayerPitch,false));
        if(!result.committed()) {
            car.setDeltaMovement(oldMotion); value.returning=false;
            if(result.restored()) player.startRiding(car);
            return reject(player,value,Reason.RETURN_FAILED);
        }
        // Successful return uses the same instance. Ignition shutdown is an explicit workshop action.
        car.requestIgnition(false); car.setDeltaMovement(Vec3.ZERO); car.setOnGround(true);
        sessions(server).remove(player.getUUID());
        var reason=reopen(player,session,true)?Reason.RETURNED:Reason.REOPEN_FAILED;
        send(player,session,reason,false); return reason;
    }
    private static Reason originReason(ServerPlayer player,BlockPos origin,boolean requireAssembler) {
        var level=(ServerLevel)player.level();
        if(!level.hasChunkAt(origin)||!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(origin.getX()>>4,origin.getZ()>>4))) return Reason.UNLOADED;
        var state=level.getBlockState(origin);
        if(!state.is(AutomobilityBlocks.AUTOMOBILE_ASSEMBLER.require())&&(requireAssembler||!state.is(AutomobilityBlocks.GARAGE.require()))) return Reason.ORIGIN_MISSING;
        return level.mayInteract(player,origin)?Reason.READY:Reason.PROTECTED;
    }
    private static boolean reopen(ServerPlayer player,Session session,boolean resume) {
        try { if(opener.open(player,session.origin(),session.vehicle(),resume)) return true; } catch(RuntimeException ignored) {}
        player.sendOverlayMessage(Component.translatable(Reason.REOPEN_FAILED.key())); return false;
    }
    public static void disconnect(ServerPlayer player) {
        var server=player.level().getServer(); var current=bound(player);
        if(current!=null) sessions(server).remove(player.getUUID(),current);
        var budgets=REQUESTS.get(server); if(budgets!=null) { var value=budgets.get(player.getUUID()); if(value!=null&&value.connection()==player.connection) budgets.remove(player.getUUID()); }
    }
    public static void stop(MinecraftServer server) { SESSIONS.remove(server); REQUESTS.remove(server); }
    public static void tick(MinecraftServer server) {
        if(server.getTickCount()%20!=0) return;
        var budgets=REQUESTS.get(server);
        if(budgets!=null) budgets.entrySet().removeIf(e->{ var player=server.getPlayerList().getPlayer(e.getKey()); return player==null||player.connection!=e.getValue().connection(); });
        var values=sessions(server); var iterator=values.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next(); var value=entry.getValue(); var player=server.getPlayerList().getPlayer(entry.getKey());
            if(player==null||player.connection!=value.connection) { iterator.remove(); continue; }
            var level=(ServerLevel)player.level(); Reason invalid=null;
            if(!player.isAlive()||player.isSpectator()) invalid=Reason.INVALID_SESSION;
            else if(!level.dimension().identifier().equals(value.session.dimension())) invalid=Reason.WRONG_DIMENSION;
            else if(!GarageTestDrivePolicy.withinLifetime(value.session.started(),level.getGameTime())) invalid=Reason.EXPIRED;
            else if(!(level.getEntity(value.session.vehicle()) instanceof AutomobileEntity car)||!car.isAlive()) invalid=Reason.INVALID_SESSION;
            else if(!car.getVehicleAccess().isOwner(player.getUUID())) invalid=Reason.OWNER_REQUIRED;
            if(invalid!=null) { iterator.remove(); send(player,value.session,invalid,false); }
        }
    }
}
