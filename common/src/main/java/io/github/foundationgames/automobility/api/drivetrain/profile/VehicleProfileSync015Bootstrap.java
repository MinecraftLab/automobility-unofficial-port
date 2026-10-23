package io.github.foundationgames.automobility.api.drivetrain.profile;

import io.github.foundationgames.automobility.automobile.drivetrain.profile.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;

/** Isolated successor hook: root publishes the actually applied authoritative snapshot after stationary reload. */
public final class VehicleProfileSync015Bootstrap implements ModInitializer{
    private record Published(ServerPlayer player,Object connection,ServerLevel world,ProfileReloadGate.Snapshot snapshot,List<byte[]>chunks){}
    private static final Map<MinecraftServer,IdentityHashMap<Object,Published>>PUBLISHED=new IdentityHashMap<>();
    private static final ConcurrentHashMap<Object,AtomicBoolean>QUEUED=new ConcurrentHashMap<>();
    private static final Map<Object,Rate>RATES=new IdentityHashMap<>();
    private static final Map<MinecraftServer,Set<Object>>CONNECTIONS=new IdentityHashMap<>();
    private static final class Rate{long tick;int requests;}
    @Override public void onInitialize(){
        PayloadTypeRegistry.clientboundPlay().register(ProfileDefinitionPayloads015.Announce.TYPE,ProfileDefinitionPayloads015.Announce.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ProfileDefinitionPayloads015.Chunk.TYPE,ProfileDefinitionPayloads015.Chunk.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ProfileDefinitionPayloads015.Request.TYPE,ProfileDefinitionPayloads015.Request.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{QUEUED.put(handler,new AtomicBoolean());CONNECTIONS.computeIfAbsent(server,key->Collections.newSetFromMap(new IdentityHashMap<>())).add(handler);VehiclePhysicalProfileBridge015.publish(handler.getPlayer());});
        ClientboundPlayChannelEvents.REGISTER.register((handler,sender,server,channels)->{if(channels.contains(ProfileDefinitionPayloads015.Announce.TYPE.id())||channels.contains(ProfileDefinitionPayloads015.Chunk.TYPE.id()))server.execute(()->VehiclePhysicalProfileBridge015.publish(handler.getPlayer()));});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{QUEUED.remove(handler);RATES.remove(handler);var map=PUBLISHED.get(server);if(map!=null)map.remove(handler);var joined=CONNECTIONS.get(server);if(joined!=null)joined.remove(handler);});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{PUBLISHED.remove(server);VehiclePhysicalProfileBridge015.closeServer(server);var joined=CONNECTIONS.remove(server);if(joined!=null)for(Object connection:joined){QUEUED.remove(connection);RATES.remove(connection);}});
        ServerPlayNetworking.registerGlobalReceiver(ProfileDefinitionPayloads015.Request.TYPE,(payload,context)->{
            var player=context.player();var connection=player.connection;var queued=QUEUED.get(connection);if(queued==null||!queued.compareAndSet(false,true))return;
            try{context.server().execute(()->{try{if(player.connection==connection)request(context.server(),player,payload);}finally{queued.set(false);}});}catch(RuntimeException stopped){queued.set(false);}
        });
    }
    /** Caller must use this same snapshot for garage/statistics and mechanics; does not bind or reset any energy participant. */
    public static boolean publish(ServerPlayer player,ProfileReloadGate.Snapshot snapshot){
        var server=player.level().getServer();if(server==null||!server.isSameThread()||server.getPlayerList().getPlayer(player.getUUID())!=player||player.connection==null||!ServerPlayNetworking.canSend(player,ProfileDefinitionPayloads015.Announce.TYPE)||!ServerPlayNetworking.canSend(player,ProfileDefinitionPayloads015.Chunk.TYPE))return false;
        var map=PUBLISHED.computeIfAbsent(server,key->new IdentityHashMap<>());var previous=map.get(player.connection);
        var chunks=previous!=null&&previous.snapshot()==snapshot?previous.chunks():snapshot.chunks();
        var published=new Published(player,player.connection,(ServerLevel)player.level(),snapshot,chunks);map.put(player.connection,published);
        ServerPlayNetworking.send(player,new ProfileDefinitionPayloads015.Announce(snapshot.offer()));return true;
    }
    private static void request(MinecraftServer server,ServerPlayer player,ProfileDefinitionPayloads015.Request request){
        var map=PUBLISHED.get(server);var published=map==null?null:map.get(player.connection);
        if(!server.isSameThread()||published==null||published.player()!=player||published.connection()!=player.connection||published.world()!=player.level()||server.getPlayerList().getPlayer(player.getUUID())!=player)return;
        var s=published.snapshot();var r=request.reference();if(!s.worldSession().equals(r.worldSession())||s.epoch()!=r.epoch()||!s.sha256().equals(r.sha256())||request.index()>=published.chunks().size())return;
        long now=server.getTickCount();var rate=RATES.computeIfAbsent(player.connection,key->new Rate());if(rate.tick!=now){rate.tick=now;rate.requests=0;}if(++rate.requests>4)return;
        if(ServerPlayNetworking.canSend(player,ProfileDefinitionPayloads015.Chunk.TYPE))ServerPlayNetworking.send(player,new ProfileDefinitionPayloads015.Chunk(r,request.index(),published.chunks().get(request.index())));
    }
}
