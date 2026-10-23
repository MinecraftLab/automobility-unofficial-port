package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.automobile.attachment.FrontAttachmentType;
import io.github.foundationgames.automobility.automobile.attachment.RearAttachmentType;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.entity.GarageService;
import io.github.foundationgames.automobility.mixin.GuiGraphicsExtractorAccess;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/** Extracts the real automobile renderer into a bounded, transparent native GUI preview. */
public final class GarageVehiclePreview {
    private GarageVehiclePreview() {}

    public static void extract(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                               @Nullable GarageService.Components components, float yawDegrees) {
        extract(graphics, x, y, width, height, components, yawDegrees, null);
    }

    public static void extract(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                               @Nullable GarageService.Components components, float yawDegrees,
                               @Nullable AutomobileEntity liveCar) {
        extract(graphics, x, y, width, height, components, yawDegrees, liveCar, GaragePreviewCamera.DEFAULT_ZOOM);
    }

    public static void extract(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                               @Nullable GarageService.Components components, float yawDegrees,
                               @Nullable AutomobileEntity liveCar, float zoom) {
        var rear = liveCar == null ? RearAttachmentType.EMPTY : liveCar.getRearAttachmentType();
        var front = liveCar == null ? FrontAttachmentType.EMPTY : liveCar.getFrontAttachmentType();
        extract(graphics, x, y, width, height, components, yawDegrees, rear, front, zoom);
    }

    /** Also supports stored cars whose entity is absent from the client level. */
    public static void extract(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                               @Nullable GarageService.Components components, float yawDegrees,
                               RearAttachmentType<?> rear, FrontAttachmentType<?> front) {
        extract(graphics, x, y, width, height, components, yawDegrees, rear, front, GaragePreviewCamera.DEFAULT_ZOOM);
    }

    /** Zoom is a deliberate, bounded detail crop inside this same native PiP target and scissor. */
    public static void extract(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                               @Nullable GarageService.Components components, float yawDegrees,
                               RearAttachmentType<?> rear, FrontAttachmentType<?> front, float zoom) {
        if (width < 16 || height < 16) return;
        float boundedZoom = GaragePreviewCamera.clampZoom(zoom);

        graphics.enableScissor(x, y, x + width, y + height);
        try {
            drawPodium(graphics, x, y, width, height, boundedZoom);
            if (components == null || !components.complete()) return;

            // Capture registry values and static attachment types during extraction. There are no
            // world/entity references in the deferred render state and no baked-model cache.
            var parts = new GarageVehiclePreviewState.Parts(components.frame().value(), components.wheel().value(),
                    components.engine().value(), rear, front);
            var pose = new Matrix3x2f(graphics.pose());
            var bounds = new ScreenRectangle(x, y, width, height).transformMaxBounds(pose);
            var state = new GarageVehiclePreviewState(parts,
                    Float.isFinite(yawDegrees) ? yawDegrees % 360f : 0,
                    x, y, x + width, y + height, boundedZoom,
                    pose, bounds, bounds);
            ((GuiGraphicsExtractorAccess) graphics).automobility$garageRenderState().addPicturesInPictureState(state);
        } finally {
            graphics.disableScissor();
        }
    }

    private static void drawPodium(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float zoom) {
        int cx = x + width / 2;
        int cy = y + Math.round(height * GarageVehiclePreviewFit.GROUND_FRACTION);
        int baseRx = Math.max(6, Math.round(Math.min(width * 0.43f, height * 0.85f)));
        int baseRy = Math.max(3, Math.min(height - (cy - y) - 5,
                Math.round(baseRx * (float) Math.sin(Math.toRadians(-GarageVehiclePreviewFit.PITCH_DEGREES)))));
        // Both the floor and the real 3D model zoom around the same centered ground anchor.
        int rx = Math.max(6, Math.round(baseRx * zoom));
        int ry = Math.max(3, Math.round(baseRy * zoom));
        ellipse(graphics, cx, cy + 3, rx, ry, 0xFF0B1317);
        ellipse(graphics, cx, cy, rx, ry, 0xFF48575B);
        ellipse(graphics, cx, cy - 1, rx - 1, Math.max(1, ry - 1), 0xFF283B41);
        // A thin warm edge sits behind the matte turntable; no luminous ring or animated reflection.
        int highlightY = cy - Math.max(1, ry - 1);
        int highlightWidth = Math.max(2, rx / 4);
        graphics.fill(cx - highlightWidth, highlightY, cx + highlightWidth, highlightY + 1, 0xFF8C816C);
    }

    private static void ellipse(GuiGraphicsExtractor graphics, int cx, int cy, int rx, int ry, int color) {
        for (int dy = -ry; dy <= ry; dy++) {
            double fraction = dy / (double) ry;
            int dx = (int) Math.round(rx * Math.sqrt(Math.max(0, 1 - fraction * fraction)));
            graphics.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }
}
