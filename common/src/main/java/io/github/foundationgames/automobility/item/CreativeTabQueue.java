package io.github.foundationgames.automobility.item;

import io.github.foundationgames.automobility.util.Eventual;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CreativeTabQueue {
    public final Identifier location;
    private final List<Eventual<? extends Item>> items = new ArrayList<>();

    public CreativeTabQueue(Identifier location) {
        this.location = location;
    }

    public void queue(Eventual<? extends Item> item) {
        this.items.add(item);
    }

    public void accept(Consumer<ItemStack> output, HolderLookup.Provider registries) {
        items.forEach(i -> {
            if (i.require() instanceof CustomCreativeOutput outputItem) {
                outputItem.provideCreativeOutput(output, registries);
            } else {
                output.accept(i.require().getDefaultInstance());
            }
        });
    }
}
