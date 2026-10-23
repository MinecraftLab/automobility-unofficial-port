package io.github.foundationgames.automobility.api.dashboard;

import io.github.foundationgames.automobility.api.dashboard.CabinPayloads022.*;
import io.github.foundationgames.automobility.automobile.garage.GarageTransferAccess;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.lang.ref.WeakReference;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.Minecraft;

/** Bounded exact-car accepted settings cache. Rendering observes; only a client tick/query or explicit UI sends metadata intent. */
public final class CabinSettingsClient022 implements ClientModInitializer {
    private static Object connection,sender,level;private static UUID session;private static long sequence,tick;
    private static final Map<UUID,Entry>CARS=new LinkedHashMap<>(32,.75f,true);
    private static final class Entry{final WeakReference<AutomobileEntity>car;State state;long observedAt,acceptedAt=-1,sentAt=-100,pending;Entry(AutomobileEntity car){this.car=new WeakReference<>(car);}}
    @Override public void onInitializeClient(){
        ClientPlayConnectionEvents.JOIN.register((h,s,c)->{reset();connection=h;sender=s;level=c.level;});
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{if(connection==h)reset();});
        ClientPlayNetworking.registerGlobalReceiver(Capabilities.TYPE,(p,ctx)->{Object h=ctx.client().getConnection(),s=ctx.responseSender();ctx.client().execute(()->{if(h!=connection||s!=sender||h!=ctx.client().getConnection())return;if(!p.session().equals(session)){session=p.session();sequence=0;CARS.clear();}});});
        ClientPlayNetworking.registerGlobalReceiver(State.TYPE,(p,ctx)->{Object h=ctx.client().getConnection(),s=ctx.responseSender(),world=ctx.client().level;ctx.client().execute(()->{
            if(h!=connection||s!=sender||h!=ctx.client().getConnection()||world!=ctx.client().level||!context()||!Objects.equals(p.session(),session))return;
            var entry=CARS.get(p.target().car());var car=entry==null?null:entry.car.get();
            if(car==null||!actual(car)||!target(car).equals(p.target())||entry.pending!=p.sequence())return;
            entry.pending=0;entry.state=p;entry.acceptedAt=tick;
        });});
        ClientTickEvents.END_CLIENT_TICK.register(client->{if(!context())return;if(tick==Long.MAX_VALUE){reset();return;}tick++;
            CARS.entrySet().removeIf(e->e.getValue().car.get()==null||!actual(e.getValue().car.get())||tick-e.getValue().observedAt>60);
            if(tick%5!=0)return;int sent=0;
            for(var entry:List.copyOf(CARS.values()))if(sent<4&&tick-entry.sentAt>=40&&send(entry,Action.QUERY,null))sent++;
        });
    }
    private static boolean context(){var c=Minecraft.getInstance();if(connection==null||connection!=c.getConnection()){if(connection!=null)reset();return false;}if(level!=c.level){level=c.level;CARS.clear();}return level!=null&&c.player!=null;}
    private static boolean actual(AutomobileEntity car){var c=Minecraft.getInstance();return car!=null&&!car.isRemoved()&&c.level!=null&&car.level()==c.level&&c.level.getEntity(car.getId())==car&&c.player!=null&&c.player.distanceToSqr(car)<=24*24;}
    public static Target target(AutomobileEntity car){return new Target(car.level().dimension().identifier().toString(),car.getUUID(),car.getId());}
    public static boolean capable(){return context()&&session!=null&&ClientPlayNetworking.canSend(Control.TYPE);}
    /** No packet send, source start, consent or state mutation outside this bounded cache. */
    public static void observe(AutomobileEntity car){if(!context()||!actual(car))return;var e=CARS.get(car.getUUID());if(e==null||e.car.get()!=car){e=new Entry(car);CARS.put(car.getUUID(),e);}e.observedAt=tick;while(CARS.size()>128)CARS.remove(CARS.keySet().iterator().next());}
    public static Optional<State>view(AutomobileEntity car){if(car==null)return Optional.empty();observe(car);var e=CARS.get(car.getUUID());return e==null||e.car.get()!=car||e.state==null||tick<e.acceptedAt||tick-e.acceptedAt>60?Optional.empty():Optional.of(e.state);}
    public static Optional<CabinPreferences022.Settings>preferences(AutomobileEntity car){return view(car).flatMap(State::preferences);}
    public static boolean pending(AutomobileEntity car){if(car==null)return false;var e=CARS.get(car.getUUID());return e!=null&&e.car.get()==car&&e.pending!=0&&tick-e.sentAt<40;}
    public static boolean query(AutomobileEntity car){if(car==null)return false;observe(car);var e=CARS.get(car.getUUID());return e!=null&&e.car.get()==car&&send(e,Action.QUERY,null);}
    public static boolean save(AutomobileEntity car,CabinPreferences022.Settings requested){
        if(car==null)return false;var c=Minecraft.getInstance();var state=view(car).orElse(null);var e=CARS.get(car.getUUID());
        return state!=null&&state.mayConfigure()&&c.player!=null&&c.player.getVehicle()==car&&!GarageTransferAccess.blocked(car)&&e!=null&&send(e,Action.SAVE,requested);
    }
    private static boolean send(Entry e,Action action,CabinPreferences022.Settings proposal){
        var car=e.car.get();if(!capable()||!actual(car)||sequence==Long.MAX_VALUE||e.pending!=0&&tick-e.sentAt<40)return false;
        var current=e.state;UUID incarnation=current==null?new UUID(0,0):current.incarnation();long revision=current==null?0:current.preferences().map(CabinPreferences022.Settings::revision).orElse(0L);
        if(action==Action.SAVE&&(current==null||proposal==null||!current.mayConfigure()||proposal.revision()!=revision+1))return false;
        long next=++sequence;e.pending=next;e.sentAt=tick;
        try{ClientPlayNetworking.send(new Control(session,next,target(car),incarnation,revision,action,proposal==null?new byte[0]:CabinPreferences022.encode(proposal)));return true;}
        catch(RuntimeException unavailable){e.pending=0;return false;}
    }
    private static void reset(){connection=sender=level=null;session=null;sequence=tick=0;CARS.clear();}
}
