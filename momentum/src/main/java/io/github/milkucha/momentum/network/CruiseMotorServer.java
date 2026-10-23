package io.github.milkucha.momentum.network;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.automobile.driving.VehicleCruiseMotorLimit;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import io.github.milkucha.momentum.config.MomentumConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
/** Server-owned feature leases; they never author speed or ordinary input freshness. */
public final class CruiseMotorServer {
    private static final Map<UUID,Session> sessions=new HashMap<>();
    private static final class Session {
        final ServerPlayer actor;final Object connection;final CruiseMotorLease.SequenceGate gate=new CruiseMotorLease.SequenceGate();
        final CruiseMotorLease.ForwardOwnership forward=new CruiseMotorLease.ForwardOwnership();
        AutomobileEntity car;float target;long issued;int lastTick=-1,requests;
        Session(ServerPlayer actor){this.actor=actor;connection=actor.connection;}
    }
    private CruiseMotorServer(){}
    public static void initialize(){
        PayloadTypeRegistry.serverboundPlay().register(CruiseMotorRequest.TYPE,CruiseMotorRequest.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CruiseMotorGrant.TYPE,CruiseMotorGrant.CODEC);
        VehicleCruiseMotorLimit.install(car->car.level().isClientSide()?CruiseMotorPrediction.targetFor(car):targetFor(car));
        ServerPlayNetworking.registerGlobalReceiver(CruiseMotorRequest.TYPE,(packet,context)->{
            var actor=context.player();var connection=actor.connection;
            context.server().execute(()->{if(actor.connection!=connection||context.server().getPlayerList().getPlayer(actor.getUUID())!=actor)return;receive(actor,packet);});
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->remove(handler.player));
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{var old=sessions.get(handler.player.getUUID());if(old!=null&&old.actor!=handler.player)sessions.remove(handler.player.getUUID());});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->sessions.clear());
        ServerTickEvents.START_SERVER_TICK.register(server->{long now=System.nanoTime();for(var session:sessions.values()){
            if(session.car==null)continue;
            boolean expired=!CruiseMotorLease.fresh(session.issued,now,CruiseMotorLease.LEASE_MS);
            if(expired||!valid(session,session.car)){
                withdraw(session,false);
            }
        }});
    }
    private static void withdraw(Session s,boolean manualForward){
        var car=s.car;
        if(s.forward.withdraw(manualForward)&&car!=null&&owns(s,car)&&car.input.accelerating){car.input.accelerating=false;car.markDirty();}
        s.car=null;
    }
    private static void remove(ServerPlayer actor){var s=sessions.get(actor.getUUID());if(s!=null&&s.actor==actor)sessions.remove(actor.getUUID());}
    private static boolean current(ServerPlayer actor){return actor.connection!=null&&actor.connection.isAcceptingMessages()&&actor.level().getServer().getPlayerList().getPlayer(actor.getUUID())==actor;}
    private static boolean owns(Session s,AutomobileEntity c){return current(s.actor)&&s.actor.connection==s.connection&&!c.isRemoved()&&s.actor.level()==c.level()&&s.actor.getVehicle()==c&&c.isDriving(s.actor)&&c.getFirstPassenger()==s.actor;}
    private static boolean valid(Session s,AutomobileEntity c){
        var cfg=MomentumConfig.get();
        return owns(s,c)&&ServerPhysicsSessions.mayUseCruiseMotor(s.actor)&&cfg.cruise.enabled&&c.engineRunning()&&c.hasFreshAcceptedClientInput()&&!c.horizontalCollision
                &&Float.isFinite(s.target)&&s.target>=cfg.cruise.minActivationKmh&&s.target<=cfg.cruise.maxTargetKmh
                &&!(c instanceof SteeringDebugAccessor access&&access.momentum$getEngineSpeed()<0)
                &&!c.input.braking&&!c.rawBrakePedalHeld()&&!c.input.holdingDrift&&!c.burningOut()&&c.getBoostTimer()==0
                &&!ServerKeyState.getBrake(s.actor.getUUID())&&!ServerKeyState.getDrift(s.actor.getUUID());
    }
    private static void receive(ServerPlayer actor,CruiseMotorRequest p){
        if(!current(actor)||!ServerPlayNetworking.canSend(actor,CruiseMotorGrant.TYPE)||!PhysicsContract.accepts(p.revision())
                ||p.operation()<0||p.operation()>3||!Float.isFinite(p.targetKmh()))return;
        var s=sessions.get(actor.getUUID());if(s==null||s.actor!=actor||s.connection!=actor.connection){s=new Session(actor);sessions.put(actor.getUUID(),s);}
        if(!s.gate.accept(p.nonce(),p.sequence()))return;
        int tick=actor.level().getServer().getTickCount();if(tick!=s.lastTick){s.lastTick=tick;s.requests=0;}
        boolean accepted=false;
        if(p.operation()==CruiseMotorRequest.CANCEL||p.operation()==CruiseMotorRequest.MANUAL_FORWARD){withdraw(s,p.operation()==CruiseMotorRequest.MANUAL_FORWARD);}
        else if(++s.requests<=4&&actor.level().getEntity(p.entityId()) instanceof AutomobileEntity car&&car.getUUID().equals(p.entityUuid())){
            boolean renewal=p.operation()==CruiseMotorRequest.RENEW;
            boolean matchingRenewal=!renewal||s.car==car&&Float.floatToIntBits(s.target)==Float.floatToIntBits(p.targetKmh())&&CruiseMotorLease.fresh(s.issued,System.nanoTime(),CruiseMotorLease.LEASE_MS);
            if(!renewal&&s.car!=null)withdraw(s,false);
            s.target=p.targetKmh();
            if(matchingRenewal&&valid(s,car)){s.car=car;s.issued=System.nanoTime();if(renewal)s.forward.confirm();else s.forward.offer();accepted=true;}else withdraw(s,false);
        }else withdraw(s,false);
        ServerPlayNetworking.send(actor,new CruiseMotorGrant(p.nonce(),p.sequence(),p.entityId(),p.entityUuid(),actor.getUUID(),accepted,p.targetKmh(),accepted?CruiseMotorLease.LEASE_MS:0));
    }
    public static void cancelOnImpact(AutomobileEntity car,float speedKmh){
        if(speedKmh<MomentumConfig.get().cruise.impactCancelMinSpeedKmh)return;
        if(car.getFirstPassenger() instanceof ServerPlayer actor){var s=sessions.get(actor.getUUID());if(s!=null&&s.car==car)withdraw(s,false);}
    }
    private static double targetFor(AutomobileEntity car){
        if(!(car.getFirstPassenger() instanceof ServerPlayer actor))return Double.NaN;
        var s=sessions.get(actor.getUUID());
        return s!=null&&s.car==car&&valid(s,car)&&CruiseMotorLease.fresh(s.issued,System.nanoTime(),CruiseMotorLease.LEASE_MS)?s.target/72.0:Double.NaN;
    }
}
