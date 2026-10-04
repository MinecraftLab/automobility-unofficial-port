package io.github.milkucha.momentum.hud;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.config.MomentumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** A local read-only guard reminder, never a fuel, battery or server-energy diagnosis. */
final class ReverseReleaseHintHud {
    private ReverseReleaseHintHud() {}

    static void render(GuiGraphicsExtractor graphics, Minecraft client, AutomobileEntity auto,
                       MomentumConfig.BarHud config, int screenWidth, int screenHeight,
                       int mainX, int mainY) {
        if (client.player == null || client.level == null) return;
        if (!ReverseReleaseHintLayout.visible(auto.isDriving(client.player),
                client.gui.screen() != null, MomentumConfig.gameplay().enabled && config.enabled,
                config.compactDashboard, config.reverseReleaseHint, auto.waitingForReverseRelease(),
                auto.rawBrakePedalHeld(), ReverseReleaseHintLayout.stationary(auto.getEffectiveSpeed()))) return;
        if (screenWidth < 104) return;

        var title = Component.translatable("hint.momentum.reverse_release.title");
        var action = Component.translatable("hint.momentum.reverse_release.action");
        long measuredWidth = (long) Math.max(client.font.width(title), client.font.width(action)) + 12;
        int width = (int) Math.min(Math.clamp(measuredWidth, 96L, 304L), (long) screenWidth - 8);
        var titleLines = client.font.split(title, width - 12);
        var actionLines = client.font.split(action, width - 12);
        var panel = ReverseReleaseHintLayout.resolve(screenWidth, screenHeight, mainX, mainY,
                width, client.font.lineHeight, titleLines.size() + actionLines.size());
        if (panel == null) return;

        graphics.fill(panel.x(), panel.y(), panel.x() + panel.width(), panel.y() + panel.height(), 0xE016181A);
        graphics.fill(panel.x(), panel.y(), panel.x() + 2, panel.y() + panel.height(), 0xFFFFD36E);
        graphics.enableScissor(panel.x() + 6, panel.y() + 4,
                panel.x() + panel.width() - 6, panel.y() + panel.height() - 4);
        try {
            int y = panel.y() + 4;
            for (FormattedCharSequence line : titleLines) {
                graphics.text(client.font, line, panel.x() + 6, y, 0xFFFFD36E, false);
                y += client.font.lineHeight;
            }
            for (FormattedCharSequence line : actionLines) {
                graphics.text(client.font, line, panel.x() + 6, y, 0xFFEEEEEE, false);
                y += client.font.lineHeight;
            }
        } finally {
            graphics.disableScissor();
        }
    }
}
