package io.github.foundationgames.automobility.client.lighting.direct.bootstrap;
import net.fabricmc.api.ClientModInitializer;
/** Optional backend is referenced only by name after successful admission. */
public final class DirectBootstrap implements ClientModInitializer {
    @Override public void onInitializeClient(){
        var decision=DirectGate.installation();
        io.github.foundationgames.automobility.client.lighting.DirectRendererSupport.admission(decision.allowed(),decision.reason());
        System.out.println("[Automobility direct50] gate "+decision.reason());
        if(!decision.allowed())return;
        try{
            var type=Class.forName("io.github.foundationgames.automobility.client.lighting.direct.DirectFeature",true,DirectBootstrap.class.getClassLoader());
            ((ClientModInitializer)type.getDeclaredConstructor().newInstance()).onInitializeClient();
            var observation=type.getMethod("supportObservation");
            io.github.foundationgames.automobility.client.lighting.DirectRendererSupport.observation(()->{
                try{return (io.github.foundationgames.automobility.client.lighting.DirectRendererSupport.Observation)observation.invoke(null);}
                catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
            });
        }catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){
            io.github.foundationgames.automobility.client.lighting.DirectRendererSupport.unavailable("setup_failed:"+unavailable.getClass().getSimpleName());
            System.out.println("[Automobility direct50] backend unavailable; scalar retained: "+unavailable.getClass().getSimpleName());
        }
    }
}
