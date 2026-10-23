package io.github.foundationgames.automobility.mixin;
import io.github.foundationgames.automobility.util.duck.GarageRawInput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.TagValueInput;
import org.spongepowered.asm.mixin.*;
@Mixin(TagValueInput.class)
public abstract class GarageRawInputMixin implements GarageRawInput {
 @Shadow @Final private CompoundTag input;
 public CompoundTag automobility$garageRawCopy(){return input.copy();}
}
