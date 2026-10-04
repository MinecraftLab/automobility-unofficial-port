package io.github.foundationgames.automobility.recipe;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.item.AutomobileComponentItem;
import io.github.foundationgames.automobility.item.AutomobilityItems;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

/** An Auto Mechanic Table ingredient with optional Automobility component matching. */
public record AutoMechanicIngredient(Ingredient ingredient, Optional<Identifier> component)
        implements Predicate<ItemStack> {
    private static final Codec<LegacyObject> LEGACY_OBJECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Item.CODEC.optionalFieldOf("item").forGetter(LegacyObject::item),
            TagKey.codec(Registries.ITEM).optionalFieldOf("tag").forGetter(LegacyObject::tag),
            Identifier.CODEC.optionalFieldOf("component").forGetter(LegacyObject::component)
    ).apply(instance, LegacyObject::new));

    public static final Codec<AutoMechanicIngredient> CODEC = Codec.either(Ingredient.CODEC, LEGACY_OBJECT_CODEC)
            .flatXmap(
                    either -> either.map(
                            ingredient -> DataResult.success(vanilla(ingredient)),
                            LegacyObject::toIngredient),
                    ingredient -> ingredient.component().isEmpty()
                            ? DataResult.success(Either.left(ingredient.ingredient()))
                            : LegacyObject.fromIngredient(ingredient).map(Either::right));

    public static AutoMechanicIngredient vanilla(Ingredient ingredient) {
        return new AutoMechanicIngredient(ingredient, Optional.empty());
    }

    public static AutoMechanicIngredient component(Ingredient ingredient, Identifier component) {
        return new AutoMechanicIngredient(ingredient, Optional.of(component));
    }

    @Override
    public boolean test(ItemStack stack) {
        if (!this.ingredient.test(stack)) {
            return false;
        }

        return this.component.map(required -> required.equals(componentId(stack))).orElse(true);
    }

    public List<ItemStack> displayStacks() {
        return this.ingredient.items().map(holder -> {
            var stack = new ItemStack(holder);
            this.component.ifPresent(component -> setComponent(stack, component));
            return stack;
        }).toList();
    }

    public static AutoMechanicIngredient fromNetwork(RegistryFriendlyByteBuf buf) {
        var ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buf);
        var component = buf.readBoolean() ? Optional.of(buf.readIdentifier()) : Optional.<Identifier>empty();
        return new AutoMechanicIngredient(ingredient, component);
    }

    public void toNetwork(RegistryFriendlyByteBuf buf) {
        Ingredient.CONTENTS_STREAM_CODEC.encode(buf, this.ingredient);
        buf.writeBoolean(this.component.isPresent());
        this.component.ifPresent(buf::writeIdentifier);
    }

    private static Identifier componentId(ItemStack stack) {
        if (stack.getItem() instanceof AutomobileComponentItem.Dynamic<?> item) {
            return item.getComponentId(stack, null);
        }

        if (stack.getItem() instanceof AutomobileComponentItem.Builtin<?>) {
            return stack.getOrDefault(
                    AutomobilityItems.COMPONENT_GENERIC_AUTO_PART.require(),
                    Automobility.rl("empty"));
        }

        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setComponent(ItemStack stack, Identifier component) {
        if (stack.getItem() instanceof AutomobileComponentItem.Dynamic<?> item) {
            var rawItem = (AutomobileComponentItem.Dynamic) item;
            rawItem.setComponent(stack, ResourceKey.create(rawItem.registryKey, component));
        } else if (stack.getItem() instanceof AutomobileComponentItem.Builtin) {
            stack.set(AutomobilityItems.COMPONENT_GENERIC_AUTO_PART.require(), component);
        }
    }

    private record LegacyObject(
            Optional<Holder<Item>> item,
            Optional<TagKey<Item>> tag,
            Optional<Identifier> component) {
        private DataResult<AutoMechanicIngredient> toIngredient() {
            if (this.item.isPresent() == this.tag.isPresent()) {
                return DataResult.error(() -> "Auto Mechanic ingredient must define exactly one of item or tag");
            }

            if (this.component.isPresent() && this.item.isEmpty()) {
                return DataResult.error(() -> "Component-specific Auto Mechanic ingredients must define an item");
            }

            Ingredient ingredient;
            if (this.item.isPresent()) {
                ingredient = Ingredient.of(HolderSet.direct(this.item.get()));
            } else {
                var holders = StreamSupport.stream(
                        BuiltInRegistries.ITEM.getTagOrEmpty(this.tag.get()).spliterator(), false).toList();
                if (holders.isEmpty()) {
                    return DataResult.error(() -> "Unknown or empty item tag '" + this.tag.get().location() + "'");
                }
                ingredient = Ingredient.of(HolderSet.direct(holders));
            }

            return DataResult.success(new AutoMechanicIngredient(ingredient, this.component));
        }

        private static DataResult<LegacyObject> fromIngredient(AutoMechanicIngredient ingredient) {
            var items = ingredient.ingredient().items().toList();
            if (items.size() != 1) {
                return DataResult.error(() -> "Component-specific Auto Mechanic ingredients must contain one item");
            }

            return DataResult.success(new LegacyObject(
                    Optional.of(items.getFirst()), Optional.empty(), ingredient.component()));
        }
    }
}
