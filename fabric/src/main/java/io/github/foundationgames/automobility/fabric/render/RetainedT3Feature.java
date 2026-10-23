package io.github.foundationgames.automobility.fabric.render;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.render.obj.RetainedObjSubmission;

/**
 * 26.1.2 has no FeatureRendererRegistry/FeatureFrameContext contract. Do not link
 * 26.2-only types. The original complete vertex emitter draws the same geometry;
 * this target does not claim the newer retained-GPU optimization or its timing.
 */
public final class RetainedT3Feature {
    private RetainedT3Feature() {}
    public static void initialize() {
        RetainedObjSubmission.disable();
        Automobility.LOG.info("26.1.2 uses the complete standard OBJ renderer; retained feature API is unavailable");
    }
    public static void beginFrame() {
        // No retained resources are created on this target.
    }
}
