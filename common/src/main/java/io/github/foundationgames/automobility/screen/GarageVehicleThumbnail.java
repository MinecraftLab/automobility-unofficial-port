package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.automobile.attachment.FrontAttachmentType;
import io.github.foundationgames.automobility.automobile.attachment.RearAttachmentType;
import io.github.foundationgames.automobility.entity.GarageService;
import io.github.foundationgames.automobility.mixin.GuiGraphicsExtractorAccess;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/** A real parked automobile, captured without creating an entity or retaining a mutable option. */
public final class GarageVehicleThumbnail {
    private GarageVehicleThumbnail() {}

    /** Use each slot 0..3 at most once per GUI frame; it identifies a visible card, not a car UUID. */
    public static void extract(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int slot,
                               @Nullable GarageService.Components components,
                               RearAttachmentType<?> rear, FrontAttachmentType<?> front) {
        if (slot < 0 || slot >= GarageVehicleThumbnailState.SLOT_COUNT)
            throw new IllegalArgumentException("Garage thumbnail slot must be 0..3");
        if (width < 8 || height < 8 || components == null || !components.complete()) return;
        var parts = new GarageVehiclePreviewState.Parts(components.frame().value(), components.wheel().value(),
                components.engine().value(), rear, front);
        var pose = new Matrix3x2f(graphics.pose());
        var bounds = new ScreenRectangle(x, y, width, height).transformMaxBounds(pose);
        var state = new GarageVehicleThumbnailState(slot, parts, x, y, x + width, y + height, pose, bounds, bounds);
        ((GuiGraphicsExtractorAccess) graphics).automobility$garageRenderState().addPicturesInPictureState(state);
    }
}
