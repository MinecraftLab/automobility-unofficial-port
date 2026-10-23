package io.github.foundationgames.automobility.screen;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix3x2f;

/** Each visible rail position has a separate native PiP target, independent of the hero. */
public record GarageVehicleThumbnailState(
        int slot, GarageVehiclePreviewState.Parts automobile,
        int x0, int y0, int x1, int y1,
        Matrix3x2f pose, ScreenRectangle scissorArea, ScreenRectangle bounds
) implements PictureInPictureRenderState {
    public static final int SLOT_COUNT = 4;

    public GarageVehicleThumbnailState {
        if (slot < 0 || slot >= SLOT_COUNT) throw new IllegalArgumentException("Garage thumbnail slot must be 0..3");
    }

    @Override public float scale() { return 1; }
}
