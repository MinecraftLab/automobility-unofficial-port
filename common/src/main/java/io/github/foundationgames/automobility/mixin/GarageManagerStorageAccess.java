package io.github.foundationgames.automobility.mixin;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(PersistentEntitySectionManager.class)
public interface GarageManagerStorageAccess { @Accessor("permanentStorage") EntityPersistentStorage<Entity> automobility$garageStorage(); }
