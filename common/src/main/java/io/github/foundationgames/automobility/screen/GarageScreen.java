package io.github.foundationgames.automobility.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

/** Native inventory controls only; part replacement is performed by the server menu. */
public final class GarageScreen extends AbstractContainerScreen<GarageScreenHandler> {
    private static final int TEXT = 0xFF292B30;
    private static final int MUTED = 0xFF555B64;
    private Button replaceButton;
    private Component hoveredClippedText;
    private int labelMouseX;
    private int labelMouseY;

    public GarageScreen(GarageScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 248, 244);
    }

    @Override
    protected void init() {
        super.init();
        this.replaceButton = this.addRenderableWidget(Button.builder(text("replace"), button -> {
            if (this.minecraft != null && this.minecraft.gameMode != null && isReady()) {
                // Never run clickMenuButton locally: that would turn a preview into a client mutation.
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
            }
        }).bounds(this.leftPos + 64, this.topPos + 102, 120, 20).build());
        updateButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButton();
    }

    private boolean isReady() {
        return "READY".equals(this.menu.getStatus().name());
    }

    private Component statusText() {
        return Component.translatable("garage.automobility.status." + this.menu.getStatus().name().toLowerCase(Locale.ROOT),
                this.menu.getRequiredCount());
    }

    private void updateButton() {
        if (this.replaceButton != null) {
            this.replaceButton.active = isReady();
            this.replaceButton.setTooltip(Tooltip.create(statusText()));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.hoveredClippedText = null;
        this.labelMouseX = mouseX - this.leftPos;
        this.labelMouseY = mouseY - this.topPos;
        super.extractBackground(graphics, mouseX, mouseY, delta);
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF393B40);
        graphics.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, 0xFFF2F2F2);
        graphics.fill(x + 3, y + 3, x + this.imageWidth - 3, y + this.imageHeight - 3, 0xFFC6C6C6);
        graphics.fill(x + 8, y + 53, x + 240, y + 101, 0xFFB8BDC1);
        graphics.fill(x + 8, y + 135, x + 240, y + 148, 0xFFB8BDC1);
        for (int index = 0; index < this.menu.slots.size(); index++) {
            var slot = this.menu.slots.get(index);
            drawSlot(graphics, x + slot.x, y + slot.y, index >= 2 && index <= 4);
        }
        clippedText(graphics, this.title, 8, 7, 232, TEXT);
        clippedText(graphics, this.menu.vehicleName(), 8, 18, 232, MUTED);
        clippedText(graphics, text("frame"), 20, 46, 36, MUTED);
        clippedText(graphics, text("wheels"), 56, 46, 36, MUTED);
        clippedText(graphics, text("engine"), 92, 46, 40, MUTED);
        clippedText(graphics, text("base_estimates"), 11, 55, 126, MUTED);
        clippedText(graphics, text("current"), 144, 55, 42, MUTED);
        clippedText(graphics, text("preview"), 195, 55, 42, MUTED);
        var current = this.menu.currentStats();
        var preview = this.menu.previewStats();
        drawStat(graphics, "acceleration", 65, current == null ? Float.NaN : current.getAcceleration(), preview == null ? Float.NaN : preview.getAcceleration());
        drawStat(graphics, "speed", 74, current == null ? Float.NaN : current.getComfortableSpeed() * 20, preview == null ? Float.NaN : preview.getComfortableSpeed() * 20);
        drawStat(graphics, "handling", 83, current == null ? Float.NaN : current.getHandling(), preview == null ? Float.NaN : preview.getHandling());
        drawStat(graphics, "grip", 92, current == null ? Float.NaN : current.getGrip(), preview == null ? Float.NaN : preview.getGrip());
        clippedText(graphics, text("input"), 9, 125, 48, MUTED);
        clippedText(graphics, Component.translatable("garage.automobility.required", this.menu.getRequiredCount()), 65, 125, 118, MUTED);
        clippedText(graphics, text("output"), 193, 125, 47, MUTED);
        clippedText(graphics, statusText(), 11, 138, 226, isReady() ? 0xFF205B35 : 0xFF6A4030);
        clippedText(graphics, this.playerInventoryTitle, 44, 152, 160, TEXT);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Labels above use absolute panel coordinates; suppress the default overlapping labels.
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int x = mouseX - this.leftPos;
        int y = mouseY - this.topPos;
        if (x >= 8 && x < 240 && y >= 135 && y < 148) {
            graphics.setTooltipForNextFrame(this.font, statusText(), mouseX, mouseY);
        } else if (x >= 8 && x < 240 && y >= 53 && y < 101) {
            var help = text("estimate_help");
            var tooltip = this.hoveredClippedText == null ? help
                    : Component.empty().append(this.hoveredClippedText).append("\n").append(help);
            graphics.setTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
        } else if (x >= 8 && x < 240 && y >= 18 && y < 27) {
            graphics.setTooltipForNextFrame(this.font, this.menu.vehicleName(), mouseX, mouseY);
        } else if (this.hoveredClippedText != null) {
            graphics.setTooltipForNextFrame(this.font, this.hoveredClippedText, mouseX, mouseY);
        }
    }

    private void drawStat(GuiGraphicsExtractor graphics, String key, int y, float current, float preview) {
        clippedText(graphics, text("stat." + key), 11, y, 126, TEXT);
        clippedText(graphics, Component.literal(number(current)), 144, y, 45, TEXT);
        int color = !Float.isFinite(current) || !Float.isFinite(preview) || Math.abs(preview - current) < 0.005f
                ? MUTED : preview > current ? 0xFF205B35 : 0xFF8A3434;
        clippedText(graphics, Component.literal(number(preview)), 195, y, 42, color);
    }

    private static String number(float value) {
        return Float.isFinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "—";
    }

    private void clippedText(GuiGraphicsExtractor graphics, Component value, int x, int y, int width, int color) {
        var layout = GarageTextLayout.fit(this.font, value.getString(), width);
        int measuredWidth = layout.fullWidth();
        if (GarageTextTooltip.isClipped(measuredWidth, width)) {
            // Use the shaped label's measured width and retain the original full component for its tooltip.
            if (GarageTextTooltip.isHoveredClipped(measuredWidth, x, y, width, this.font.lineHeight,
                    this.labelMouseX, this.labelMouseY)) {
                this.hoveredClippedText = value;
            }
        }
        graphics.text(this.font, layout.visualOrder(), this.leftPos + x, this.topPos + y, color, false);
    }

    private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y, boolean installedPart) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, installedPart ? 0xFF647780 : 0xFF373737);
        graphics.fill(x, y, x + 17, y + 17, 0xFFFFFFFF);
        graphics.fill(x, y, x + 16, y + 16, installedPart ? 0xFF9BA8AE : 0xFF8B8B8B);
    }

    private static Component text(String key) {
        return Component.translatable("garage.automobility." + key);
    }
}
