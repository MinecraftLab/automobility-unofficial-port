package io.github.foundationgames.automobility.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

import java.util.List;

/** Four independent native targets: deferred card blits can never all reference the last car. */
public final class GarageVehicleThumbnailRenderer extends PictureInPictureRenderer<GarageVehicleThumbnailState> {
    private final SlotRenderer[] slots = new SlotRenderer[GarageVehicleThumbnailState.SLOT_COUNT];

    public GarageVehicleThumbnailRenderer() {
        for (int slot = 0; slot < slots.length; slot++) slots[slot] = new SlotRenderer(this, slot);
    }

    @Override public Class<GarageVehicleThumbnailState> getRenderStateClass() { return GarageVehicleThumbnailState.class; }

    @Override
    public void prepare(GarageVehicleThumbnailState state, GuiRenderState guiState, FeatureRenderDispatcher dispatcher, int guiScale) {
        slots[state.slot()].prepare(state, guiState, dispatcher, guiScale);
    }

    @Override
    protected void renderToTexture(GarageVehicleThumbnailState state, PoseStack pose, SubmitNodeCollector submitter) {
        slots[state.slot()].renderAutomobile(state, pose, submitter);
    }

    @Override protected String getTextureLabel() { return "Automobility garage thumbnail pool"; }

    @Override public void close() {
        for (var slot : slots) slot.close();
        super.close();
    }

    private record Key(GarageVehiclePreviewState.Parts parts, List<Model<?>> bakedModels, int width, int height) {
        static Key capture(GarageVehicleThumbnailState state) {
            return new Key(state.automobile(), GarageVehicleRenderSupport.bakedModels(state.automobile()),
                    state.x1() - state.x0(), state.y1() - state.y0());
        }
    }

    private static final class SlotRenderer extends PictureInPictureRenderer<GarageVehicleThumbnailState> {
        private final GarageVehicleThumbnailRenderer owner;
        private final int slot;
        private final GarageVehicleRenderSupport automobile = new GarageVehicleRenderSupport();
        private Key renderedKey;

        SlotRenderer(GarageVehicleThumbnailRenderer owner, int slot) { this.owner = owner; this.slot = slot; }

        @Override public Class<GarageVehicleThumbnailState> getRenderStateClass() { return GarageVehicleThumbnailState.class; }

        @Override protected boolean textureIsReadyToBlit(GarageVehicleThumbnailState state) {
            return Key.capture(state).equals(renderedKey);
        }

        @Override
        protected void renderToTexture(GarageVehicleThumbnailState state, PoseStack pose, SubmitNodeCollector submitter) {
            owner.renderToTexture(state, pose, submitter);
        }

        private void renderAutomobile(GarageVehicleThumbnailState state, PoseStack pose, SubmitNodeCollector submitter) {
            var key = Key.capture(state);
            var bounds = automobile.measure(state.automobile(), key.bakedModels());
            var fit = GarageVehicleThumbnailFit.fit(bounds, state.x1() - state.x0(), state.y1() - state.y0());
            GarageVehicleRenderSupport.render(state.automobile(), pose, submitter, fit.scale(),
                    GarageVehicleThumbnailFit.YAW_DEGREES, fit.centerX(), fit.centerY(), fit.centerZ());
            // Missing models are retried instead of caching an incomplete resource-bake frame.
            renderedKey = bounds.valid() ? key : null;
        }

        @Override protected float getTranslateY(int height, int guiScale) { return height * .5f; }
        @Override protected String getTextureLabel() { return "Automobility garage thumbnail " + slot; }
    }
}
