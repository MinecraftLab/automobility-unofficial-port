package io.github.foundationgames.automobility.automobile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;
import io.github.foundationgames.automobility.item.AutomobilityItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

import java.util.Optional;
import java.util.function.Consumer;

public record AutomobileData(Optional<Identifier> prefabName,
                             ResourceKey<AutomobileFrame> frame,
                             ResourceKey<AutomobileWheel> wheel,
                             ResourceKey<AutomobileEngine> engine,
                             VehicleLightMode lightMode, boolean signalAutoCancel,
                             boolean comfortSignals, boolean lockSignalConfirmation) implements TooltipProvider {
    public AutomobileData {
        // The field remains in old addon/item schemas, but cannot restore the removed effect.
        lockSignalConfirmation = false;
    }

    public AutomobileData(Optional<Identifier> prefabName, ResourceKey<AutomobileFrame> frame,
                          ResourceKey<AutomobileWheel> wheel, ResourceKey<AutomobileEngine> engine,
                          VehicleLightMode lightMode, boolean signalAutoCancel) {
        this(prefabName, frame, wheel, engine, lightMode, signalAutoCancel, true, false);
    }

    public AutomobileData(Optional<Identifier> prefabName, ResourceKey<AutomobileFrame> frame,
                          ResourceKey<AutomobileWheel> wheel, ResourceKey<AutomobileEngine> engine,
                          VehicleLightMode lightMode) {
        this(prefabName, frame, wheel, engine, lightMode, true);
    }

    /** Legacy addon constructors and the item prototype retain their OFF default. */
    public AutomobileData(Optional<Identifier> prefabName, ResourceKey<AutomobileFrame> frame,
                          ResourceKey<AutomobileWheel> wheel, ResourceKey<AutomobileEngine> engine) {
        this(prefabName, frame, wheel, engine, VehicleLightMode.OFF);
    }

    public static final AutomobileData DEFAULT = new AutomobileData(
            Optional.empty(),
            AutomobileFrame.STANDARD_WHITE,
            AutomobileWheel.STANDARD,
            AutomobileEngine.IRON
    );

    public static final Codec<AutomobileData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.optionalFieldOf("prefab_id").forGetter(AutomobileData::prefabName),
            AutomobileFrame.CODEC.fieldOf("frame").forGetter(AutomobileData::frame),
            AutomobileWheel.CODEC.fieldOf("wheels").forGetter(AutomobileData::wheel),
            AutomobileEngine.CODEC.fieldOf("engine").forGetter(AutomobileData::engine),
            VehicleLightMode.CODEC.optionalFieldOf("light_mode", VehicleLightMode.OFF).forGetter(AutomobileData::lightMode),
            Codec.BOOL.optionalFieldOf("signal_auto_cancel", true).forGetter(AutomobileData::signalAutoCancel),
            Codec.BOOL.optionalFieldOf("comfort_signals", true).forGetter(AutomobileData::comfortSignals),
            Codec.BOOL.optionalFieldOf("lock_signal_confirmation", false).forGetter(AutomobileData::lockSignalConfirmation)
    ).apply(inst, AutomobileData::new));

    private static final AutomobileStats stats = new AutomobileStats();

    public static AutomobileData prefab(Identifier prefabName,
                                        ResourceKey<AutomobileFrame> frame,
                                        ResourceKey<AutomobileWheel> wheel,
                                        ResourceKey<AutomobileEngine> engine) {
        return prefab(prefabName, frame, wheel, engine, VehicleLightMode.OFF);
    }

    public static AutomobileData prefab(Identifier prefabName,
                                        ResourceKey<AutomobileFrame> frame,
                                        ResourceKey<AutomobileWheel> wheel,
                                        ResourceKey<AutomobileEngine> engine,
                                        VehicleLightMode lightMode) {
        return new AutomobileData(Optional.of(prefabName), frame, wheel, engine, lightMode);
    }

    public static AutomobileData prefab(Identifier prefabName,
                                        ResourceKey<AutomobileFrame> frame,
                                        ResourceKey<AutomobileWheel> wheel,
                                        ResourceKey<AutomobileEngine> engine,
                                        VehicleLightMode lightMode, boolean signalAutoCancel) {
        return new AutomobileData(Optional.of(prefabName), frame, wheel, engine, lightMode, signalAutoCancel);
    }

    public static AutomobileData prefab(Identifier prefabName,
                                        ResourceKey<AutomobileFrame> frame,
                                        ResourceKey<AutomobileWheel> wheel,
                                        ResourceKey<AutomobileEngine> engine,
                                        VehicleLightMode lightMode, boolean signalAutoCancel,
                                        boolean comfortSignals, boolean lockSignalConfirmation) {
        return new AutomobileData(Optional.of(prefabName), frame, wheel, engine, lightMode,
                signalAutoCancel, comfortSignals, lockSignalConfirmation);
    }

    public ItemStack asStack() {
        var stack = AutomobilityItems.AUTOMOBILE.require().getDefaultInstance();
        stack.set(AutomobilityItems.COMPONENT_AUTOMOBILE_DATA.require(), this);

        if (this.prefabName().isPresent()) {
            var name = this.prefabName().get();
            stack.set(DataComponents.ITEM_NAME, Component.translatable(String.format(
                    "prefab.%s.%s", name.getNamespace(), name.getPath()
            )));
        }

        return stack;
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag tooltipFlag, DataComponentGetter componentGetter) {
        var frame = context.registries().lookupOrThrow(AutomobileFrame.REGISTRY).get(frame());
        var wheel = context.registries().lookupOrThrow(AutomobileWheel.REGISTRY).get(wheel());
        var engine = context.registries().lookupOrThrow(AutomobileEngine.REGISTRY).get(engine());

        if (Minecraft.getInstance().hasShiftDown()) {
            stats.from(
                    frame.map(Holder.Reference::value).orElse(AutomobileFrame.EMPTY),
                    wheel.map(Holder.Reference::value).orElse(AutomobileWheel.EMPTY),
                    engine.map(Holder.Reference::value).orElse(AutomobileEngine.EMPTY)
            );
            stats.appendTexts(tooltip, stats);
        } else {
            if (prefabName().isEmpty()) {
                tooltip.accept(
                        Component.translatable("tooltip.automobility.frameLabel").withStyle(ChatFormatting.BLUE)
                                .append(Component.translatable(AutomobileFrame.getTranslationKey(frame().identifier())).withStyle(ChatFormatting.DARK_GREEN))
                );
                tooltip.accept(
                        Component.translatable("tooltip.automobility.wheelLabel").withStyle(ChatFormatting.BLUE)
                                .append(Component.translatable(AutomobileWheel.getTranslationKey(wheel().identifier())).withStyle(ChatFormatting.DARK_GREEN))
                );
                tooltip.accept(
                        Component.translatable("tooltip.automobility.engineLabel").withStyle(ChatFormatting.BLUE)
                                .append(Component.translatable(AutomobileEngine.getTranslationKey(engine().identifier())).withStyle(ChatFormatting.DARK_GREEN))
                );
            }
            tooltip.accept(Component.translatable("tooltip.automobility.shiftForStats").withStyle(ChatFormatting.GOLD));
        }
    }
}
