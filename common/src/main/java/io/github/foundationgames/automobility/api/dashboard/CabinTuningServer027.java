package io.github.foundationgames.automobility.api.dashboard;

import io.github.foundationgames.automobility.api.dashboard.CabinTuningPayloads027.*;
import io.github.foundationgames.automobility.automobile.garage.GarageTransferAccess;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;

/** Actual server owner of the single cosmetic car tag; it has no media/player/mechanical dependency. */
public final class CabinTuningServer027 implements ModInitializer {
    private static final Map<MinecraftServer,Map<Object,Session>>SERVERS=new IdentityHashMap<>();
    private static final ConcurrentHashMap<Object,AtomicInteger>QUEUED=new ConcurrentHashMap<>();
    private static final class Session{final UUID id=UUID.randomUUID();final ServerPlayer player;final Object connection;long sequence,tick=Long.MIN_VALUE;int requests;Session(ServerPlayer p){player=p;connection=p.connection;}}
    @Override public void onInitialize(){
        PayloadTypeRegistry.serverboundPlay().register(Control.TYPE,Control.CODEC);PayloadTypeRegistry.clientboundPlay().register(Capabilities.TYPE,Capabilities.CODEC);PayloadTypeRegistry.clientboundPlay().register(State.TYPE,State.CODEC);
        ServerPlayConnectionEvents.JOIN.register((h,s,server)->session(h.getPlayer()));
        ClientboundPlayChannelEvents.REGISTER.register((h,s,server,channels)->{if(channels.contains(Capabilities.TYPE.id())||channels.contains(State.TYPE.id()))server.execute(()->session(h.getPlayer()));});
        ServerPlayConnectionEvents.DISCONNECT.register((h,server)->{var map=SERVERS.get(server);if(map!=null)map.remove(h);QUEUED.remove(h);});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{var map=SERVERS.remove(server);if(map!=null)for(Object connection:map.keySet())QUEUED.remove(connection);});
        ServerPlayNetworking.registerGlobalReceiver(Control.TYPE,(request,context)->{
            var actor=context.player();Object connection=actor.connection;var queued=QUEUED.computeIfAbsent(connection,k->new AtomicInteger());
            if(queued.incrementAndGet()>8){queued.decrementAndGet();return;}
            var released=new AtomicBoolean();Runnable release=()->{if(released.compareAndSet(false,true))queued.decrementAndGet();};
            try{context.server().execute(()->{try{if(actor.connection==connection&&context.server().getPlayerList().getPlayer(actor.getUUID())==actor)control(actor,request);}finally{release.run();}});}
            catch(RuntimeException stopped){release.run();}
        });
    }
    private static Session session(ServerPlayer player){
        var server=player.level().getServer();if(server==null||!server.isSameThread()||server.getPlayerList().getPlayer(player.getUUID())!=player||player.connection==null||!ServerPlayNetworking.canSend(player,Capabilities.TYPE)||!ServerPlayNetworking.canSend(player,State.TYPE))return null;
        var map=SERVERS.computeIfAbsent(server,k->new IdentityHashMap<>());var old=map.get(player.connection);
        if(old==null||old.player!=player){old=new Session(player);map.put(player.connection,old);ServerPlayNetworking.send(player,new Capabilities(old.id));}return old;
    }
    private static AutomobileEntity resolve(ServerPlayer actor,Target target){
        if(!actor.isAlive()||actor.isSpectator()||!target.dimension().equals(actor.level().dimension().identifier().toString()))return null;
        var found=actor.level().getEntity(target.entityId());return found instanceof AutomobileEntity car&&car.getUUID().equals(target.car())&&!car.isRemoved()
                &&car.level()==actor.level()&&actor.distanceToSqr(car)<=24*24&&(Object)car instanceof PersistentCabinTuning027?car:null;
    }
    public static boolean mayConfigure(ServerPlayer actor,AutomobileEntity car){return car.level() instanceof ServerLevel level&&level.getServer().isSameThread()
            &&level.getEntity(car.getUUID())==car&&!car.isRemoved()&&car.isAlive()&&actor.level()==level&&actor.isAlive()&&!actor.isSpectator()
            &&level.getServer().getPlayerList().getPlayer(actor.getUUID())==actor&&actor.connection!=null&&configurationContext(actor,car)
            &&car.getVehicleAccess().mayConfigure(actor.getUUID())&&!GarageTransferAccess.blocked(car);}
    private static boolean configurationContext(ServerPlayer actor,AutomobileEntity car){
        if(actor.getVehicle()==car)return true;
        return actor.containerMenu instanceof io.github.foundationgames.automobility.screen.GarageScreenHandler menu
                &&menu.stillValid(actor)&&!menu.isCatalog()&&!menu.isStored()&&!menu.hasPendingChanges()
                &&menu.getCarried().isEmpty()&&menu.selectedVehicle()==car&&car.getUUID().equals(menu.selectedChoiceId())
                &&(Object)menu instanceof GarageCabinTuningAccess027 access
                &&access.cabinTuning$accessReason(car)==io.github.foundationgames.automobility.entity.GarageService.Status.READY
                &&actor.distanceToSqr(car)<=64&&actor.level().mayInteract(actor,car.blockPosition())&&!car.isDecorative()
                &&(!car.isInvulnerable()||actor.isCreative());
    }
    private static void control(ServerPlayer actor,Control request){
        var session=session(actor);if(session==null||!session.id.equals(request.session())||request.sequence()<=session.sequence)return;session.sequence=request.sequence();
        long now=actor.level().getGameTime();if(now!=session.tick){session.tick=now;session.requests=0;}if(++session.requests>8)return;
        var car=resolve(actor,request.target());if(car==null)return;var access=(PersistentCabinTuning027)(Object)car;var current=access.cabinTuning$read();Result result=Result.READY;
        if(request.action()==Action.SAVE){
            var proposal=CabinTuning027.read(request.proposal()).settings().orElse(null);
            if(!mayConfigure(actor,car))result=Result.DENIED;
            else if(!access.cabinTuning$incarnation().equals(request.incarnation())||proposal==null||!CabinTuning027.accepts(current,request.revision(),proposal))result=Result.STALE;
            else{var permit=new Permit(actor,car,access.cabinTuning$raw(),access.cabinTuning$incarnation(),request.revision(),now);try{access.cabinTuning$replace(proposal,permit);result=Result.CHANGED;}catch(IllegalStateException changed){result=Result.STALE;}}
        }
        var after=access.cabinTuning$read();byte[]settings=after.settings().map(CabinTuning027::encode).orElse(new byte[0]);
        ServerPlayNetworking.send(actor,new State(session.id,request.sequence(),request.target(),access.cabinTuning$incarnation(),after.status(),settings,mayConfigure(actor,car)&&after.writable(),result));
    }
    /** Unforgeable one-use mutation scope; raw Tag CAS and permission/context are checked again immediately before installation. */
    public static final class Permit{
        private final ServerPlayer actor;private final AutomobileEntity car;private final Object world,connection,menu;private final Tag raw;private final UUID incarnation;private final long revision,tick;private boolean used;
        private Permit(ServerPlayer actor,AutomobileEntity car,Tag raw,UUID incarnation,long revision,long tick){this.actor=actor;this.car=car;world=car.level();connection=actor.connection;menu=actor.containerMenu;this.raw=raw;this.incarnation=incarnation;this.revision=revision;this.tick=tick;}
        public void require(AutomobileEntity actual,CabinTuning027.Settings proposal){
            if(used||actual!=car||car.level()!=world||actor.connection!=connection||actor.containerMenu!=menu||car.level().getGameTime()!=tick||!mayConfigure(actor,car))throw new IllegalStateException("stale cabin mutation");
            var access=(PersistentCabinTuning027)(Object)car;
            if(!incarnation.equals(access.cabinTuning$incarnation())||!Objects.equals(raw,access.cabinTuning$raw())||!CabinTuning027.accepts(access.cabinTuning$read(),revision,proposal))throw new IllegalStateException("cabin raw/revision changed");used=true;
        }
    }
}
