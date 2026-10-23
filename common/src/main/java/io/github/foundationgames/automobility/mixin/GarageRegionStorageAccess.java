package io.github.foundationgames.automobility.mixin;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(EntityStorage.class)
public interface GarageRegionStorageAccess { @Accessor("simpleRegionStorage") SimpleRegionStorage automobility$garageStorage(); }
