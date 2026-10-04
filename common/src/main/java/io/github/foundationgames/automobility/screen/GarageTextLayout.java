package io.github.foundationgames.automobility.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Measures and draws the same shaped glyph sequence; this does not replace Minecraft's renderer. */
final class GarageTextLayout {
    private static final String ELLIPSIS = "…";

    private GarageTextLayout() {}

    static Layout fit(Font font, String content, int availableWidth) {
        var language = Language.getInstance();
        return fit(content, availableWidth,
                value -> language.getVisualOrder(FormattedText.of(value)), font::width);
    }

    static Layout fit(String content, int availableWidth,
                      Function<String, FormattedCharSequence> visualOrder,
                      ToIntFunction<FormattedCharSequence> measure) {
        int limit = Math.max(0, availableWidth);
        var full = visualOrder.apply(content);
        int fullWidth = measure.applyAsInt(full);
        if (fullWidth <= limit) {
            return new Layout(full, fullWidth);
        }
        var best = visualOrder.apply(ELLIPSIS);
        if (measure.applyAsInt(best) > limit) {
            return new Layout(FormattedCharSequence.EMPTY, fullWidth);
        }
        // Search UTF-16 indices, correcting each candidate boundary without scanning the whole prefix.
        // Shaping widths need not be monotonic: retain only verified fits, not a claim of maximal length.
        // At most 2 + ceil(log2(content.length() + 1)) shaping/measurement attempts, even for long names.
        int low = 0;
        int high = content.length() - 1;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            int end = middle;
            if (end > 0 && Character.isHighSurrogate(content.charAt(end - 1))
                    && Character.isLowSurrogate(content.charAt(end))) {
                end--;
            }
            var candidate = visualOrder.apply(content.substring(0, end) + ELLIPSIS);
            if (measure.applyAsInt(candidate) <= limit) {
                best = candidate;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return new Layout(best, fullWidth);
    }

    record Layout(FormattedCharSequence visualOrder, int fullWidth) {}
}

