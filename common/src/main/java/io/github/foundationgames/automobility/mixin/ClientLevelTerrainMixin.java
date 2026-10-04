package io.github.foundationgames.automobility.mixin;

import io.github.foundationgames.automobility.entity.AutomobileTerrainRevisions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelTerrainMixin implements AutomobileTerrainRevisions.Source {
    @Unique
    private final AutomobileTerrainRevisions automobility$terrainRevisions = new AutomobileTerrainRevisions();

    @Override
    public AutomobileTerrainRevisions automobility$terrainRevisions() {
        return automobility$terrainRevisions;
    }

    @Inject(method = "setBlocksDirty", at = @At("TAIL"))
    private void automobility$terrainBlockChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        if (oldState != newState) {
            automobility$terrainRevisions.changed(pos.getX() >> 4, pos.getZ() >> 4);
        }
    }

    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void automobility$terrainChunkLoaded(ChunkPos pos, CallbackInfo ci) {
        automobility$terrainRevisions.changed(pos.x(), pos.z());
    }

    @Inject(method = "unload", at = @At("TAIL"))
    private void automobility$terrainChunkUnloaded(LevelChunk chunk, CallbackInfo ci) {
        var pos = chunk.getPos();
        automobility$terrainRevisions.unloaded(pos.x(), pos.z());
    }
}
