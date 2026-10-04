package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.Automobility;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class SingleSlotScreen extends AbstractContainerScreen<SingleSlotScreenHandler> implements MenuAccess<SingleSlotScreenHandler> {
    private static final Identifier TEXTURE = Automobility.rl("textures/gui/container/single_slot.png");

    public SingleSlotScreen(SingleSlotScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 176, 140);

        this.inventoryLabelY = 47;
        this.titleLabelX = 60;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }
}
