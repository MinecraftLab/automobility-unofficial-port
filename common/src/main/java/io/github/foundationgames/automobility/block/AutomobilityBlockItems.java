package io.github.foundationgames.automobility.block;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/** Keeps block items on the block translation namespace already used by the locale catalog. */
final class AutomobilityBlockItems {
    private AutomobilityBlockItems() {}

    static Item.Properties properties(ResourceKey<Item> itemKey) {
        return new Item.Properties().setId(itemKey).useBlockDescriptionPrefix();
    }
}
