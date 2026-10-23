package io.github.foundationgames.automobility.item;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.AutomobileComponent;
import io.github.foundationgames.automobility.util.Eventual;
import io.github.foundationgames.automobility.util.SimpleMapContentRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.function.Consumer;

public abstract class AutomobileComponentItem<T extends AutomobileComponent<T>, V> extends Item implements CustomCreativeOutput {
    protected final String translationKey;

    public AutomobileComponentItem(Properties settings, String translationKey) {
        super(settings);
        this.translationKey = translationKey;
    }

    public ItemStack createStack(V componentDescription) {
        var stack = new ItemStack(this);
        this.setComponent(stack, componentDescription);
        return stack;
    }

    public abstract void setComponent(ItemStack stack, V desc);

    public abstract T getComponent(ItemStack stack, HolderLookup.Provider registries);

    public abstract Identifier getComponentId(ItemStack stack, T component);

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, display, tooltip, tooltipFlag);
        var component = this.getComponent(stack, context.registries());
        var id = this.getComponentId(stack, component);
        var compKey = id.getNamespace()+"."+id.getPath();
        tooltip.accept(Component.translatable(this.translationKey+"."+compKey).withStyle(ChatFormatting.BLUE));

        component.appendTexts(tooltip, component);
    }

    public boolean isVisible(T component) {
        return !component.isEmpty();
    }

    protected boolean addToCreative(T component) {
        return !component.isEmpty();
    }

    public static class Dynamic<T extends AutomobileComponent<T>> extends AutomobileComponentItem<T, ResourceKey<T>> {
        public final ResourceKey<Registry<T>> registryKey;
        public final Eventual<DataComponentType<ResourceKey<T>>> dataComponent;
        public final T defaultComponent;

        public Dynamic(Properties settings, String translationKey, ResourceKey<Registry<T>> registryKey, Eventual<DataComponentType<ResourceKey<T>>> dataComponent, T defaultComponent) {
            super(settings, translationKey);
            this.registryKey = registryKey;
            this.dataComponent = dataComponent;
            this.defaultComponent = defaultComponent;
        }

        @Override
        public void setComponent(ItemStack stack, ResourceKey<T> component) {
            stack.set(dataComponent.require(), component);
        }

        @Override
        public T getComponent(ItemStack stack, HolderLookup.Provider registries) {
            var component = stack.get(dataComponent.require());
            if (component == null) {
                return defaultComponent;
            }

            return registries.lookupOrThrow(registryKey).get(component).map(Holder.Reference::value).orElse(defaultComponent);
        }

        @Override
        public Identifier getComponentId(ItemStack stack, T component) {
            var key = stack.get(dataComponent.require());
            if (key == null) return Automobility.rl("empty");

            return key.identifier();
        }

        public Holder<T> lookupComponent(ItemStack stack, HolderLookup.Provider registries) {
            var component = stack.get(dataComponent.require());
            if (component == null) {
                return Holder.direct(defaultComponent);
            }

            return registries.lookupOrThrow(registryKey).get(component).map(r -> (Holder<T>)r).orElse(Holder.direct(defaultComponent));
        }

        @Override
        public void provideCreativeOutput(Consumer<ItemStack> output, HolderLookup.Provider registries) {
            registries.lookupOrThrow(registryKey).listElements().forEach(ref ->
                    ref.unwrapKey().ifPresent(key -> {
                        var component = ref.value();
                        if (addToCreative(component) && NewVehicleContent.allows(registryKey.identifier(), key.identifier())) {
                            output.accept(this.createStack(key));
                        }
                    }));
        }
    }

    public static class Builtin<T extends AutomobileComponent<T>> extends AutomobileComponentItem<T, T> {
        protected final SimpleMapContentRegistry<T> registry;

        public Builtin(Properties settings, String translationKey, SimpleMapContentRegistry<T> registry) {
            super(settings, translationKey);

            this.registry = registry;
        }

        @Override
        public void setComponent(ItemStack stack, T component) {
            stack.set(AutomobilityItems.COMPONENT_GENERIC_AUTO_PART.require(), component.getId());
        }

        public T getComponent(ItemStack stack, HolderLookup.Provider registries) {
            return this.registry.getOrDefault(stack.get(AutomobilityItems.COMPONENT_GENERIC_AUTO_PART.require()));
        }

        @Override
        public Identifier getComponentId(ItemStack stack, T component) {
            return component.getId();
        }

        @Override
        public void provideCreativeOutput(Consumer<ItemStack> output, HolderLookup.Provider registries) {
            this.registry.forEach(component -> {
                if (addToCreative(component)) output.accept(this.createStack(component));
            });
        }
    }
}
