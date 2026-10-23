package io.github.foundationgames.automobility.mixin;

import io.github.foundationgames.automobility.client.lighting.HeadlightTerrainCertificate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Independent light-only observers; original terrain/physics revision mixins remain unchanged. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelHeadlightTerrainMixin implements HeadlightTerrainCertificate.Source {
    @Unique private HeadlightTerrainCertificate.Tracker automobility$headlightTracker;
    @Override public HeadlightTerrainCertificate.Tracker automobility$headlightTerrain(){
        if(!HeadlightTerrainCertificate.ENABLED)return null;
        if(automobility$headlightTracker==null)automobility$headlightTracker=new HeadlightTerrainCertificate.Tracker();
        return automobility$headlightTracker;
    }
    @Override public Object automobility$headlightChunk(int x,int z){
        var chunk=((ClientLevel)(Object)this).getChunkSource().getChunk(x,z,ChunkStatus.FULL,false);
        return chunk!=null&&chunk.getClass()==LevelChunk.class?chunk:null;
    }
    @Inject(method="setBlocksDirty",at=@At("TAIL"))
    private void automobility$headlightChanged(BlockPos pos,BlockState oldState,BlockState newState,CallbackInfo ci){
        if(automobility$headlightTracker!=null&&oldState!=newState)automobility$headlightTracker.changed(pos.getX()>>4,pos.getZ()>>4);
    }
    @Inject(method="onChunkLoaded",at=@At("TAIL"))
    private void automobility$headlightLoaded(ChunkPos pos,CallbackInfo ci){
        if(automobility$headlightTracker!=null)automobility$headlightTracker.changed(pos.x(),pos.z());
    }
    @Inject(method="unload",at=@At("TAIL"))
    private void automobility$headlightUnloaded(LevelChunk chunk,CallbackInfo ci){
        if(automobility$headlightTracker!=null){var pos=chunk.getPos();automobility$headlightTracker.changed(pos.x(),pos.z());}
    }
}
