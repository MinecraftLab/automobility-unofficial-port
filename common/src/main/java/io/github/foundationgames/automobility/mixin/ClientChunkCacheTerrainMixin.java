package io.github.foundationgames.automobility.mixin;

import io.github.foundationgames.automobility.entity.AutomobileTerrainRevisions;
import net.minecraft.client.multiplayer.ClientChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheTerrainMixin {
    @Unique
    private int automobility$viewCenterX = Integer.MIN_VALUE;
    @Unique
    private int automobility$viewCenterZ = Integer.MIN_VALUE;
    @Unique
    private int automobility$viewRadius = Integer.MIN_VALUE;

    @Inject(method = "updateViewCenter", at = @At("TAIL"))
    private void automobility$terrainViewCenterChanged(int x, int z, CallbackInfo ci) {
        if (x != automobility$viewCenterX || z != automobility$viewCenterZ) {
            automobility$viewCenterX = x;
            automobility$viewCenterZ = z;
            automobility$terrainStorageChanged();
        }
    }

    @Inject(method = "updateViewRadius", at = @At("TAIL"))
    private void automobility$terrainViewRadiusChanged(int radius, CallbackInfo ci) {
        if (radius != automobility$viewRadius) {
            automobility$viewRadius = radius;
            automobility$terrainStorageChanged();
        }
    }

    @Unique
    private void automobility$terrainStorageChanged() {
        var cache = (ClientChunkCache) (Object) this;
        if (cache.getLevel() instanceof AutomobileTerrainRevisions.Source source) {
            var revisions = source.automobility$terrainRevisions();
            if (revisions != null) { // A cache may still be inside its owning level's construction.
                revisions.storageChanged();
            }
        }
    }
}
