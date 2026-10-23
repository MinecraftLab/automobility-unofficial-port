package io.github.foundationgames.automobility.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.foundationgames.automobility.entity.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.entity.ChunkEntities;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Optional;
@Mixin(EntityStorage.class)
public abstract class GarageEntityStorageMixin {
 @Shadow @Final private ServerLevel level;
 @WrapMethod(method="lambda$loadEntities$0")
 private ChunkEntities<Entity> automobility$garageOrigin(ChunkPos chunk,Optional<CompoundTag> raw,Operation<ChunkEntities<Entity>> original){
  try(var scope=GarageWorldAdmission.origin(this,level,chunk,raw)){
   try{var result=original.call(chunk,raw);scope.complete();return result;}catch(Throwable error){scope.failure(error);throw error;}
  }
 }
 @Inject(method="storeEntities",at=@At("HEAD"),cancellable=true)
 private void automobility$garageNeverSaveFailedOrigin(ChunkEntities<Entity> entities,CallbackInfo ci){if(GarageFailedOrigins.failed(this,entities.getPos().pack()))ci.cancel();}
}
