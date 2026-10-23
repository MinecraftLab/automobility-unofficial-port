package io.github.foundationgames.automobility.client.lighting.direct.bootstrap;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
/** Shared startup-only gate. No references to optional backend classes. */
public final class DirectGate {
    private static DirectCompatibilityPolicy.Decision installed;
    public static synchronized DirectCompatibilityPolicy.Decision installation(){
        if(installed==null)installed=resolve();return installed;
    }
    private static DirectCompatibilityPolicy.Decision resolve(){
        if(!Boolean.parseBoolean(System.getProperty("automobility.experimental.directEntityScratch", "true")))return new DirectCompatibilityPolicy.Decision(false,"opt-in OFF");
        try{
            var f=FabricLoader.getInstance();
            return DirectCompatibilityPolicy.evaluate(new DirectCompatibilityPolicy.Inputs(true,f.getEnvironmentType()==EnvType.CLIENT,
                    version(f,"minecraft"),version(f,"automobility"),version(f,"iris"),version(f,"sodium"),sha(f,"iris"),sha(f,"sodium")));
        }catch(Exception|LinkageError unavailable){return new DirectCompatibilityPolicy.Decision(false,"optional gate unavailable");}
    }
    private static String version(FabricLoader f,String id){return f.getModContainer(id).map(m->m.getMetadata().getVersion().getFriendlyString()).orElse("");}
    private static String sha(FabricLoader f,String id)throws Exception{
        var m=f.getModContainer(id).orElse(null);if(m==null||m.getOrigin().getPaths().size()!=1)return "";
        var path=m.getOrigin().getPaths().getFirst();if(!Files.isRegularFile(path))return "";
        var d=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[] b=new byte[65536];int n;while((n=in.read(b))>=0)if(n>0)d.update(b,0,n);}
        return HexFormat.of().withUpperCase().formatHex(d.digest());
    }
    private DirectGate(){}
}
