package io.github.foundationgames.automobility.api.drivetrain.profile;

import io.github.foundationgames.automobility.automobile.drivetrain.profile.*;
import io.github.foundationgames.automobility.automobile.garage.GarageTransferAccess;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;

/** Integration point for the later unified Runtime/garage successor; no legacy fallback, inventory write or energy rebinding. */
public final class VehiclePhysicalProfileBridge015{
    private static final Map<ServerLevel,ProfileReloadGate>WORLDS=new IdentityHashMap<>();
    public record Bound(ProfileReloadGate.Snapshot authority,PhysicalDefinitions.Selection parts,PayloadMassModel.Result payload){
        public double vehicleMassKg(){return parts.emptyMassKg()+payload.knownSubtotalKg();}
    }
    private VehiclePhysicalProfileBridge015(){}
    /** Trusted startup supplies the same Core worldInstance UUID. A profile reload must use stage/apply instead. */
    public static void bind(ServerLevel world,UUID coreWorldInstance,PhysicalDefinitions initial){
        thread(world);var existing=WORLDS.get(world);if(existing!=null){if(!existing.active().worldSession().equals(coreWorldInstance))throw new IllegalStateException("world instance replacement requires explicit world unload");return;}
        WORLDS.put(world,new ProfileReloadGate(coreWorldInstance,initial));for(var player:world.players())publish(player);
    }
    public static void unload(ServerLevel world){thread(world);WORLDS.remove(world);}
    static void closeServer(MinecraftServer server){if(!server.isSameThread())throw new IllegalStateException("profile owner thread");WORLDS.keySet().removeIf(world->world.getServer()==server);}
    public static Optional<ProfileReloadGate.Snapshot>snapshot(ServerLevel world){thread(world);var gate=WORLDS.get(world);return gate==null?Optional.empty():Optional.of(gate.active());}
    public static boolean stage(ServerLevel world,ProfileJson.Read read){thread(world);var gate=WORLDS.get(world);return gate!=null&&gate.stage(read);}
    /** Caller enumerates the entire affected live-car set and derives mechanical rest from actual state on this same tick. */
    public static boolean apply(ServerLevel world,Collection<ProfileReloadGate.Boundary>completeAffectedCars){thread(world);var gate=WORLDS.get(world);if(gate==null||!gate.apply(completeAffectedCars))return false;for(var player:world.players())publish(player);return true;}
    public static Optional<Bound>server(AutomobileEntity car){
        if(!(car.level() instanceof ServerLevel level))return Optional.empty();thread(level);
        if(car.isRemoved()||level.getEntity(car.getUUID())!=car||GarageTransferAccess.blocked(car))return Optional.empty();var authority=snapshot(level).orElse(null);if(authority==null)return Optional.empty();
        var actual=car.garageComponents();if(actual==null||!actual.complete())return Optional.empty();String frame=actual.frame().unwrapKey().map(k->k.identifier().toString()).orElse(""),wheel=actual.wheel().unwrapKey().map(k->k.identifier().toString()).orElse(""),engine=actual.engine().unwrapKey().map(k->k.identifier().toString()).orElse("");
        if(frame.isEmpty()||wheel.isEmpty()||engine.isEmpty())return Optional.empty();var parts=ProfileViews.server(authority,frame,wheel,engine).orElse(null);if(parts==null)return Optional.empty();
        var observed=VehiclePayloadReader.capture(car).observation().orElse(null);if(observed==null)return Optional.empty();
        return Optional.of(new Bound(authority,parts,PayloadMassModel.evaluate(authority.definitions(),observed)));
    }
    /** Can preview an engine not mounted yet, against the same applied table as driving; includes no owner/storage write. */
    public static Optional<PhysicalDefinitions.Selection>garage(ServerLevel world,String frame,String wheel,String proposedEngine){return snapshot(world).flatMap(s->ProfileViews.server(s,frame,wheel,proposedEngine));}
    public static boolean publish(ServerPlayer player){return snapshot((ServerLevel)player.level()).map(s->VehicleProfileSync015Bootstrap.publish(player,s)).orElse(false);}
    private static void thread(ServerLevel level){Objects.requireNonNull(level);if(!level.getServer().isSameThread())throw new IllegalStateException("profile owner thread");}
}
