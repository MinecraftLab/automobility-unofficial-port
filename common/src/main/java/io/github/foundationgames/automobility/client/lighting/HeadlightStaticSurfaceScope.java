package io.github.foundationgames.automobility.client.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Predicate;

/** Conservative pinned-native prototype domain, not protection against arbitrary mutating mixins. */
final class HeadlightStaticSurfaceScope {
    private static HeadlightStaticSurfaceScope active;
    private static Thread activeThread;
    private BlockGetter world;
    private final Predicate<BlockPos> loaded;
    private final java.util.function.LongPredicate localEnvelope;
    private boolean localEnvelopeValid=true;
    private final Set<Long> chunks=new HashSet<>();
    private final Set<BlockState> states=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Object,Boolean> models=new IdentityHashMap<>();
    private final Map<BlockState,Object> observedModels=new IdentityHashMap<>();
    private final HeadlightDelegateBindings.Builder delegates=new HeadlightDelegateBindings.Builder();
    private boolean valid=true;
    private boolean modelCaptureStable=true;
    private String reason="known_native";
    HeadlightStaticSurfaceScope(Predicate<BlockPos> loaded){this(loaded,null);}
    HeadlightStaticSurfaceScope(Predicate<BlockPos> loaded,java.util.function.LongPredicate localEnvelope){this.loaded=loaded;this.localEnvelope=localEnvelope;}
    boolean localEnvelopeValid(){return localEnvelopeValid;}
    boolean valid(){return valid && delegates.stillValid();}
    HeadlightDelegateBindings modelBindings(){
        if(!modelCaptureStable)return null;
        try{return delegates.freeze();}catch(IllegalStateException error){return null;}
    }
    String reason(){return reason;}
    void reject(){valid=false;}
    boolean enter(BlockGetter world){
        if(active!=null){active.reject();reject();return false;}
        this.world=world;active=this;activeThread=Thread.currentThread();return true;
    }
    void exit(){if(active==this){active=null;activeThread=null;}}
    static void observe(BlockGetter world,BlockPos pos,BlockState state){
        var current=active;
        if(current!=null && activeThread==Thread.currentThread() && current.world==world)current.visit(pos,state);
    }
    static BlockStateModel observeModel(BlockStateModel model,BlockGetter world,BlockState state){
        var current=active;
        if(HeadlightPinnedCollectorWrappers.ENABLED && current!=null && activeThread==Thread.currentThread() && current.world==world){
            current.recordModel(state,model);
        }
        return model;
    }
    void recordModel(BlockState state,Object model){
        if(observedModels.containsKey(state) && observedModels.get(state)!=model){modelCaptureStable=false;reject();return;}
        if(observedModels.size()>=256 && !observedModels.containsKey(state)){modelCaptureStable=false;reject();return;}
        observedModels.put(state,model);model(model);
    }
    void certifyModels(java.util.function.Function<BlockState,Object> lookup){
        if(!valid)return;
        for(var state:states)if(!state.isAir()){
            var model=lookup.apply(state);
            if(observedModels.containsKey(state) && observedModels.get(state)!=model){modelCaptureStable=false;reason="model_root_changed";reject();return;}
            model(model);
        }
    }
    void visit(BlockPos pos,BlockState state){
        if(!valid)return;
        long chunk=((long)(pos.getX()>>4)<<32)|((pos.getZ()>>4)&0xffffffffL);
        // Observe, never trim a native read or weaken the original global certification.
        if(localEnvelope!=null&&!localEnvelope.test(chunk))localEnvelopeValid=false;
        if(chunks.add(chunk) && (chunks.size()>64 || !loaded.test(pos))){reason="unloaded_or_chunk_cap";reject();return;}
        if(states.add(state) && (states.size()>256 || !supported(state))){reason="unsupported_state:"+(state==null?"null":state.getClass().getName());reject();}
    }
    static boolean supported(BlockState state){
        if(state==null || state.getClass()!=BlockState.class)return false;
        var b=state.getBlock();
        boolean known=b==Blocks.AIR || b==Blocks.CAVE_AIR || b==Blocks.VOID_AIR || b==Blocks.STONE
                || b==Blocks.STONE_SLAB || b==Blocks.SMOOTH_STONE_SLAB || b==Blocks.OAK_FENCE;
        return known && state.getFluidState().isEmpty() && !b.hasDynamicShape();
    }
    void model(Object model){
        if(!valid)return;
        Boolean known=models.get(model);
        if(known==null){
            if(models.size()>=256){reject();return;}
            known=NativeModels.closed(model,delegates);models.put(model,known);
        }
        if(!known){reason="model:"+NativeModels.failure;reject();}
    }
    /** Only reads the selected, already-collected native tree. No callbacks/RNG/predicates. */
    private static final class NativeModels {
        private static Class<?> single,weighted,multipart,part,quads,weightedListType,weightedEntryType;
        private static Field singleModel,weightedList,multipartModels,partQuads,allQuads;
        private static String failure="unknown_native_tree";
        static {
            try{
                var loader=HeadlightStaticSurfaceScope.class.getClassLoader();
                single=Class.forName("net.minecraft.client.renderer.block.dispatch.SingleVariant",false,loader);
                weighted=Class.forName("net.minecraft.client.renderer.block.dispatch.WeightedVariants",false,loader);
                multipart=Class.forName("net.minecraft.client.renderer.block.dispatch.multipart.MultiPartModel",false,loader);
                part=Class.forName("net.minecraft.client.resources.model.SimpleModelWrapper",false,loader);
                quads=Class.forName("net.minecraft.client.resources.model.geometry.QuadCollection",false,loader);
                weightedListType=Class.forName("net.minecraft.util.random.WeightedList",false,loader);
                weightedEntryType=Class.forName("net.minecraft.util.random.Weighted",false,loader);
                singleModel=field(single,"model");weightedList=field(weighted,"list");multipartModels=field(multipart,"models");
                partQuads=field(part,"quads");allQuads=field(quads,"all");
            }catch(ReflectiveOperationException | RuntimeException error){single=null;failure=error.toString();}
        }
        private static Field field(Class<?> type,String name)throws ReflectiveOperationException{
            var f=type.getDeclaredField(name);f.setAccessible(true);return f;
        }
        static boolean closed(Object model,HeadlightDelegateBindings.Builder delegates){
            if(single==null)return false;
            failure="unknown_native_tree";
            try{return walk(model,Collections.newSetFromMap(new IdentityHashMap<>()),new int[1],0,delegates);}
            catch(ReflectiveOperationException | RuntimeException error){failure=error.toString();return false;}
        }
        private static boolean walk(Object value,Set<Object> stack,int[] nodes,int depth,HeadlightDelegateBindings.Builder delegates)throws ReflectiveOperationException{
            if(value==null || depth>8 || ++nodes[0]>128 || !stack.add(value))return false;
            try{
                var type=value.getClass();
                if(HeadlightPinnedCollectorWrappers.supported(value))
                    return walk(HeadlightPinnedCollectorWrappers.delegate(value,delegates),stack,nodes,depth+1,delegates);
                if(type==single)return leaf(singleModel.get(value));
                if(type==weighted){
                    var list=weightedList.get(value);
                    if(list==null || list.getClass()!=weightedListType)return false;
                    Object unwrapped=list.getClass().getMethod("unwrap").invoke(list);
                    if(!(unwrapped instanceof List<?> entries) || !nativeList(entries) || entries.isEmpty() || entries.size()>64)return false;
                    for(Object entry:entries)if(entry==null || entry.getClass()!=weightedEntryType
                            || !walk(weightedEntryType.getMethod("value").invoke(entry),stack,nodes,depth+1,delegates))return false;
                    return true;
                }
                if(type==multipart){
                    Object selected=multipartModels.get(value);
                    if(!(selected instanceof List<?> children) || !nativeList(children) || children.size()>64)return false;
                    for(Object child:children)if(!walk(child,stack,nodes,depth+1,delegates))return false;
                    return true;
                }
                failure="unknown_native_type:"+type.getName();return false;
            }finally{stack.remove(value);}
        }
        private static boolean leaf(Object value)throws ReflectiveOperationException{
            if(value==null || value.getClass()!=part)return false;
            Object collection=partQuads.get(value);
            if(collection==null || collection.getClass()!=quads)return false;
            Object all=allQuads.get(collection);
            if(!(all instanceof List<?> list) || !nativeList(list) || list.size()>4096)return false;
            int visitedQuads=0;
            for(Object item:list){
                // Fastutil's standard iterator is not fail-fast: retain a hard scan
                // bound even if a published native list unexpectedly grows.
                if(++visitedQuads>4096){failure="quad_scan_cap";return false;}
                if(item==null || item.getClass()!=BakedQuad.class || ((BakedQuad)item).materialInfo().isTinted())return false;
            }
            return true;
        }
        private static boolean nativeList(List<?> list)throws ClassNotFoundException{
            var type=list.getClass();String name=type.getName();
            boolean known=switch(name){
                case "java.util.ArrayList","java.util.Arrays$ArrayList","java.util.ImmutableCollections$ListN",
                     "java.util.ImmutableCollections$List12","java.util.Collections$EmptyList","java.util.Collections$SingletonList",
                     "com.google.common.collect.RegularImmutableList","com.google.common.collect.SingletonImmutableList",
                     "it.unimi.dsi.fastutil.objects.ReferenceArrayList" -> true;
                default -> false;
            };
            if(!known){failure="unknown_list:"+name;return false;}
            return type==Class.forName(name,false,HeadlightStaticSurfaceScope.class.getClassLoader());
        }
    }
}
