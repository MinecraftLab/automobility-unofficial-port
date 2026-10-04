package io.github.foundationgames.automobility.recipe;

import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import io.github.foundationgames.automobility.item.AutomobileFrameItem;
import io.github.foundationgames.automobility.item.AutomobilityItems;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoMechanicTableRecipeTest {
    private static AutomobileFrameItem frameItem;

    private static AutoMechanicTableRecipe duplicateIronRecipe() {
        var iron = Ingredient.of(Items.IRON_INGOT);
        return new AutoMechanicTableRecipe(
                Identifier.fromNamespaceAndPath("automobility-test", "duplicate_ingredient"),
                List.of(iron, iron),
                new ItemStack(Items.STICK),
                0
        );
    }

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Items.IRON_INGOT.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        Items.STICK.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);

        if (AutomobilityItems.COMPONENT_FRAME.get().isEmpty()) {
            AutomobilityItems.COMPONENT_FRAME.create();
        }
        frameItem = AutomobilityItems.AUTOMOBILE_FRAME.get()
                .orElseGet(AutomobilityItems.AUTOMOBILE_FRAME::create);
        frameItem.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
    }

    @Test
    void duplicateIngredientsCanUseMultipleItemsFromOneStack() {
        var stack = new ItemStack(Items.IRON_INGOT, 2);
        var input = new ContainerRecipeInput(new SimpleContainer(stack));

        assertTrue(duplicateIronRecipe().matches(input, null));
        assertEquals(2, stack.getCount(), "Matching must not consume the real inventory");
    }

    @Test
    void duplicateIngredientsRejectAnInsufficientStack() {
        var input = new ContainerRecipeInput(new SimpleContainer(new ItemStack(Items.IRON_INGOT, 1)));

        assertFalse(duplicateIronRecipe().matches(input, null));
    }

    @Test
    void componentIngredientAcceptsOnlyTheRequestedFrameVariant() {
        var ingredient = AutoMechanicIngredient.component(
                Ingredient.of(frameItem), AutomobileFrame.WOODEN_MOTORCAR.identifier());

        assertTrue(ingredient.test(frameItem.createStack(AutomobileFrame.WOODEN_MOTORCAR)));
        assertFalse(ingredient.test(frameItem.createStack(AutomobileFrame.STEEL_MOTORCAR)));
        assertEquals(
                AutomobileFrame.WOODEN_MOTORCAR,
                frameItem.createStack(AutomobileFrame.WOODEN_MOTORCAR)
                        .get(AutomobilityItems.COMPONENT_FRAME.require()));
    }

    @Test
    void componentIngredientProducesAComponentSpecificDisplayStack() {
        var ingredient = AutoMechanicIngredient.component(
                Ingredient.of(frameItem), AutomobileFrame.GOLDEN_MOTORCAR.identifier());
        var displayStack = ingredient.displayStacks().getFirst();

        assertEquals(AutomobileFrame.GOLDEN_MOTORCAR,
                displayStack.get(AutomobilityItems.COMPONENT_FRAME.require()));
    }

}
