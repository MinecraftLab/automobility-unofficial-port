package io.github.foundationgames.automobility.client.lighting.direct;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import io.github.foundationgames.automobility.client.lighting.direct.mixin.DirectBufferAccess;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;

/** QA-only, defaultOFF direct stream experiment; Core49 remains unchanged. */
public final class DirectFeature implements ClientModInitializer {
    private static final boolean ENABLED=Boolean.parseBoolean(System.getProperty("automobility.experimental.directEntityScratch", "true"));
    private static boolean armed,active;
    private static Thread owner;
    private static DirectPinnedIris iris;
    private static DirectScratch scratch;
    private static Class<?> batchClass;
    private static Field batchSize;
    private static Object world,models,device;
    private static DirectPinnedIris.Snapshot epoch;
    private static long pushes,vertices,rejects,peakBytes,clears;
    public record Request(int vertices,VertexConsumer target,Consumer<VertexConsumer> emitter){}

    @Override public void onInitializeClient(){
        if(!ENABLED){System.out.println("[Automobility direct50] direct OFF; private integrated scalar");return;}
        try{
            var client=Minecraft.getInstance();
            if(!pin("iris","1.11.4+mc26.2","F1F7AB57C974D193BA33AA285864A0DED949216F402116FCEAF4DC7739B4DD7C")
                    ||!pin("sodium","0.9.2+mc26.2","16A5E91DB49750F8C046ECCA3B8F2AF5A28EA6F9B6581B42F123CA8FDA20864F"))return;
            var core=FabricLoader.getInstance().getModContainer("automobility").orElseThrow();
            if(!core.getMetadata().getVersion().getFriendlyString().equals("1.2.0-dev.50+26.2-fabric")&&!core.getMetadata().getVersion().getFriendlyString().equals("1.2.0-dev.51+26.2-fabric")&&!core.getMetadata().getVersion().getFriendlyString().equals("1.2.0-dev.52+26.2-fabric")&&!core.getMetadata().getVersion().getFriendlyString().equals("1.3.0-dev.8+26.2-fabric")&&!core.getMetadata().getVersion().getFriendlyString().equals("1.3.0+26.2-fabric")&&!core.getMetadata().getVersion().getFriendlyString().equals("1.3.1+26.2-fabric")&&!core.getMetadata().getVersion().getFriendlyString().equals("1.4.0-dev.1+26.2-fabric"))return;
            iris=DirectPinnedIris.resolve();if(!iris.available())return;
            batchClass=Class.forName("io.github.foundationgames.automobility.client.lighting.VehicleHeadlightRenderer$LitBatch");
            batchSize=batchClass.getDeclaredField("size");batchSize.setAccessible(true);owner=Thread.currentThread();armed=true;System.out.println("[Automobility direct50] direct ARMED; private integrated opt-in");
            ClientTickEvents.END_CLIENT_TICK.register(c->{if(world!=c.level){clear();world=c.level;}});
            ClientLifecycleEvents.CLIENT_STOPPING.register(c->{System.out.println("[Automobility direct50] closing "+statistics());armed=false;clear();world=models=device=null;});
        }catch(Exception|LinkageError rejected){armed=false;System.out.println("[Automobility direct50] direct unavailable "+rejected.getClass().getSimpleName());}
    }
    private static boolean pin(String id,String version,String wanted)throws Exception{
        var mod=FabricLoader.getInstance().getModContainer(id).orElse(null);
        if(mod==null||!version.equals(mod.getMetadata().getVersion().getFriendlyString())||mod.getOrigin().getPaths().size()!=1)return false;
        Path path=mod.getOrigin().getPaths().getFirst();if(!Files.isRegularFile(path))return false;
        var digest=MessageDigest.getInstance("SHA-256");try(var stream=Files.newInputStream(path)){byte[] block=new byte[65536];int n;while((n=stream.read(block))>=0)if(n>0)digest.update(block,0,n);}
        return HexFormat.of().withUpperCase().formatHex(digest.digest()).equals(wanted);
    }
    public static boolean enabled(){return ENABLED&&armed;}
    public static io.github.foundationgames.automobility.client.lighting.DirectRendererSupport.Observation supportObservation(){
        return new io.github.foundationgames.automobility.client.lighting.DirectRendererSupport.Observation(ENABLED,armed,pushes,vertices);
    }
    static DirectPinnedIris iris(){return iris;}
    static long pushes(){return pushes;}
    static long pushedVertices(){return vertices;}
    static long clears(){return clears;}
    static void disable(){armed=false;clear();}
    public static void abortForFailure(){disable();}
    private static boolean reject(){rejects++;return false;}
    public static String statistics(){return "enabled="+ENABLED+" armed="+armed+" pushes="+pushes+" vertices="+vertices+" rejects="+rejects+" residentBytes="+(scratch==null?0:scratch.capacity())+" peakBytes="+peakBytes+" clears="+clears;}
    private static void clear(){if(active)throw new IllegalStateException("Reentrant scratch lifecycle");if(scratch!=null){scratch.close();scratch=null;}epoch=null;clears++;}

    public static boolean renderBatch(Object batch,VertexConsumer target,Consumer<VertexConsumer> emitter){
        if(!enabled())return false;
        if(batch==null||batch.getClass()!=batchClass)return reject();
        int count;try{count=Math.multiplyExact(batchSize.getInt(batch),4);}catch(ReflectiveOperationException|ArithmeticException rejected){return reject();}
        return render(new Request(count,target,emitter));
    }
    private static boolean render(Request request){
        if(!enabled())return false;
        if(Thread.currentThread()!=owner||active||request.target()==null||request.target().getClass()!=BufferBuilder.class
                ||!((Object)request.target() instanceof DirectBufferAccess target)
                ||!target.automobility$directBuilding()
                ||!DirectAdmission.allows(request.vertices(),target.automobility$directVertices())
                ||target.automobility$directTopology()!=PrimitiveTopology.QUADS)return reject();
        var before=iris.capture();VertexFormat format=target.automobility$directFormat();
        if(before==null||!iris.isExtendedEntity(format))return reject();
        VertexBufferWriter writer=VertexBufferWriter.tryOf(request.target());if(writer==null)return reject();
        int initial=target.automobility$directVertices();
        var c=Minecraft.getInstance();Object currentModels=c.getModelManager().getBlockStateModelSet(),currentDevice=RenderSystem.getDevice();
        if(world!=c.level||models!=currentModels||device!=currentDevice||epoch==null||!epoch.sameEpoch(before)){
            clear();world=c.level;models=currentModels;device=currentDevice;epoch=before;
        }
        active=true;
        try{
            if(scratch==null)scratch=new DirectScratch();scratch.reset(request.vertices(),before.ids());peakBytes=Math.max(peakBytes,scratch.capacity());
            // Pure ordered original emission once into owned scratch; no target touched yet.
            request.emitter().accept(scratch);ByteBuffer bytes=scratch.finish();
            var after=iris.capture();
            if(after==null||!before.sameEpoch(after)||!before.ids().equals(after.ids())||!target.automobility$directBuilding()||target.automobility$directVertices()!=initial
                    ||target.automobility$directFormat()!=format||target.automobility$directTopology()!=PrimitiveTopology.QUADS
                    ||!DirectAdmission.allows(request.vertices(),initial)||bytes.remaining()!=DirectAdmission.bytes(request.vertices())||!bytes.isDirect())
                throw new IllegalStateException("Scratch preflight drift before target mutation");
            long address=MemoryUtil.memAddress(bytes);
            try(var stack=MemoryStack.stackPush()){
                // Mutation boundary: no catch/fallback retry after either call.
                target.automobility$directFinishVertex();writer.push(stack,address,request.vertices(),format);
            }
            if(target.automobility$directVertices()!=initial+request.vertices())throw new IllegalStateException("Native push count mismatch after mutation");
            pushes++;vertices+=request.vertices();return true;
        }finally{active=false;}
    }
}
