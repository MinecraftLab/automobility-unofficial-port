package io.github.foundationgames.automobility.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.item.AutomobileComponentItem;
import io.github.foundationgames.automobility.item.AutomobilityItems;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.ArrayList;

public final class AutoMechanicTableRecipeSerializer {
    public static final Codec<AutoComponentResult> AUTO_COMPONENT_STACK = RecordCodecBuilder.create(inst -> inst.group(
            Item.CODEC.fieldOf("item").forGetter(AutoComponentResult::item),
            Codec.INT.optionalFieldOf("count", 1).forGetter(AutoComponentResult::count),
            Identifier.CODEC.fieldOf("component").forGetter(AutoComponentResult::component)
    ).apply(inst, AutoComponentResult::new));

    public static final MapCodec<AutoMechanicTableRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf("category").forGetter(AutoMechanicTableRecipe::getCategory),
            Codec.list(AutoMechanicIngredient.CODEC).fieldOf("ingredients").forGetter(r -> r.autoMechanicIngredients),
            AUTO_COMPONENT_STACK.fieldOf("result").forGetter(AutoMechanicTableRecipe::getResultDescriptor),
            Codec.INT.fieldOf("sortnum").forGetter(r -> r.sortNum)
    ).apply(inst, AutoMechanicTableRecipe::fromComponentIngredients));

    public static final StreamCodec<RegistryFriendlyByteBuf, AutoMechanicTableRecipe> STREAM_CODEC =
            StreamCodec.of(AutoMechanicTableRecipeSerializer::toNetwork, AutoMechanicTableRecipeSerializer::fromNetwork);

    public static final RecipeSerializer<AutoMechanicTableRecipe> INSTANCE = new RecipeSerializer<>(CODEC, STREAM_CODEC);

    public static AutoMechanicTableRecipe fromNetwork(RegistryFriendlyByteBuf buf) {
        var category = Identifier.tryParse(buf.readUtf());

        int size = buf.readVarInt();
        var ingredients = new ArrayList<AutoMechanicIngredient>();
        for (int i = 0; i < size; i++) {
            ingredients.add(AutoMechanicIngredient.fromNetwork(buf));
        }

        var result = AutoComponentResult.fromNetwork(buf);
        int sortNum = buf.readInt();

        return AutoMechanicTableRecipe.fromComponentIngredients(category, ingredients, result, sortNum);
    }

    public static void toNetwork(RegistryFriendlyByteBuf buf, AutoMechanicTableRecipe recipe) {
        buf.writeUtf(recipe.category.toString());
        buf.writeVarInt(recipe.autoMechanicIngredients.size());
        recipe.autoMechanicIngredients.forEach(ingredient -> ingredient.toNetwork(buf));
        recipe.result.toNetwork(buf);
        buf.writeInt(recipe.sortNum);
    }

    public record AutoComponentResult(Holder<Item> item, int count, Identifier component) {
        public static AutoComponentResult fromStack(ItemStack stack) {
            var item = stack.getItem();
            Identifier component = Automobility.rl("empty");

            if (item instanceof AutomobileComponentItem.Dynamic<?> cItem) {
                component = cItem.getComponentId(stack, null);
            } else if (item instanceof AutomobileComponentItem.Builtin<?> cItem) {
                component = cItem.getComponent(stack, null).getId();
            }

            return new AutoComponentResult(stack.typeHolder(), stack.getCount(), component);
        }

        public static AutoComponentResult fromNetwork(RegistryFriendlyByteBuf buf) {
            return new AutoComponentResult(Item.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readIdentifier());
        }

        public void toNetwork(RegistryFriendlyByteBuf buf) {
            Item.STREAM_CODEC.encode(buf, this.item);
            buf.writeVarInt(this.count);
            buf.writeIdentifier(this.component);
        }

        public ItemStack createStack() {
            var stack = new ItemStack(this.item, this.count);
            var item = stack.getItem();
            if (item instanceof AutomobileComponentItem.Dynamic<?> cItem) {
                cItem.setComponent(stack, (ResourceKey) ResourceKey.create(cItem.registryKey, this.component));
            } else {
                stack.set(AutomobilityItems.COMPONENT_GENERIC_AUTO_PART.require(), this.component);
            }
            return stack;
        }
    }

    private AutoMechanicTableRecipeSerializer() {}
}
