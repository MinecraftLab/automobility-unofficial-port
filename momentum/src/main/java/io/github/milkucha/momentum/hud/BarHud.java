package io.github.milkucha.momentum.hud;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.MomentumCruiseControl;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import io.github.milkucha.momentum.config.MomentumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * BarHud - compact procedural driving dashboard.
 *
 * The default layout keeps the speed readout and ten speed/boost segments in one
 * stable main panel. Cruise uses a separate top-row pod, so engaging it never moves
 * or resizes the speed display and companion HUD elements can anchor deterministically.
 *
 * Layout tuned under the "barHud" config section.
 *
 * Compact preset: 104x17 main panel, 10 fixed segments and a 38x11 cruise pod.
 */
public class BarHud {

    private static final double TO_KMH = 72.0;

    private static final int COL_PANEL_BG = 0xD916181A;
    private static final int COL_PANEL_TOP = 0xFF3B3F42;
    private static final int COL_PANEL_BOTTOM = 0xFF090A0B;
    private static final int COL_EMPTY_SEGMENT = 0xFF303438;
    private static final int COL_DIAL_METAL = 0xFFD8C7A6;
    private static final int CRUISE_INDICATOR_WIDTH = 10;
    private static final int[][] TINY_DIGITS = {
            {0b111, 0b101, 0b101, 0b101, 0b111},
            {0b010, 0b110, 0b010, 0b010, 0b111},
            {0b111, 0b001, 0b111, 0b100, 0b111},
            {0b111, 0b001, 0b111, 0b001, 0b111},
            {0b101, 0b101, 0b111, 0b001, 0b001},
            {0b111, 0b100, 0b111, 0b001, 0b111},
            {0b111, 0b100, 0b111, 0b101, 0b111},
            {0b111, 0b001, 0b010, 0b010, 0b010},
            {0b111, 0b101, 0b111, 0b101, 0b111},
            {0b111, 0b101, 0b111, 0b001, 0b111}
    };

    public static void render(GuiGraphicsExtractor graphics, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        Entity vehicle = client.player.getVehicle();
        if (!(vehicle instanceof AutomobileEntity auto)) return;
        if (client.gui.screen() != null) return;

        MomentumConfig cfg = MomentumConfig.get();
        MomentumConfig.BarHud b = cfg.barHud;
        boolean cruiseActive = MomentumCruiseControl.isActiveFor(auto);
        int cruiseColor = cfg.cruise.activeColor;
        int legacyBarColor = cruiseActive ? cruiseColor : b.barColor;
        int legacyTextColor = cruiseActive ? cruiseColor : b.textColor;

        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();

        DashboardHudLayout.PanelSize panel = DashboardHudLayout.resolvePanelSize(
                screenW, b.totalWidth, b.totalHeight);
        DashboardHudLayout.Origin origin = b.compactDashboard
                ? DashboardHudLayout.resolveCompactOrigin(
                        screenW, screenH, panel.width(), panel.height(),
                        b.x, b.y, b.xFraction, b.marginBottom)
                : DashboardHudLayout.resolveOrigin(
                        screenW, screenH, panel.width(), panel.height(),
                        b.x, b.y, b.xFraction, b.marginBottom);
        int originX = origin.x();
        int originY = origin.y();

        double speedKmh = auto.getEffectiveSpeed() * TO_KMH;

        // Separate engine vs boost contributions so boost bars can be coloured differently.
        // engineSpeed is the base; hSpeed = engineSpeed + boostSpeed.
        double engineKmh = (auto instanceof SteeringDebugAccessor acc)
                ? DashboardHudLayout.baseSpeedKmh(
                        speedKmh, acc.momentum$getEngineSpeed() * TO_KMH)
                : speedKmh;

        if (b.compactDashboard) {
            renderCompactDashboard(graphics, client, b, panel, originX, originY,
                    speedKmh, engineKmh, cruiseActive, cruiseColor,
                    b.barColor, b.textColor);
            return;
        }

        renderLegacyDashboard(graphics, client, b, panel, originX, originY,
                speedKmh, engineKmh, cruiseActive, cruiseColor,
                legacyBarColor, legacyTextColor);
    }

    private static void renderCompactDashboard(GuiGraphicsExtractor graphics, Minecraft client,
                                               MomentumConfig.BarHud config,
                                               DashboardHudLayout.PanelSize panel,
                                               int originX, int originY,
                                               double speedKmh, double engineKmh,
                                               boolean cruiseActive, int cruiseColor,
                                               int barColor, int textColor) {
        DashboardHudLayout.Layout layout = DashboardHudLayout.resolve(
                panel.width(), panel.height());
        if (!layout.renderable()) return;

        drawPanel(graphics, originX, originY, layout.width(), layout.height());
        drawSpeedDial(graphics, originX + layout.iconX(), originY + layout.iconY(),
                COL_DIAL_METAL, barColor);

        long displayedSpeed = Math.clamp(Math.round(Math.max(0.0, speedKmh)), 0L, 999L);
        String speedText = Long.toString(displayedSpeed);
        int speedWidth = client.font.width(speedText);
        int speedX = originX + layout.numberX()
                + Math.max(0, layout.numberWidth() - speedWidth);
        int speedY = originY + layout.numberY();
        graphics.text(client.font, Component.literal(speedText), speedX, speedY, textColor);

        drawKmhUnit(graphics, originX + layout.unitX(), originY + layout.unitY(), textColor);
        graphics.fill(originX + layout.dividerX(), originY + layout.dividerY(),
                originX + layout.dividerX() + 1,
                originY + layout.dividerY() + layout.dividerHeight(), COL_PANEL_TOP);

        int engineSegments = DashboardHudLayout.filledSegments(
                engineKmh, config.maxSpeedKmh, layout.segmentCount());
        int filledSegments = DashboardHudLayout.filledSegments(
                speedKmh, config.maxSpeedKmh, layout.segmentCount());
        for (int segment = 0; segment < layout.segmentCount(); segment++) {
            int segmentX = originX + layout.segmentX(segment);
            int segmentY = originY + layout.barY();
            graphics.fill(segmentX, segmentY,
                    segmentX + DashboardHudLayout.SEGMENT_WIDTH,
                    segmentY + layout.segmentHeight(), COL_EMPTY_SEGMENT);
            if (segment >= filledSegments) continue;

            int segmentColor = segment < engineSegments ? barColor : config.boostBarColor;
            graphics.fill(segmentX, segmentY,
                    segmentX + DashboardHudLayout.SEGMENT_WIDTH,
                    segmentY + layout.segmentHeight(), segmentColor);
            graphics.fill(segmentX, segmentY,
                    segmentX + DashboardHudLayout.SEGMENT_WIDTH,
                    segmentY + 1, lighten(segmentColor));
        }

        if (cruiseActive) {
            int marker = DashboardHudLayout.markerSegment(MomentumCruiseControl.getTargetKmh(),
                    config.maxSpeedKmh, layout.segmentCount());
            if (marker >= 0) {
                int markerX = originX + layout.segmentX(marker);
                graphics.fill(markerX, originY + 3,
                        markerX + DashboardHudLayout.SEGMENT_WIDTH, originY + 4, cruiseColor);
                graphics.fill(markerX, originY + 13,
                        markerX + DashboardHudLayout.SEGMENT_WIDTH,
                        originY + 14, cruiseColor);
            }
        }

        if (cruiseActive) {
            DashboardHudLayout.Origin cruise = DashboardHudLayout.resolveCruiseOrigin(
                    new DashboardHudLayout.Origin(originX, originY));
            drawCruisePod(graphics, cruise.x(), cruise.y(), cruiseColor,
                    Math.clamp(Math.round(MomentumCruiseControl.getTargetKmh()), 0L, 999L));
        }
    }

    private static void drawCruisePod(GuiGraphicsExtractor graphics, int x, int y,
                                      int color, long targetKmh) {
        drawPanel(graphics, x, y,
                DashboardHudLayout.CRUISE_WIDTH, DashboardHudLayout.CRUISE_HEIGHT);
        drawMiniChevron(graphics, x + 3, y + 3, color);
        drawTinyNumber(graphics, x + 9, y + 3, targetKmh, color);
        drawKmhUnit(graphics, x + 24, y + 3, color);
    }

    private static void renderLegacyDashboard(GuiGraphicsExtractor graphics, Minecraft client,
                                              MomentumConfig.BarHud config,
                                              DashboardHudLayout.PanelSize panel,
                                              int originX, int originY,
                                              double speedKmh, double engineKmh,
                                              boolean cruiseActive, int cruiseColor,
                                              int barColor, int textColor) {
        int numBars = Math.max(0,
                (panel.width() + config.barSpacing) / (config.barWidth + config.barSpacing));
        int engineBars = DashboardHudLayout.filledSegments(engineKmh, config.maxSpeedKmh, numBars);
        int totalFilledBars = DashboardHudLayout.filledSegments(speedKmh, config.maxSpeedKmh, numBars);

        for (int segment = 0; segment < totalFilledBars; segment++) {
            int segmentX = originX + segment * (config.barWidth + config.barSpacing);
            int color = segment < engineBars ? barColor : config.boostBarColor;
            graphics.fill(segmentX, originY, segmentX + config.barWidth,
                    originY + panel.height(), color);
        }

        String speedText = String.format("%.0f km/h", speedKmh);
        int textX = originX + config.textOffsetX;
        int textY = originY + config.textOffsetY;
        graphics.text(client.font, Component.literal(speedText), textX, textY, textColor);

        if (cruiseActive) {
            int indicatorX = textX + client.font.width(speedText) + 4;
            drawCruiseChevrons(graphics, indicatorX, textY, cruiseColor);
            graphics.text(client.font,
                    Component.literal(String.format("%.0f", MomentumCruiseControl.getTargetKmh())),
                    indicatorX + CRUISE_INDICATOR_WIDTH + 2, textY, cruiseColor);
        }
    }

    private static void drawSpeedDial(GuiGraphicsExtractor graphics, int x, int y,
                                      int metalColor, int needleColor) {
        graphics.fill(x + 2, y, x + 6, y + 1, metalColor);
        graphics.fill(x + 1, y + 1, x + 2, y + 3, metalColor);
        graphics.fill(x + 6, y + 1, x + 7, y + 3, metalColor);
        graphics.fill(x, y + 3, x + 1, y + 7, metalColor);
        graphics.fill(x + 7, y + 3, x + 8, y + 7, metalColor);
        graphics.fill(x + 1, y + 6, x + 7, y + 7, metalColor);
        graphics.fill(x + 3, y + 4, x + 5, y + 6, needleColor);
        graphics.fill(x + 4, y + 3, x + 6, y + 4, needleColor);
        graphics.fill(x + 5, y + 2, x + 7, y + 3, needleColor);
    }

    private static void drawKmhUnit(GuiGraphicsExtractor graphics, int x, int y, int color) {
        // Tiny 2x5 K M H glyphs retain the speed unit without spending a full text row.
        pixel(graphics, x, y, 0b101, color);
        pixel(graphics, x, y + 1, 0b110, color);
        pixel(graphics, x, y + 2, 0b100, color);
        pixel(graphics, x, y + 3, 0b110, color);
        pixel(graphics, x, y + 4, 0b101, color);
        pixel(graphics, x + 4, y, 0b101, color);
        pixel(graphics, x + 4, y + 1, 0b111, color);
        pixel(graphics, x + 4, y + 2, 0b111, color);
        pixel(graphics, x + 4, y + 3, 0b101, color);
        pixel(graphics, x + 4, y + 4, 0b101, color);
        pixel(graphics, x + 8, y, 0b101, color);
        pixel(graphics, x + 8, y + 1, 0b101, color);
        pixel(graphics, x + 8, y + 2, 0b111, color);
        pixel(graphics, x + 8, y + 3, 0b101, color);
        pixel(graphics, x + 8, y + 4, 0b101, color);
    }

    private static void pixel(GuiGraphicsExtractor graphics, int x, int y, int bits, int color) {
        for (int bit = 0; bit < 3; bit++) {
            if ((bits & (1 << (2 - bit))) != 0) {
                graphics.fill(x + bit, y, x + bit + 1, y + 1, color);
            }
        }
    }

    private static void drawTinyNumber(GuiGraphicsExtractor graphics, int x, int y,
                                       long value, int color) {
        String digits = Long.toString(value);
        for (int index = 0; index < digits.length(); index++) {
            int digit = digits.charAt(index) - '0';
            for (int row = 0; row < 5; row++) {
                pixel(graphics, x + index * 4, y + row, digitRow(digit, row), color);
            }
        }
    }

    private static int digitRow(int digit, int row) {
        if (digit < 0 || digit >= TINY_DIGITS.length || row < 0 || row >= 5) return 0;
        return TINY_DIGITS[digit][row];
    }

    private static void drawMiniChevron(GuiGraphicsExtractor graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 1, y + 1, color);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, color);
        graphics.fill(x + 2, y + 2, x + 3, y + 4, color);
        graphics.fill(x + 1, y + 4, x + 2, y + 5, color);
        graphics.fill(x, y + 5, x + 1, y + 6, color);
    }

    private static int lighten(int color) {
        int red = ((color >>> 16) & 0xFF) + (0xFF - ((color >>> 16) & 0xFF)) / 2;
        int green = ((color >>> 8) & 0xFF) + (0xFF - ((color >>> 8) & 0xFF)) / 2;
        int blue = (color & 0xFF) + (0xFF - (color & 0xFF)) / 2;
        return (color & 0xFF000000) | red << 16 | green << 8 | blue;
    }

    // ── Debug overlay ─────────────────────────────────────────────────────────

    public static void renderDebug(GuiGraphicsExtractor graphics, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        Entity vehicle = client.player.getVehicle();
        if (!(vehicle instanceof AutomobileEntity auto)) return;
        if (client.gui.screen() != null) return;

        MomentumConfig cfg = MomentumConfig.get();
        if (!cfg.barHud.debug) return;
        if (!(auto instanceof SteeringDebugAccessor dbg)) return;

        float   steering  = dbg.momentum$getSteering();
        float   hSpd      = dbg.momentum$getHSpeed();
        float   angSpd    = dbg.momentum$getAngularSpeed();
        float   engSpd    = dbg.momentum$getEngineSpeed();
        boolean drifting  = dbg.momentum$isDrifting();
        boolean onGround  = dbg.momentum$isOnGround();
        boolean cruise    = MomentumCruiseControl.isActiveFor(auto);

        List<String>  texts  = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        row(texts, colors, "GENERAL",                                           0xFF999999);
        row(texts, colors, String.format("steer:  %+.3f", steering),            0xFFFFFF55);
        row(texts, colors, String.format("hSpd:   %.3f",  hSpd),                0xFF55FFFF);
        row(texts, colors, String.format("engSpd: %.3f",  engSpd),              0xFFAAAAAA);
        row(texts, colors, String.format("angSpd: %+.3f", angSpd),              0xFFFF55FF);
        row(texts, colors, "ground: " + yn(onGround),     onGround ? 0xFF55FF55 : 0xFF999999);
        row(texts, colors, "drift:  " + yn(drifting),     drifting ? 0xFF55FF55 : 0xFF999999);
        row(texts, colors, String.format("cruise: %s %.0f", yn(cruise), MomentumCruiseControl.getTargetKmh()),
                cruise ? MomentumConfig.get().cruise.activeColor : 0xFF999999);

        int lineH  = 9;
        int padX   = 6;
        int padY   = 4;
        int dbgW   = 152;
        int dbgH   = padY * 2 + texts.size() * lineH;

        int screenW = client.getWindow().getGuiScaledWidth();
        int dbgX = cfg.barHud.debugX >= 0
                ? cfg.barHud.debugX
                : screenW - dbgW - (int)(screenW * cfg.barHud.debugXFraction);
        int dbgY = cfg.barHud.debugY;

        drawPanel(graphics, dbgX, dbgY, dbgW, dbgH);
        for (int i = 0; i < texts.size(); i++) {
            graphics.text(client.font, Component.literal(texts.get(i)),
                    dbgX + padX, dbgY + padY + i * lineH, colors.get(i));
        }
    }

    private static void row(List<String> texts, List<Integer> colors, String text, int color) {
        texts.add(text);
        colors.add(color);
    }

    private static String yn(boolean v) { return v ? "YES" : "no"; }

    private static void drawCruiseChevrons(GuiGraphicsExtractor g, int x, int y, int color) {
        int shadow = 0x99000000;
        int cy = y + 1;
        chevron(g, x + 1, cy + 1, shadow);
        chevron(g, x + 6, cy + 1, shadow);
        chevron(g, x, cy, color);
        chevron(g, x + 5, cy, color);
    }

    private static void chevron(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x,     y,     x + 1, y + 1, color);
        g.fill(x + 1, y + 1, x + 2, y + 2, color);
        g.fill(x + 2, y + 2, x + 3, y + 3, color);
        g.fill(x + 3, y + 3, x + 4, y + 4, color);
        g.fill(x + 2, y + 4, x + 3, y + 5, color);
        g.fill(x + 1, y + 5, x + 2, y + 6, color);
        g.fill(x,     y + 6, x + 1, y + 7, color);
    }

    private static void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int right = x + w;
        int bottom = y + h;
        g.fill(x + 1, y, right - 1, bottom, COL_PANEL_BG);
        g.fill(x, y + 1, right, bottom - 1, COL_PANEL_BG);
        g.fill(x + 2, y + 1, right - 2, y + 2, COL_PANEL_TOP);
        g.fill(x + 2, bottom - 2, right - 2, bottom - 1, COL_PANEL_BOTTOM);
    }
}
