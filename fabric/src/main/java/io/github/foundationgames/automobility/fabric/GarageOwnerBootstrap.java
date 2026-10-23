package io.github.foundationgames.automobility.fabric;

import io.github.foundationgames.automobility.automobile.garage.GarageTransferParticipant;
import io.github.foundationgames.automobility.automobile.garage.GarageEngineTransferParticipant;
import io.github.foundationgames.automobility.entity.GarageTransferRuntime;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Private integrated-cohort gate. Unknown artifacts keep owner-bearing transfers closed. */
public final class GarageOwnerBootstrap {
    private GarageOwnerBootstrap(){}
    // Target source port: fill only from exact target producer handoffs before build.
    // No historical1.2 or26.2 producer JAR is admitted in this target.
    private static final Map<String,String> QUALIFIED_JARS=Map.of(
            "pumps_and_pistons","D476D0293B13451864656D94F94F282CD3C05D9EEF832D976BF8CAB4C34AA65C",
            "pumps_and_pistons_automobility","A559725D194D27BB124DB4F7E23B982A0AEF29B222FEC3BDD54F73F22773ED28",
            "powerworks","4479D0F250E28A525C4092B532D2F04177AE1CD248852207B0970DFCA07029A8");
    private record Nested(String parent,String member){}
    private static final Map<String,Nested> QUALIFIED_NESTED=Map.of(
            "pumps_and_pistons_automobility",new Nested("pumps_and_pistons","META-INF/jars/pumps-and-pistons-automobility-1.11.1+26.1.2.jar"));
    public static void initialize(){
        var loader=FabricLoader.getInstance();var required=new TreeSet<String>();
        if(loader.isModLoaded("pumps_and_pistons")||loader.isModLoaded("pumps_and_pistons_automobility"))required.add("pumps_and_pistons");
        if(loader.isModLoaded("powerworks"))required.add("powerworks");
        GarageTransferRuntime.initialize(required);
        if(required.contains("pumps_and_pistons")&&qualified("pumps_and_pistons")&&qualified("pumps_and_pistons_automobility")){
            register("pumps_and_pistons","dev.rosenvik.pumpsandpistons.compat.automobility.garage.PumpsGarageTransferParticipant");
            registerEngine("pumps_and_pistons","dev.rosenvik.pumpsandpistons.compat.automobility.garage.PumpsEngineTransferParticipant");
        }
        if(required.contains("powerworks")&&qualified("powerworks")){
            register("powerworks","com.offchunk.powerworks.compat.automobility.garage.PowerworksGarageParticipant");
            registerEngine("powerworks","com.offchunk.powerworks.compat.automobility.garage.PowerworksEngineTransferParticipant");
        }
        ServerLifecycleEvents.SERVER_STARTING.register(GarageTransferRuntime::start);
        ServerTickEvents.END_SERVER_TICK.register(GarageTransferRuntime::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(GarageTransferRuntime::stop);
    }
    private static boolean qualified(String id){
        var expected=QUALIFIED_JARS.get(id);if(expected==null)return false;
        try{
            var mod=FabricLoader.getInstance().getModContainer(id).orElseThrow();var origin=mod.getOrigin();
            if(origin.getKind()==ModOrigin.Kind.NESTED){
                var nested=QUALIFIED_NESTED.get(id);if(nested==null||!nested.parent().equals(origin.getParentModId())||!nested.member().equals(origin.getParentSubLocation())||!qualified(nested.parent()))return false;
                var parent=FabricLoader.getInstance().getModContainer(nested.parent()).orElseThrow().getOrigin();
                if(parent.getKind()!=ModOrigin.Kind.PATH||parent.getPaths().size()!=1)return false;
                try(var zip=new java.util.zip.ZipFile(parent.getPaths().getFirst().toFile())){
                    var entry=zip.getEntry(nested.member());if(entry==null||entry.isDirectory()||entry.getSize()<0||entry.getSize()>16_777_216)return false;
                    var digest=MessageDigest.getInstance("SHA-256");try(var input=zip.getInputStream(entry)){byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);}
                    return HexFormat.of().withUpperCase().formatHex(digest.digest()).equals(expected);
                }
            }
            if(origin.getKind()!=ModOrigin.Kind.PATH||origin.getPaths().size()!=1)return false;
            var path=origin.getPaths().getFirst();if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.getFileName().toString().endsWith(".jar"))return false;
            var digest=MessageDigest.getInstance("SHA-256");try(var input=Files.newInputStream(path)){byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);}
            return HexFormat.of().withUpperCase().formatHex(digest.digest()).equals(expected);
        }catch(Exception error){LoggerFactory.getLogger("AutomobilityGarage").warn("Garage owner artifact could not be qualified: {}",id,error);return false;}
    }
    private static void register(String id,String name){
        try{
            var owner=(GarageTransferParticipant)Class.forName(name,true,GarageOwnerBootstrap.class.getClassLoader()).getConstructor().newInstance();
            if(!id.equals(owner.id())||!GarageTransferRuntime.registerQualified(owner))throw new IllegalStateException("Garage owner identity/registration refused");
        }catch(ReflectiveOperationException|RuntimeException|LinkageError error){LoggerFactory.getLogger("AutomobilityGarage").warn("Garage owner remains unavailable: {}",id,error);}
    }
    private static void registerEngine(String id,String name){
        try{
            var owner=(GarageEngineTransferParticipant)Class.forName(name,true,GarageOwnerBootstrap.class.getClassLoader()).getConstructor().newInstance();
            if(!id.equals(owner.id())||!GarageTransferRuntime.registerQualifiedEngine(owner))throw new IllegalStateException("Garage engine owner identity/registration refused");
        }catch(ReflectiveOperationException|RuntimeException|LinkageError error){LoggerFactory.getLogger("AutomobilityGarage").warn("Garage engine owner remains unavailable: {}",id,error);}
    }
}
