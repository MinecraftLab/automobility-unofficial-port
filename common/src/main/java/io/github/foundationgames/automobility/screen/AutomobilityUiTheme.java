package io.github.foundationgames.automobility.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared native Minecraft surfaces. Widgets retain vanilla input and narration. */
public final class AutomobilityUiTheme {
    public static final int TEXT = 0xFF202020, MUTED = 0xFF454545, ACCENT = 0xFF285B25;
    public static final int PANEL = 0xFFC6C6C6;
    private AutomobilityUiTheme() {}
    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF373737);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, 0xFFFFFFFF);
        graphics.fill(x + 2, y + height - 2, x + width - 1, y + height - 1, 0xFF777777);
        graphics.fill(x + width - 2, y + 2, x + width - 1, y + height - 1, 0xFF777777);
    }
    public static void divider(GuiGraphicsExtractor graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, 0xFF777777);
        graphics.fill(x, y + 1, x + width, y + 2, 0xFFE4E4E4);
    }
}
