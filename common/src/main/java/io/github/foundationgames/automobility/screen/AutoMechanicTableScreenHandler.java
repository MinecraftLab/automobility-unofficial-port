package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.block.AutomobilityBlocks;
import io.github.foundationgames.automobility.recipe.AutoMechanicIngredient;
import io.github.foundationgames.automobility.recipe.AutoMechanicTableRecipe;
import io.github.foundationgames.automobility.recipe.AutoMechanicTableRecipeSerializer;
import io.github.foundationgames.automobility.recipe.ContainerRecipeInput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AutoMechanicTableScreenHandler extends AbstractContainerMenu {
    private final Level world;
    private final ContainerLevelAccess context;
    private final DataSlot selectedRecipe = DataSlot.standalone();

    public List<AutoMechanicTableRecipe> recipes;

    public final List<AutoMechanicIngredient> missingIngredients = new ArrayList<>();
    public final SimpleContainer inputInv;
    public final Slot outputSlot;

    private final int playerInvSlot;

    public AutoMechanicTableScreenHandler(int syncId, Inventory playerInv) {
        this(syncId, playerInv, ContainerLevelAccess.NULL, OpeningData.EMPTY);
    }

    public AutoMechanicTableScreenHandler(int syncId, Inventory playerInv, OpeningData openingData) {
        this(syncId, playerInv, ContainerLevelAccess.NULL, openingData);
    }

    public AutoMechanicTableScreenHandler(int syncId, Inventory playerInv, ContainerLevelAccess ctx) {
        this(syncId, playerInv, ctx, OpeningData.fromLevel(playerInv.player.level()));
    }

    private AutoMechanicTableScreenHandler(int syncId, Inventory playerInv, ContainerLevelAccess ctx, OpeningData openingData) {
        super(Automobility.AUTO_MECHANIC_SCREEN.require("Auto mechanic screen not registered!"), syncId);
        this.world = playerInv.player.level();
        this.context = ctx;
        this.inputInv = new SimpleContainer(9) {
            @Override public void setChanged() { AutoMechanicTableScreenHandler.this.onInputUpdated(); }
        };

        for(int s = 0; s < 9; s++) {
            this.addSlot(new InputSlot(this.inputInv, s, 8 + (s * 18), 88));
        }
        this.outputSlot = this.addSlot(new OutputSlot(new SimpleContainer(1), 0, 26, 38));

        this.playerInvSlot = this.slots.size();
        int playerInvY = 127;
        for(int row = 0; row < 3; row++) {
            for(int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + (row * 9) + 9, 8 + (col * 18), playerInvY + (row * 18)));
            }
        }
        for(int s = 0; s < 9; s++) {
            this.addSlot(new Slot(playerInv, s, 8 + (s * 18), playerInvY + 58));
        }

        this.recipes = openingData.toRecipes();
        Collections.sort(this.recipes);

        this.selectedRecipe.set(-1);
        this.addDataSlot(this.selectedRecipe);
    }

    public Optional<AutoMechanicTableRecipe> getSelectedRecipe() {
        int id = this.selectedRecipe.get();
        return (id >= 0 && this.recipes.size() > 0 && id < this.recipes.size()) ? Optional.of(this.recipes.get(id)) : Optional.empty();
    }

    public int getSelectedRecipeId() {
        return this.selectedRecipe.get();
    }

    private void updateMissingIngredients() {
        this.missingIngredients.clear();

        var ctr = new ContainerRecipeInput(this.inputInv);
        this.getSelectedRecipe().ifPresent(recipe ->
                recipe.forMissingAutoMechanicIngredients(ctr, this.missingIngredients::add));
    }

    private void updateRecipeState() {
        this.updateMissingIngredients();

        var ctr = new ContainerRecipeInput(this.inputInv);
        this.getSelectedRecipe().ifPresent(recipe -> {
            if (recipe.matches(ctr, this.world)) {
                this.outputSlot.set(recipe.getResultItem().copy());
            } else {
                this.outputSlot.set(ItemStack.EMPTY);
            }
        });
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id < this.recipes.size()) {
            this.selectRecipe(id);
            return true;
        }

        return super.clickMenuButton(player, id);
    }

    private void onInputUpdated() {
        updateRecipeState();
    }

    private void selectRecipe(int id) {
        this.selectedRecipe.set(id);
        updateRecipeState();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);

        this.outputSlot.set(ItemStack.EMPTY);
        this.context.execute((world, pos) -> this.clearContainer(player, this.inputInv));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.context, player, AutomobilityBlocks.AUTO_MECHANIC_TABLE.require());
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.outputSlot && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int fromSlotId) {
        var newStack = ItemStack.EMPTY;
        var fromSlot = this.slots.get(fromSlotId);

        if (fromSlot.hasItem()) {
            var fromStack = fromSlot.getItem();
            var fromItem = fromStack.getItem();
            newStack = fromStack.copy();

            // Items transferred out of output slot
            if (fromSlot == this.outputSlot) {
                fromItem.onCraftedBy(fromStack, player);
                if (!this.moveItemStackTo(fromStack, this.playerInvSlot, this.playerInvSlot + 36, true)) {
                    return ItemStack.EMPTY;
                }

                fromSlot.onQuickCraft(fromStack, newStack);
            // Items transferred out of input row
            } else if (this.slots.stream().anyMatch(s -> s.container == this.inputInv && s.index == fromSlotId)) {
                if (!this.moveItemStackTo(fromStack, this.playerInvSlot, this.playerInvSlot + 36, false)) {
                    return ItemStack.EMPTY;
                }
            // Items being transferred into the input row, which match the missing ingredients
            } else if (this.missingIngredients.stream().anyMatch(ing -> ing.test(fromStack))) {
                if (!this.moveItemStackTo(fromStack, 0, 9, false)) {
                    return ItemStack.EMPTY;
                }
            // Items transferred from inventory to hotbar
            } else if (fromSlotId >= this.playerInvSlot && fromSlotId < this.playerInvSlot + 27) {
                if (!this.moveItemStackTo(fromStack, this.playerInvSlot + 27, this.playerInvSlot + 36, false)) {
                    return ItemStack.EMPTY;
                }
            // Items transferred from hotbar to inventory
            } else if (fromSlotId >= this.playerInvSlot + 27 && fromSlotId < this.playerInvSlot + 36 &&
                    !this.moveItemStackTo(fromStack, this.playerInvSlot, this.playerInvSlot + 27, false)) {
                return ItemStack.EMPTY;
            }

            if (fromStack.isEmpty()) {
                fromSlot.set(ItemStack.EMPTY);
            }
            fromSlot.setChanged();

            if (fromStack.getCount() == newStack.getCount()) {
                return ItemStack.EMPTY;
            }
            fromSlot.onTake(player, fromStack);
            this.broadcastChanges();
        }

        return newStack;
    }

    public static class InputSlot extends Slot {
        public InputSlot(Container inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }
    }

    public class OutputSlot extends Slot {
        public OutputSlot(Container inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            super.onTake(player, stack);

            AutoMechanicTableScreenHandler.this.getSelectedRecipe()
                    .ifPresent(recipe -> {
                        recipe.assemble(new ContainerRecipeInput(AutoMechanicTableScreenHandler.this.inputInv));
                        stack.getItem().onCraftedBy(stack, player);
                        AutoMechanicTableScreenHandler.this.updateRecipeState();
                    });
        }
    }

    public record RecipeData(Identifier id, AutoMechanicTableRecipe recipe) {
        public static final StreamCodec<RegistryFriendlyByteBuf, RecipeData> STREAM_CODEC =
                StreamCodec.ofMember(RecipeData::encode, RecipeData::decode);

        private void encode(RegistryFriendlyByteBuf buf) {
            buf.writeIdentifier(this.id);
            AutoMechanicTableRecipeSerializer.toNetwork(buf, this.recipe);
        }

        private static RecipeData decode(RegistryFriendlyByteBuf buf) {
            var id = buf.readIdentifier();
            var recipe = AutoMechanicTableRecipeSerializer.fromNetwork(buf);
            recipe.sortId = id;
            return new RecipeData(id, recipe);
        }
    }

    public record OpeningData(List<RecipeData> recipes) {
        public static final OpeningData EMPTY = new OpeningData(List.of());
        public static final StreamCodec<RegistryFriendlyByteBuf, OpeningData> STREAM_CODEC =
                StreamCodec.ofMember(OpeningData::encode, OpeningData::decode);

        public static OpeningData fromPlayer(ServerPlayer player) {
            return fromRecipeManager(player.level().getServer().getRecipeManager());
        }

        public static OpeningData fromLevel(Level level) {
            var server = level.getServer();
            return server == null ? EMPTY : fromRecipeManager(server.getRecipeManager());
        }

        private static OpeningData fromRecipeManager(RecipeManager recipeManager) {
            return new OpeningData(recipeManager.getRecipes().stream()
                    .filter(h -> h.value().getType() == AutoMechanicTableRecipe.TYPE)
                    .map(h -> new RecipeData(h.id().identifier(), (AutoMechanicTableRecipe) h.value()))
                    .collect(Collectors.toList()));
        }

        private void encode(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(this.recipes.size());
            for (var recipe : this.recipes) {
                RecipeData.STREAM_CODEC.encode(buf, recipe);
            }
        }

        private static OpeningData decode(RegistryFriendlyByteBuf buf) {
            int size = buf.readVarInt();
            var recipes = new ArrayList<RecipeData>(size);
            for (int i = 0; i < size; i++) {
                recipes.add(RecipeData.STREAM_CODEC.decode(buf));
            }
            return new OpeningData(recipes);
        }

        public List<AutoMechanicTableRecipe> toRecipes() {
            var result = new ArrayList<AutoMechanicTableRecipe>(this.recipes.size());
            for (var data : this.recipes) {
                data.recipe.sortId = data.id;
                result.add(data.recipe);
            }
            return result;
        }
    }
}
