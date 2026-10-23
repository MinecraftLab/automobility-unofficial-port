package io.github.foundationgames.automobility.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

/** Uses vanilla's GUI render target and the production frame/engine/wheel/attachment path. */
public final class GarageVehiclePreviewRenderer extends PictureInPictureRenderer<GarageVehiclePreviewState> {
    private final GarageVehicleRenderSupport automobile = new GarageVehicleRenderSupport();

    public GarageVehiclePreviewRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    @Override
    public Class<GarageVehiclePreviewState> getRenderStateClass() {
        return GarageVehiclePreviewState.class;
    }

    @Override
    protected void renderToTexture(GarageVehiclePreviewState state, PoseStack pose) {
        var dispatcher = Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();
        var bounds = automobile.measure(state.automobile());
        var fit = GarageVehiclePreviewFit.fit(bounds, state.x1() - state.x0(), state.y1() - state.y0());
        // Rotate around the actual display footprint, and place its lowest geometry on the podium.
        GarageVehicleRenderSupport.render(state.automobile(), pose, dispatcher.getSubmitNodeStorage(), fit.scale(), state.yawDegrees(),
                fit.centerX(), fit.groundY(), fit.centerZ());
        // Match vanilla GuiEntityRenderer: flush while this PiP target is bound.
        dispatcher.renderAllFeatures();
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height * GarageVehiclePreviewFit.GROUND_FRACTION;
    }

    @Override
    protected String getTextureLabel() {
        return "Automobility garage vehicle";
    }
}
