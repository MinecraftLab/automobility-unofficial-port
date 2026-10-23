package io.github.foundationgames.automobility.mixin;
import io.github.foundationgames.automobility.entity.GarageWorldAdmission;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EntityType.class)
public class GarageEntityLoadMixin {
 @Inject(method="loadEntityRecursive(Lnet/minecraft/world/level/storage/ValueInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/EntityProcessor;)Lnet/minecraft/world/entity/Entity;",at=@At("HEAD"),cancellable=true)
 private static void automobility$garageAdmission(ValueInput input,Level level,EntitySpawnReason request,EntityProcessor processor,CallbackInfoReturnable<Entity> cir){if(GarageWorldAdmission.reject(input,level))cir.setReturnValue(null);}
 @Inject(method="loadEntityRecursive(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/storage/ValueInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/EntityProcessor;)Lnet/minecraft/world/entity/Entity;",at=@At("HEAD"),cancellable=true)
 private static void automobility$garageExplicitAdmission(EntityType<?> type,ValueInput input,Level level,EntitySpawnReason reason,EntityProcessor processor,CallbackInfoReturnable<Entity> cir){if(GarageWorldAdmission.reject(input,level,type==io.github.foundationgames.automobility.entity.AutomobilityEntities.AUTOMOBILE.require()))cir.setReturnValue(null);}
}
