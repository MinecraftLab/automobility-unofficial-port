package io.github.foundationgames.automobility.mixin;
import java.io.File;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(PlayerDataStorage.class)
public interface GaragePlayerDataDirectoryAccess { @Accessor("playerDir") File automobility$playerDirectory(); }
