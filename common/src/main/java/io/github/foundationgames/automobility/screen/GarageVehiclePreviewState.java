package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import io.github.foundationgames.automobility.automobile.AutomobileWheel;
import io.github.foundationgames.automobility.automobile.attachment.FrontAttachmentType;
import io.github.foundationgames.automobility.automobile.attachment.RearAttachmentType;
import io.github.foundationgames.automobility.automobile.attachment.front.FrontAttachment;
import io.github.foundationgames.automobility.automobile.attachment.rear.RearAttachment;
import io.github.foundationgames.automobility.automobile.render.RenderableAutomobile;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fc;
import org.joml.Vector3f;

/** A GUI frame owns its selected parts; it never retains an entity, inventory, or baked model. */
public record GarageVehiclePreviewState(
        Parts automobile, float yawDegrees,
        int x0, int y0, int x1, int y1, float scale,
        Matrix3x2fc pose, ScreenRectangle scissorArea, ScreenRectangle bounds
) implements PictureInPictureRenderState {

    /** Static, parked display pose. Attachment types render their actual models in their default pose. */
    public record Parts(AutomobileFrame frame, AutomobileWheel wheel, AutomobileEngine engine,
                        RearAttachmentType<?> rearType, FrontAttachmentType<?> frontType)
            implements RenderableAutomobile {
        @Override public AutomobileFrame getFrame() { return frame; }
        @Override public AutomobileWheel getWheels() { return wheel; }
        @Override public AutomobileEngine getEngine() { return engine; }
        @Override public RearAttachmentType<?> getRearAttachmentType() { return rearType; }
        @Override public FrontAttachmentType<?> getFrontAttachmentType() { return frontType; }
        @Override public @Nullable RearAttachment getRearAttachment() { return null; }
        @Override public @Nullable FrontAttachment getFrontAttachment() { return null; }
        @Override public float getAutomobileYaw(float delta) { return 0; }
        @Override public float getRearAttachmentYaw(float delta) { return 0; }
        @Override public float getWheelAngle(float delta) { return 0; }
        @Override public float getSteering(float delta) { return 0; }
        @Override public float getSuspensionBounce(float delta) { return 0; }
        @Override public boolean engineRunning() { return false; }
        @Override public int getBoostTimer() { return 0; }
        @Override public int getTurboCharge() { return 0; }
        @Override public long getTime() { return 0; }
        @Override public boolean automobileOnGround() { return true; }
        @Override public boolean debris() { return false; }
        @Override public Vector3f debrisColor() { return new Vector3f(); }
    }
}
