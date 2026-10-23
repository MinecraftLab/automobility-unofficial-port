package io.github.foundationgames.automobility.client.vehicle;

import java.util.List;

/** Actual widget geometry shared with the native screen and headless bounds checks. */
public record VehicleEffectsLayout(int left, int top, int width, int height, boolean small) {
    public static VehicleEffectsLayout resolve(int screenWidth, int screenHeight) {
        int width = Math.max(1, Math.min(360, screenWidth - 16));
        int height = Math.max(1, Math.min(252, screenHeight - 16));
        return new VehicleEffectsLayout((screenWidth - width) / 2, (screenHeight - height) / 2,
                width, height, width < 240 || height < 224);
    }

    public Bounds quality(int index) {
        if (index < 0 || index >= 4) throw new IllegalArgumentException("Quality index");
        int buttonWidth = (width - 44) / 4;
        return new Bounds(left + 16 + index * (buttonWidth + 4), top + 57, buttonWidth, 20);
    }

    public Bounds volume() { return new Bounds(left + 16, top + 130, width - 32, 20); }
    public Bounds custom() { return new Bounds(left + 16, top + 84, width - 32, 20); }
    public Bounds mute() { return new Bounds(left + 16, top + 156, width - 32, 20); }

    public Bounds footer(int index) {
        if (index < 0 || index >= 3) throw new IllegalArgumentException("Footer index");
        int buttonWidth = (width - 40) / 3;
        return new Bounds(left + 16 + index * (buttonWidth + 4), top + height - 28, buttonWidth, 20);
    }

    public List<Bounds> widgets() {
        return List.of(quality(0), quality(1), quality(2), quality(3), custom(), volume(), mute(),
                footer(0), footer(1), footer(2));
    }

    public record Bounds(int x, int y, int width, int height) {}
}

