package io.github.foundationgames.automobility.automobile.render.obj;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Platform hook for the private retained-body experiment. No Fabric/Iris ABI in common. */
public final class RetainedObjSubmission {
    private static Backend backend;
    private RetainedObjSubmission() { }

    public static void install(Backend value) {
        backend = java.util.Objects.requireNonNull(value);
    }

    static boolean enabled() { return backend != null; }

    public static void disable() { backend = null; }

    static boolean trySubmit(BakedObj bake, PoseStack.Pose pose, SubmitNodeCollector collector,
                             RenderType type, int light, int overlay, int color,
                             SubmitNodeCollector.CustomGeometryRenderer emitter) {
        return backend.submit(new Request(bake, pose.copy(), collector, type, light, overlay, color, emitter));
    }

    @FunctionalInterface
    public interface Backend { boolean submit(Request request); }

    public record Request(BakedObj bake, PoseStack.Pose pose, SubmitNodeCollector collector,
                          RenderType type, int light, int overlay, int color,
                          SubmitNodeCollector.CustomGeometryRenderer emitter) { }
}
