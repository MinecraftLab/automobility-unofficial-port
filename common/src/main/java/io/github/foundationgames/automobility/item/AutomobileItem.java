package io.github.foundationgames.automobility.item;

import io.github.foundationgames.automobility.automobile.AutomobileData;
import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import io.github.foundationgames.automobility.automobile.AutomobileWheel;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.entity.AutomobilityEntities;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

public class AutomobileItem extends Item implements CustomCreativeOutput {
    public static final List<AutomobileData> PREFABS = new ArrayList<>();

    public AutomobileItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.getLevel().isClientSide()) {
            var stack = context.getItemInHand();
            var data = resolveData(stack.get(AutomobilityItems.COMPONENT_AUTOMOBILE_DATA.require()));
            var e = new AutomobileEntity(AutomobilityEntities.AUTOMOBILE.require(), context.getLevel());
            var pos = context.getClickLocation();
            e.setPos(pos);
            e.setYRot(context.getHorizontalDirection().toYRot());
            e.setXRot(0);

            var frame = context.getLevel().registryAccess().lookupOrThrow(AutomobileFrame.REGISTRY).get(data.frame())
                    .map(r -> (Holder<AutomobileFrame>)r).orElseGet(() -> Holder.direct(AutomobileFrame.EMPTY));
            var wheel = context.getLevel().registryAccess().lookupOrThrow(AutomobileWheel.REGISTRY).get(data.wheel())
                    .map(r -> (Holder<AutomobileWheel>)r).orElseGet(() -> Holder.direct(AutomobileWheel.EMPTY));
            var engine = context.getLevel().registryAccess().lookupOrThrow(AutomobileEngine.REGISTRY).get(data.engine())
                    .map(r -> (Holder<AutomobileEngine>)r).orElseGet(() -> Holder.direct(AutomobileEngine.EMPTY));
            e.setComponents(frame, wheel, engine);

            context.getLevel().addFreshEntity(e);
            stack.shrink(1);
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    public static void addPrefabs(AutomobileData ... prefabs) {
        PREFABS.addAll(Arrays.asList(prefabs));
    }

    static AutomobileData resolveData(@Nullable AutomobileData data) {
        return data != null ? data : AutomobileData.DEFAULT;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        var data = stack.get(AutomobilityItems.COMPONENT_AUTOMOBILE_DATA.require());

        if (data != null) {
            data.addToTooltip(context, tooltipComponents, tooltipFlag, stack);
        }

        super.appendHoverText(stack, context, display, tooltipComponents, tooltipFlag);
    }

    @Override
    public void provideCreativeOutput(Consumer<ItemStack> output, HolderLookup.Provider registries) {
        for (var prefab : PREFABS) {
            output.accept(prefab.asStack());
        }
    }
}
