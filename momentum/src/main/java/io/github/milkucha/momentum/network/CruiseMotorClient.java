package io.github.milkucha.momentum.network;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.*;
import io.github.milkucha.momentum.config.MomentumConfig;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;
import java.util.concurrent.ThreadLocalRandom;
/** All mutations run on the client thread, including integrated-server connections. */
public final class CruiseMotorClient {
    private static final CruiseMotorLease<AutomobileEntity> lease=new CruiseMotorLease<>();
    private static Object connection;private static AutomobileEntity requestedCar;private static boolean supported;private static long retryAfter;private static boolean continuous;private static float continuousTarget;private static AutomobileEntity forceSyncCar;
    private CruiseMotorClient(){}
    public static void initialize(){
        CruiseMotorPrediction.install(CruiseMotorClient::targetFor);
        ClientPlayConnectionEvents.INIT.register((handler,client)->{long nonce;do{nonce=ThreadLocalRandom.current().nextLong();}while(nonce==0);lease.begin(nonce);connection=handler;requestedCar=null;supported=false;retryAfter=0;continuous=false;forceSyncCar=null;});
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->supported=handler==connection&&ClientPlayNetworking.canSend(CruiseMotorRequest.TYPE));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{if(connection==handler){lease.end();connection=null;requestedCar=null;supported=false;continuous=false;forceSyncCar=null;}});
        ClientPlayNetworking.registerGlobalReceiver(CruiseMotorGrant.TYPE,(p,ctx)->{
            var actual=ctx.client().getConnection();
            ctx.client().execute(()->{
                var client=ctx.client();var car=requestedCar;
                if(actual==null||actual!=connection||client.getConnection()!=actual||car==null||client.level!=car.level()
                        ||client.player==null||!p.driverUuid().equals(client.player.getUUID())||!car.isDriving(client.player)
                        ||car.getId()!=p.entityId()||!car.getUUID().equals(p.entityUuid()))return;
                var pending=lease.pending();
                if(pending==null||p.nonce()!=lease.nonce()||p.sequence()!=pending.sequence()||pending.context()!=car)return;
                if(!lease.receive(p.nonce(),p.sequence(),car,p.targetKmh(),p.accepted(),p.durationMs(),System.nanoTime())&&p.accepted()==false)retryAfter=System.nanoTime()+500_000_000L;
            });
        });
    }
    private static boolean context(AutomobileEntity car){var c=Minecraft.getInstance();return supported&&connection==c.getConnection()&&c.player!=null&&c.level==car.level()&&c.player.getVehicle()==car&&car.isDriving(c.player)&&car.getFirstPassenger()==c.player;}
    public static boolean update(AutomobileEntity car,float target){
        if(!context(car)||car.getBoostTimer()>0){cancel();return false;}
        long now=System.nanoTime();
        if(requestedCar!=null&&(requestedCar!=car||continuous&&(!lease.active(car,now)
                ||Float.floatToIntBits(continuousTarget)!=Float.floatToIntBits(target))))cancel();
        requestedCar=car;
        if(!(lease.active(car,now)&&!continuous)&&(retryAfter==0||now-retryAfter>=0)&&lease.needsRequest(car,target,now)){
            var request=lease.request(car,target,now);
            if(request!=null)ClientPlayNetworking.send(new CruiseMotorRequest(PhysicsContract.REVISION,lease.nonce(),request.sequence(),request.renewal()?CruiseMotorRequest.RENEW:CruiseMotorRequest.SET,car.getId(),car.getUUID(),target));
        }
        boolean active=lease.active(car,now);
        if(active&&!continuous){
            // Sent before provideClientInput publishes enhanced forward. An offer alone never owns it.
            var activation=lease.request(car,target,now);
            if(activation==null)return false;
            ClientPlayNetworking.send(new CruiseMotorRequest(PhysicsContract.REVISION,lease.nonce(),activation.sequence(),CruiseMotorRequest.RENEW,car.getId(),car.getUUID(),target));
            continuous=true;continuousTarget=target;
        }
        return active;
    }
    public static void cancel(){withdraw(false);}
    public static void manualForward(){withdraw(true);}
    private static void withdraw(boolean manualForward){
        var car=requestedCar;if(car==null)return;
        if(continuous)forceSyncCar=car;
        long seq=lease.cancel();requestedCar=null;retryAfter=0;continuous=false;
        if(seq!=0&&supported&&connection==Minecraft.getInstance().getConnection()&&ClientPlayNetworking.canSend(CruiseMotorRequest.TYPE))
            ClientPlayNetworking.send(new CruiseMotorRequest(PhysicsContract.REVISION,lease.nonce(),seq,manualForward?CruiseMotorRequest.MANUAL_FORWARD:CruiseMotorRequest.CANCEL,car.getId(),car.getUUID(),0));
    }
    /** Semantic withdrawal must republish normal input even when its bits did not change. */
    public static boolean consumeInputResync(AutomobileEntity car){
        if(forceSyncCar!=car)return false;forceSyncCar=null;return true;
    }
    private static double targetFor(AutomobileEntity car){
        if(!continuous||requestedCar!=car||!context(car)||!MomentumCruiseControl.isActiveFor(car)||!MomentumConfig.gameplay().cruise.enabled
                ||!car.engineRunning()||MomentumBrakeState.rawBrakeHeld||MomentumBrakeState.brakeHeld||MomentumDriftState.driftKeyHeld
                ||car.input.braking||car.rawBrakePedalHeld()||car.input.holdingDrift||car.burningOut()||car.getBoostTimer()>0)return Double.NaN;
        return lease.target(car,System.nanoTime())/72.0;
    }
}
