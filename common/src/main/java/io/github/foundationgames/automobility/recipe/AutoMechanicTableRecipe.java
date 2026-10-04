package io.github.foundationgames.automobility.recipe;

import io.github.foundationgames.automobility.Automobility;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class AutoMechanicTableRecipe implements Recipe<ContainerRecipeInput>, Comparable<AutoMechanicTableRecipe> {
    public static final Identifier ID = Automobility.rl("auto_mechanic_table");
    public static final RecipeType<AutoMechanicTableRecipe> TYPE = new RecipeType<>() {};

    protected final Identifier category;
    protected final List<Ingredient> ingredients;
    protected final List<AutoMechanicIngredient> autoMechanicIngredients;
    protected final AutoMechanicTableRecipeSerializer.AutoComponentResult result;
    protected final int sortNum;

    public @Nullable Identifier sortId;

    public AutoMechanicTableRecipe(Identifier category, List<Ingredient> ingredients, AutoMechanicTableRecipeSerializer.AutoComponentResult result, int sortNum) {
        this(category, ingredients.stream().map(AutoMechanicIngredient::vanilla).toList(), result, sortNum);
    }

    private AutoMechanicTableRecipe(Identifier category, Collection<AutoMechanicIngredient> ingredients,
            AutoMechanicTableRecipeSerializer.AutoComponentResult result, int sortNum) {
        this.category = category;
        this.autoMechanicIngredients = List.copyOf(ingredients);
        this.ingredients = ingredients.stream().map(AutoMechanicIngredient::ingredient).toList();
        this.result = result;
        this.sortNum = sortNum;
    }

    public AutoMechanicTableRecipe(Identifier category, List<Ingredient> ingredients, ItemStack result, int sortNum) {
        this(category, ingredients, AutoMechanicTableRecipeSerializer.AutoComponentResult.fromStack(result), sortNum);
    }

    static AutoMechanicTableRecipe fromComponentIngredients(Identifier category,
            List<AutoMechanicIngredient> ingredients,
            AutoMechanicTableRecipeSerializer.AutoComponentResult result, int sortNum) {
        return new AutoMechanicTableRecipe(category, ingredients, result, sortNum);
    }

    public Identifier getCategory() {
        return this.category;
    }

    @Override
    public boolean matches(ContainerRecipeInput inv, Level lvl) {
        boolean[] result = {true};
        this.forMissingAutoMechanicIngredients(inv, ingredient -> result[0] = false);

        return result[0];
    }

    @Override
    public ItemStack assemble(ContainerRecipeInput inv) {
        for (var ing : this.autoMechanicIngredients) {
            for (int i = 0; i < inv.size(); i++) {
                var stack = inv.getItem(i);
                if (ing.test(stack)) {
                    stack.shrink(1);
                    break;
                }
            }
        }

        return this.result.createStack();
    }

    public ItemStack getResultItem() {
        return this.result.createStack();
    }

    public AutoMechanicTableRecipeSerializer.AutoComponentResult getResultDescriptor() {
        return this.result;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return this.category.toString();
    }

    @Override
    public RecipeSerializer<? extends Recipe<ContainerRecipeInput>> getSerializer() {
        return AutoMechanicTableRecipeSerializer.INSTANCE;
    }

    @Override
    public RecipeType<? extends Recipe<ContainerRecipeInput>> getType() {
        return TYPE;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(this.ingredients);
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    public void forMissingIngredients(ContainerRecipeInput inv, Consumer<Ingredient> action) {
        this.forMissingAutoMechanicIngredients(inv, ingredient -> action.accept(ingredient.ingredient()));
    }

    public void forMissingAutoMechanicIngredients(ContainerRecipeInput inv, Consumer<AutoMechanicIngredient> action) {
        var invCopy = new ArrayList<ItemStack>();
        for (int i = 0; i < inv.size(); i++) {
            invCopy.add(inv.getItem(i).copy());
        }

        for (var ing : this.autoMechanicIngredients) {
            var matchingStack = invCopy.stream().filter(ing).findFirst();
            if (matchingStack.isEmpty()) {
                action.accept(ing);
            } else {
                var stack = matchingStack.get();
                stack.shrink(1);
                if (stack.isEmpty()) {
                    invCopy.remove(stack);
                }
            }
        }
    }

    @Override
    public int compareTo(@NotNull AutoMechanicTableRecipe o) {
        int diff = this.getCategory().compareTo(o.getCategory());
        if (diff != 0) return diff;

        diff = Integer.compare(this.sortNum, o.sortNum);
        if (diff != 0) return diff;

        if (this.sortId != null && o.sortId != null) {
            return this.sortId.compareTo(o.sortId);
        }

        return this.result.item().getRegisteredName().compareTo(o.result.item().getRegisteredName());
    }
}
