package io.github.foundationgames.automobility.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Custom material with vanilla Button input, focus, tooltip and narration behavior. */
public final class GarageUiButton extends Button {
    public enum Style { PRIMARY, SECONDARY, QUIET, TAB, RAIL }

    @FunctionalInterface
    public interface ContentRenderer {
        void draw(GuiGraphicsExtractor graphics, GarageUiButton button, int mouseX, int mouseY, float delta);
    }

    private final Style style;
    private boolean selected;
    private ContentRenderer contentRenderer;

    public GarageUiButton(GarageUiLayout.Box bounds, Component message, OnPress onPress, Style style) {
        super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), message, onPress, DEFAULT_NARRATION);
        this.style = style;
    }

    public void setSelected(boolean selected) { this.selected = selected; }
    public boolean isSelected() { return selected; }
    public void setContentRenderer(ContentRenderer renderer) { this.contentRenderer = renderer; }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (contentRenderer != null) {
            contentRenderer.draw(graphics, this, mouseX, mouseY, delta);
            return;
        }
        GarageUiDraw.button(graphics, Minecraft.getInstance().font,
                new GarageUiLayout.Box(getX(), getY(), getWidth(), getHeight()), getMessage(), style,
                active, isHovered(), isFocused(), selected, getAlpha());
    }
}
