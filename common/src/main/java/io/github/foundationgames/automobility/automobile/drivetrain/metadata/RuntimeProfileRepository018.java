package io.github.foundationgames.automobility.automobile.drivetrain.metadata;

import io.github.foundationgames.automobility.api.drivetrain.profile.*;
import io.github.foundationgames.automobility.automobile.drivetrain.VehicleDrivetrainRuntime;
import io.github.foundationgames.automobility.automobile.drivetrain.profile.*;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;

/** Real-world repository lifecycle, supplied with the existing Core world UUID. Reload never resets mechanics or an energy binding. */
public final class RuntimeProfileRepository018{
    private static final Map<ServerLevel,World>WORLDS=new IdentityHashMap<>();
    private static final Map<AutomobileEntity,WeakReference<VehicleDrivetrainRuntime>>CARS=new WeakHashMap<>();
    private static Path configFile;
    private static final class World{final UUID id;PhysicalDefinitions pendingInitial;String diagnostic="";World(UUID id){this.id=id;}}
    private RuntimeProfileRepository018(){}
    public static void configure(Path exactConfigFile){configFile=Objects.requireNonNull(exactConfigFile);}
    public static void observe(AutomobileEntity car,VehicleDrivetrainRuntime runtime){if(!car.level().isClientSide())CARS.put(car,new WeakReference<>(runtime));}
    public static void bind(ServerLevel level,UUID coreWorldInstance){
        thread(level);var old=WORLDS.get(level);if(old!=null){if(!old.id.equals(coreWorldInstance))throw new IllegalStateException("profile world UUID changed without unload");return;}
        var world=new World(coreWorldInstance);WORLDS.put(level,world);var definition=read(world);
        if(definition!=null){VehiclePhysicalProfileBridge015.bind(level,coreWorldInstance,definition);world.diagnostic="";}
    }
    private static PhysicalDefinitions read(World world){
        if(configFile==null){world.diagnostic="profile_config_unbound";return null;}
        var configured=ProfileCatalog015.configuration(configFile);
        if(configured.accepted()){var definition=configured.read().orElseThrow().definitions();try{
            ProfileDefinitionCodec.encode(definition);
            if(!admissible(definition)){world.diagnostic="profile_transport_budget_refused_preserved";return null;}
            var declared=ProfileCatalog015.bundled(RuntimeProfileRepository018.class.getClassLoader());
            if(!sameKinds(declared,definition)){world.diagnostic="profile_declared_engine_kind_change_refused";return null;}
            return definition;
        }catch(java.io.IOException|IllegalArgumentException invalid){world.diagnostic="profile_config_aggregate_invalid_preserved";return null;}}
        if(!configured.diagnostic().equals("profile_config_absent")){world.diagnostic=configured.diagnostic();return null;}
        try{var bundled=ProfileCatalog015.bundled(RuntimeProfileRepository018.class.getClassLoader());if(!admissible(bundled)){world.diagnostic="bundled_profile_transport_budget_refused";return null;}return bundled;}catch(java.io.IOException|IllegalArgumentException unavailable){world.diagnostic="bundled_profile_unavailable";return null;}
    }
    private static boolean admissible(PhysicalDefinitions definitions){
        for(String frame:definitions.frameBindings().keySet())for(String wheel:definitions.wheels().keySet())for(String engine:definitions.engines().keySet()){
            var s=definitions.resolve(frame,wheel,engine).orElseThrow();
            if(!RuntimeTransportBudget018.permits(s,definitions.loadRules().maximumKnownPayloadKg(),0,0,0,0,1,0,0,1.18,
                    io.github.foundationgames.automobility.automobile.drivetrain.EngineController.Config.defaults().starterTorqueFractionOfPeak(),.05))return false;
        }return true;
    }
    /** Called on successful server data/config reload; invalid raw leaves the existing active/pending definition intact. */
    public static void reload(MinecraftServer server){
        if(!server.isSameThread())throw new IllegalStateException("profile server thread");
        for(var entry:WORLDS.entrySet())if(entry.getKey().getServer()==server){var level=entry.getKey();var world=entry.getValue();var definition=read(world);if(definition==null)continue;
            var active=VehiclePhysicalProfileBridge015.snapshot(level).orElse(null);
            if(active==null)world.pendingInitial=definition;
            else if(sameKinds(active.definitions(),definition)){VehiclePhysicalProfileBridge015.stage(level,new ProfileJson.Read(ProfileJson.write(definition),definition,""));world.diagnostic="profile_reload_pending_rest";}
            else world.diagnostic="profile_engine_kind_change_refused";
        }
    }
    private static boolean sameKinds(PhysicalDefinitions before,PhysicalDefinitions after){for(var entry:before.engines().entrySet()){var next=after.engines().get(entry.getKey());if(next!=null&&(entry.getValue().electric()!=null)!=(next.electric()!=null))return false;}return true;}
    /** Full selected-live-car set: weak registrations cannot retain removed/world-unloaded cars or become an energy owner. */
    public static void tick(MinecraftServer server){
        if(!server.isSameThread())throw new IllegalStateException("profile server thread");
        CARS.entrySet().removeIf(entry->entry.getKey().isRemoved()||entry.getValue().get()==null);
        for(var entry:WORLDS.entrySet())if(entry.getKey().getServer()==server){
            var level=entry.getKey();var world=entry.getValue();var boundaries=new ArrayList<ProfileReloadGate.Boundary>();
            for(var car:CARS.entrySet())if(car.getKey().level()==level){var runtime=car.getValue().get();if(runtime!=null)boundaries.add(runtime.profileReloadBoundary());}
            boolean ready=boundaries.stream().allMatch(ProfileReloadGate.Boundary::ready);
            if(world.pendingInitial!=null&&ready){VehiclePhysicalProfileBridge015.bind(level,world.id,world.pendingInitial);world.pendingInitial=null;world.diagnostic="";}
            if(VehiclePhysicalProfileBridge015.apply(level,boundaries))world.diagnostic="";
        }
    }
    public static Optional<VehiclePhysicalProfileBridge015.Bound>server(AutomobileEntity car){
        if(!(car.level() instanceof ServerLevel level)||!WORLDS.containsKey(level))return Optional.empty();
        // Invalid/oversized aggregate mass blocks this model without deleting cargo, guessing a clamp, or crashing the entity tick.
        try{return VehiclePhysicalProfileBridge015.server(car);}catch(IllegalArgumentException invalidPayload){return Optional.empty();}
    }
    public static Optional<ProfileReloadGate.Snapshot>snapshot(ServerLevel level){thread(level);return VehiclePhysicalProfileBridge015.snapshot(level);}
    public static String diagnostic(ServerLevel level){thread(level);var value=WORLDS.get(level);return value==null?"profile_world_unbound":value.diagnostic;}
    public static void unload(ServerLevel world){thread(world);WORLDS.remove(world);CARS.keySet().removeIf(car->car.level()==world);VehiclePhysicalProfileBridge015.unload(world);}
    public static void close(MinecraftServer server){if(!server.isSameThread())throw new IllegalStateException("profile server thread");for(var world:new ArrayList<>(WORLDS.keySet()))if(world.getServer()==server)unload(world);}
    private static void thread(ServerLevel level){if(!level.getServer().isSameThread())throw new IllegalStateException("profile owner thread");}
}
