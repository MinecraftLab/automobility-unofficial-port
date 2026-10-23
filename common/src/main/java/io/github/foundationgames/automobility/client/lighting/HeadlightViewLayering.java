package io.github.foundationgames.automobility.client.lighting;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Perspective view-space layering candidate; it does not change beam/world geometry. */
public final class HeadlightViewLayering {
    private static final float SCALE = 1.0f - 1.0f / 4096.0f;

    private HeadlightViewLayering() {}

    /**
     * Copies a camera-relative pose and applies the standard perspective view offset.
     * Local scaling on the left also scales the pose translation, so the offset is
     * towards the camera origin rather than towards the automobile origin. Uniform
     * camera rotation may be applied before or after this offset.
     *
     * Use only for submitted vertex positions. Keep the original normal transform,
     * world positions, visibility clips and light intensity calculations. In a
     * perspective projection x/w and y/w stay unchanged; reverse-Z depth increases.
     * This is not suitable for an orthographic projection. Its distance-dependent
     * depth pull (about 9.3 mm at 38 m, 24.4 cm at 1 km) can cross close occluders.
     */
    public static Matrix4f apply(Matrix4fc cameraRelativePose) {
        return new Matrix4f(cameraRelativePose).scaleLocal(SCALE);
    }
}
