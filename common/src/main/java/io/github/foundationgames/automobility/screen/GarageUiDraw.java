package io.github.foundationgames.automobility.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Native pixel geometry and restrained materials shared by the showroom's interactive widgets. */
public final class GarageUiDraw {
    public static final int TEXT = 0xFFE8ECE6, MUTED = 0xFF94A7A5, ACCENT = 0xFFD6B783;
    public static final int POSITIVE = 0xFFBDD4A1, NEGATIVE = 0xFFE9A48F;
    public static final int BACKGROUND = 0xFF10191D, PANEL = 0xFF18252A, LINE = 0xFF314249;
    public static final int INK = 0xFF172126;

    private GarageUiDraw() {}

    public enum Surface { PANEL, RAIL, HERO, DRAWER, CARD }

    public static void backdrop(GuiGraphicsExtractor graphics, GarageUiLayout.Layout layout) {
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xEB091014);
        var panel = layout.panel();
        graphics.fill(panel.x() + 3, panel.y() + 5, panel.right() + 3, panel.bottom() + 5, 0x66000000);
        surface(graphics, panel, Surface.PANEL);
        surface(graphics, layout.rail(), Surface.RAIL);
        surface(graphics, layout.hero(), Surface.HERO);
        graphics.fill(panel.x() + 12, panel.y() + 42, panel.right() - 12, panel.y() + 43, LINE);
    }

    public static void surface(GuiGraphicsExtractor graphics, GarageUiLayout.Box box, Surface surface) {
        switch (surface) {
            case PANEL -> {
                fill(graphics, box, BACKGROUND);
                graphics.outline(box.x(), box.y(), box.width(), box.height(), 0xFF35444A);
                graphics.fill(box.x() + 1, box.y() + 1, box.right() - 1, box.y() + 2, 0xFF455158);
            }
            case RAIL -> fill(graphics, box, 0xFF121E22);
            case HERO -> {
                graphics.fillGradient(box.x(), box.y(), box.right(), box.bottom(), 0xFF18282D, 0xFF101C20);
                // A quiet floor plane separates the body silhouette without a decorative grid.
                int horizon = box.y() + Math.round(box.height() * 0.66f);
                graphics.fillGradient(box.x(), horizon, box.right(), box.bottom(), 0x001D3035, 0xFF1A2B30);
            }
            case DRAWER -> {
                fill(graphics, box, 0xFF152226);
                graphics.fill(box.x(), box.y(), box.right(), box.y() + 1, LINE);
            }
            case CARD -> {
                fill(graphics, box, 0xFF1C2C31);
                graphics.outline(box.x(), box.y(), box.width(), box.height(), LINE);
            }
        }
    }

    public static void button(GuiGraphicsExtractor graphics, Font font, GarageUiLayout.Box box,
                              Component label, GarageUiButton.Style style, boolean active,
                              boolean hovered, boolean focused, boolean selected, float alpha) {
        boolean primary = style == GarageUiButton.Style.PRIMARY;
        boolean quiet = style == GarageUiButton.Style.QUIET || style == GarageUiButton.Style.TAB
                || style == GarageUiButton.Style.RAIL;
        int background = primary ? active ? hovered ? 0xFFE4CAA0 : ACCENT : 0xFF354043
                : selected ? 0xFF304044 : hovered && active ? 0xFF30434A : quiet ? 0xFF17262B : 0xFF24343A;
        int stroke = focused ? ACCENT : selected ? 0xFF899B91 : hovered && active ? 0xFF6F8789 : LINE;
        fill(graphics, box, alpha(background, alpha));
        if (!quiet || hovered || focused || selected)
            graphics.outline(box.x(), box.y(), box.width(), box.height(), alpha(stroke, alpha));
        if (primary && active)
            graphics.fill(box.x() + 1, box.y() + 1, box.right() - 1, box.y() + 2, alpha(0xFFF0DCB8, alpha));
        if (style == GarageUiButton.Style.RAIL && selected)
            graphics.fill(box.x(), box.y() + 1, box.x() + 2, box.bottom() - 1, alpha(ACCENT, alpha));
        if (style == GarageUiButton.Style.TAB && selected)
            graphics.fill(box.x() + 4, box.bottom() - 2, box.right() - 4, box.bottom() - 1, alpha(ACCENT, alpha));
        if (focused) {
            graphics.outline(box.x() + 2, box.y() + 2, Math.max(0, box.width() - 4),
                    Math.max(0, box.height() - 4), alpha(primary && active ? INK : ACCENT, alpha));
        }
        int color = !active ? MUTED : primary ? INK : TEXT;
        var fitted = GarageTextLayout.fit(font, label.getString(), Math.max(0, box.width() - 12));
        int labelWidth = font.width(fitted.visualOrder());
        graphics.text(font, fitted.visualOrder(), box.x() + (box.width() - labelWidth) / 2,
                box.y() + (box.height() - font.lineHeight) / 2 + 1, alpha(color, alpha), false);
    }

    public static void railRow(GuiGraphicsExtractor graphics, Font font, GarageUiLayout.Box box,
                               Component title, Component subtitle, boolean selected,
                               boolean hovered, boolean focused) {
        fill(graphics, box, selected ? 0xFF2B3D42 : hovered ? 0xFF203238 : 0xFF142126);
        if (selected) graphics.fill(box.x(), box.y(), box.x() + 2, box.bottom(), ACCENT);
        if (focused) graphics.outline(box.x(), box.y(), box.width(), box.height(), ACCENT);
        text(graphics, font, title, box.x() + 8, box.y() + 7, box.width() - 15, selected ? TEXT : 0xFFC1CECA);
        if (subtitle != null)
            text(graphics, font, subtitle, box.x() + 8, box.y() + 21, box.width() - 15, selected ? ACCENT : MUTED);
    }

    public static void partCard(GuiGraphicsExtractor graphics, Font font, GarageUiLayout.Box box,
                                Component title, Component name, Component availability, Component delta,
                                boolean selected, boolean active, boolean hovered, boolean focused) {
        fill(graphics, box, selected ? 0xFF2B3C40 : hovered && active ? 0xFF273B41 : 0xFF1B2B30);
        graphics.outline(box.x(), box.y(), box.width(), box.height(), selected || focused ? ACCENT : LINE);
        text(graphics, font, title, box.x() + 8, box.y() + 6, box.width() - 16, MUTED);
        text(graphics, font, name, box.x() + 8, box.y() + 19, box.width() - 16, active ? TEXT : MUTED);
        if (availability != null)
            text(graphics, font, availability, box.x() + 8, box.y() + 33, box.width() - 16, selected ? ACCENT : MUTED);
        if (delta != null && box.height() >= 62)
            text(graphics, font, delta, box.x() + 8, box.y() + 47, box.width() - 16, POSITIVE);
    }

    public static void statBar(GuiGraphicsExtractor graphics, GarageUiLayout.Box box,
                               float current, float preview, float maximum) {
        fill(graphics, box, 0xFF334349);
        if (!(Float.isFinite(maximum) && maximum > 0)) return;
        if (Float.isFinite(preview)) {
            int length = Math.round(Math.clamp(preview / maximum, 0, 1) * box.width());
            int color = !Float.isFinite(current) || preview >= current ? ACCENT : NEGATIVE;
            graphics.fill(box.x(), box.y(), box.x() + length, box.bottom(), color);
        }
        if (Float.isFinite(current) && box.width() > 0) {
            int tick = Math.round(Math.clamp(current / maximum, 0, 1) * (box.width() - 1));
            graphics.fill(box.x() + tick, box.y() - 1, box.x() + tick + 1, box.bottom() + 1, TEXT);
        }
    }

    public static void inventorySlot(GuiGraphicsExtractor graphics, int x, int y, boolean readOnly) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, readOnly ? 0xFF506560 : LINE);
        graphics.fill(x, y, x + 16, y + 16, 0xFF0E191D);
        graphics.fill(x, y + 15, x + 16, y + 16, 0xFF26383D);
    }

    public static void text(GuiGraphicsExtractor graphics, Font font, Component label,
                             int x, int y, int width, int color) {
        var fitted = GarageTextLayout.fit(font, label.getString(), width);
        graphics.text(font, fitted.visualOrder(), x, y, color, false);
    }

    private static void fill(GuiGraphicsExtractor graphics, GarageUiLayout.Box box, int color) {
        graphics.fill(box.x(), box.y(), box.right(), box.bottom(), color);
    }

    private static int alpha(int color, float alpha) {
        int value = Math.round((color >>> 24) * Math.clamp(alpha, 0, 1));
        return color & 0x00FFFFFF | value << 24;
    }
}
