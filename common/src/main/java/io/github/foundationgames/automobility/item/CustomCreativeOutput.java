package io.github.foundationgames.automobility.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public interface CustomCreativeOutput {
	void provideCreativeOutput(Consumer<ItemStack> output, HolderLookup.Provider registries);
}
