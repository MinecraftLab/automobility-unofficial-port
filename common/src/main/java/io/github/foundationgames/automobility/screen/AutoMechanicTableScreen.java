package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.recipe.AutoMechanicIngredient;
import io.github.foundationgames.automobility.recipe.AutoMechanicTableRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AutoMechanicTableScreen extends AbstractContainerScreen<AutoMechanicTableScreenHandler> {
    private static final Identifier TEXTURE = Automobility.rl("textures/gui/container/auto_mechanic_table.png");

    private static final int RECIPE_BUTTON_SIZE = 17;
    private static final int RECIPE_PANEL_WIDTH = 85;
    private static final int RECIPE_PANEL_HEIGHT = 51;

    private static final int CATEGORY_BUTTON_WIDTH = 12;
    private static final int CATEGORY_BUTTON_HEIGHT = 15;
    private static final int CATEGORY_BUTTON_AREA_WIDTH = 91;

    private static final int SCROLL_BAR_WIDTH = 3;
    private static final int SCROLL_BAR_HEIGHT = 10;
    private static final int SCROLL_BAR_AREA_HEIGHT = 51;

    private long time = 0;

    private int recipePanelX;
    private int recipePanelY;

    private int categoryButtonsX;
    private int categoryButtonsY;

    private int currentCategory = 0;
    private int recipeScroll = 0;
    private final List<Identifier> orderedCategories = createDefaultCategories();
    private final Map<Identifier, List<RecipeEntry>> recipes = new HashMap<>();

    private FormattedCharSequence categoryTitle;
    private ItemStack hoveredMissingIngredient = null;

    public AutoMechanicTableScreen(AutoMechanicTableScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 176, 209);

        this.titleLabelY = 8;

        for (int id = 0; id < handler.recipes.size(); id++) {
            var recipe = handler.recipes.get(id);
            var category = recipe.getCategory();

            this.recipes.computeIfAbsent(category, cat -> new ArrayList<>());
            if (!this.orderedCategories.contains(category)) {
                this.orderedCategories.add(category);
            }

            this.recipes.get(category).add(new RecipeEntry(id, recipe));
        }

        this.inventoryLabelY = this.topPos + 115;
    }

    private static List<Identifier> createDefaultCategories() {
        var list = new ArrayList<Identifier>();
        list.add(Automobility.rl("frames"));
        list.add(Automobility.rl("engines"));
        list.add(Automobility.rl("wheels"));

        return list;
    }

    @Override
    protected void init() {
        super.init();

        this.recipePanelX = this.leftPos + 76;
        this.recipePanelY = this.topPos + 21;

        this.categoryButtonsX = this.leftPos + 75;
        this.categoryButtonsY = this.topPos + 4;

        this.categoryTitle = this.createCategoryTitle(this.orderedCategories.get(0));
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        this.time++;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
        this.drawCategoryBar(graphics, mouseX, mouseY);
        this.drawRecipes(graphics, mouseX, mouseY);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractContents(graphics, mouseX, mouseY, delta);
        this.drawMissingIngredients(graphics);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mx, int my) {
        super.extractTooltip(graphics, mx, my);
        int hoveredRecipe = this.getHoveredRecipe(mx, my);
        if (hoveredRecipe >= 0) {
            graphics.setTooltipForNextFrame(font, this.menu.recipes.get(hoveredRecipe).getResultItem(), mx, my);
        }

        if (this.hoveredMissingIngredient != null && this.minecraft != null) {
            var tt = getTooltipFromItem(minecraft, hoveredMissingIngredient);
            if (!tt.isEmpty()) {
                tt.set(0, tt.getFirst().copy().withStyle(ChatFormatting.RED));
            }

            graphics.setTooltipForNextFrame(minecraft.font, tt, hoveredMissingIngredient.getTooltipImage(), mx, my);
        }
    }

    private void changeCategory(int by) {
        this.currentCategory = Math.floorMod((this.currentCategory + by), this.orderedCategories.size());
        this.categoryTitle = createCategoryTitle(this.orderedCategories.get(this.currentCategory));
        this.recipeScroll = 0;
    }

    private FormattedCharSequence createCategoryTitle(Identifier category) {
        var translated = I18n.get("part_category."+category.getNamespace()+"."+category.getPath());
        if (this.font.width(translated) > 64) {
            return Component.literal(this.font.plainSubstrByWidth(translated, 57) + "...").getVisualOrderText();
        }
        return Component.literal(this.font.plainSubstrByWidth(translated, 64)).getVisualOrderText();
    }

    private void buttonClicked() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
            int selectedCatButton = getHoveredCategoryButton((int) mouseX, (int) mouseY);
            if (selectedCatButton != 0) {
                this.changeCategory(selectedCatButton);
                this.buttonClicked();

                return true;
            }

            int recipe = this.getHoveredRecipe((int) mouseX, (int) mouseY);
            if (recipe >= 0) {
                this.selectRecipe(recipe);
                this.buttonClicked();

                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void selectRecipe(int id) {
        this.menu.clickMenuButton(this.minecraft.player, id);
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
    }

    private int getMaxRecipeScroll() {
        return Math.max(0, Mth.ceil((float)this.getRecipeList().size() / 5) - 3);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && this.getHoveredRecipe((int) mouseX, (int) mouseY) >= -1) {
            this.recipeScroll += scrollY > 0 ? -1 : 1;
            this.recipeScroll = Mth.clamp(this.recipeScroll, 0, this.getMaxRecipeScroll());

            return true;
        }

        return false;
    }

    protected final void drawMissingIngredient(GuiGraphicsExtractor graphics, AutoMechanicIngredient ing, int x, int y, boolean hovered) {
        graphics.fill(x, y, x + 16, y + 16, 0x45FF0000);

        var stacks = ing.displayStacks();
        if (stacks.isEmpty()) {
            return;
        }
        var stack = stacks.get(Mth.floor((float)this.time / 30) % stacks.size());
        graphics.fakeItem(stack, x, y);

        graphics.fill(x, y, x + 16, y + 16, 0x30FFFFFF);

        if (hovered) {
            this.hoveredMissingIngredient = stack;
        }
    }

    protected void drawMissingIngredients(GuiGraphicsExtractor graphics) {
        var inputInv = this.menu.inputInv;
        var missingIngs = new ArrayDeque<>(this.menu.missingIngredients);
        this.hoveredMissingIngredient = null;

        for (int i = 0; i < inputInv.getContainerSize(); i++) if (!missingIngs.isEmpty()) {
            int x = this.leftPos + 8 + (i * 18);
            int y = this.topPos + 88;

            if (inputInv.getItem(i).isEmpty()) {
                var ing = missingIngs.removeFirst();
                this.drawMissingIngredient(graphics, ing, x, y, hoveredSlot != null && hoveredSlot.index == i);
            }
        }
    }

    protected List<RecipeEntry> getRecipeList() {
        if (this.currentCategory < this.orderedCategories.size() && this.currentCategory >= 0) {
            return Objects.requireNonNullElseGet(this.recipes.get(this.orderedCategories.get(this.currentCategory)), Collections::emptyList);
        }

        return Collections.emptyList();
    }

    protected int getHoveredCategoryButton(int mouseX, int mouseY) {
        if (mouseY > this.categoryButtonsY && mouseY < this.categoryButtonsY + CATEGORY_BUTTON_HEIGHT) {
            int relX = mouseX - this.categoryButtonsX;
            if (relX < 0 || relX > CATEGORY_BUTTON_AREA_WIDTH) {
                return 0;
            }

            if (relX < CATEGORY_BUTTON_WIDTH) {
                return -1;
            }
            if (relX > (CATEGORY_BUTTON_AREA_WIDTH - CATEGORY_BUTTON_WIDTH)) {
                return 1;
            }
        }

        return 0;
    }

    protected int getHoveredRecipe(int mouseX, int mouseY) {
        mouseX -= this.recipePanelX;
        mouseY -= this.recipePanelY;

        if (this.currentCategory < this.orderedCategories.size() && this.currentCategory >= 0 &&
                (mouseX >= 0 && mouseX < RECIPE_PANEL_WIDTH) && (mouseY >= 0 && mouseY < RECIPE_PANEL_HEIGHT)) {
            int row = Mth.floor((float)mouseY / RECIPE_BUTTON_SIZE);
            int col = Mth.floor((float)mouseX / RECIPE_BUTTON_SIZE);

            if (row >= 0 && col >= 0) {
                int idx = (5 * (row + this.recipeScroll)) + col;
                var recipes = this.recipes.get(this.orderedCategories.get(this.currentCategory));
                if (idx < recipes.size()) {
                    return recipes.get(idx).id();
                }

                return -1; // Still within the recipe box bounds, but no recipe
            }
        }

        return -2;
    }

    protected void drawCategoryBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int hoveredCatButton = this.getHoveredCategoryButton(mouseX, mouseY);

        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.categoryButtonsX, this.categoryButtonsY,
                176, 17 + (hoveredCatButton < 0 ? CATEGORY_BUTTON_HEIGHT : 0), CATEGORY_BUTTON_WIDTH, CATEGORY_BUTTON_HEIGHT, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.categoryButtonsX + (CATEGORY_BUTTON_AREA_WIDTH - CATEGORY_BUTTON_WIDTH), this.categoryButtonsY,
                188, 17 + (hoveredCatButton > 0 ? CATEGORY_BUTTON_HEIGHT : 0), CATEGORY_BUTTON_WIDTH, CATEGORY_BUTTON_HEIGHT, 256, 256);

        if (this.categoryTitle != null) {
            graphics.centeredText(this.font, this.categoryTitle, this.leftPos + 120, this.topPos + 8, 0xFFFFFF);
        }
    }

    protected void drawRecipes(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.orderedCategories.size() > 0) {
            var recipes = this.recipes.get(this.orderedCategories.get(this.currentCategory));

            if (recipes != null) for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 5; col++) {
                    int idx = (5 * this.recipeScroll) + (5 * row) + col;

                    if (idx < recipes.size()) {
                        int x = (col * RECIPE_BUTTON_SIZE) + this.recipePanelX;
                        int y = (row * RECIPE_BUTTON_SIZE) + this.recipePanelY;

                        var entry = recipes.get(idx);

                        var state = RecipeButtonState.DEFAULT;
                        if (this.menu.getSelectedRecipe().isPresent() &&
                                this.menu.getSelectedRecipeId() == entry.id()) {
                            state = RecipeButtonState.SELECTED;
                        } else if (this.getHoveredRecipe(mouseX, mouseY) == entry.id()) {
                            state = RecipeButtonState.HOVERED;
                        }

                        this.drawRecipeEntry(entry, graphics, x, y, state);
                    } else {
                        break;
                    }
                }
            }
        }

        int maxScroll = this.getMaxRecipeScroll();

        int scrollBarX = this.leftPos + 162;
        int scrollBarY = this.topPos + 21;
        if (maxScroll > 0) {
            scrollBarY += (int)((SCROLL_BAR_AREA_HEIGHT - SCROLL_BAR_HEIGHT) * ((float)this.recipeScroll / maxScroll));
        }

        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, scrollBarX, scrollBarY, 227, 0, SCROLL_BAR_WIDTH, SCROLL_BAR_HEIGHT, 256, 256);
    }

    protected void drawRecipeEntry(RecipeEntry entry, GuiGraphicsExtractor graphics, int x, int y, RecipeButtonState state) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 176 + (state.ordinal() * RECIPE_BUTTON_SIZE), 0, RECIPE_BUTTON_SIZE, RECIPE_BUTTON_SIZE, 256, 256);

        var stack = entry.recipe.getResultItem();
        graphics.fakeItem(stack, x, y);
    }

    public record RecipeEntry(int id, AutoMechanicTableRecipe recipe) {}

    public enum RecipeButtonState {
        DEFAULT, HOVERED, SELECTED
    }
}
