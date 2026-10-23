package io.github.foundationgames.automobility.client.vehicle;

/** One client preference for every AutomobileEntity; applied before vanilla wall clipping.
 * Blocks describe the standard-sized player's nominal distance, not a forced final position. */
public final class VehicleCameraDistance {
    public static final int DEFAULT_BLOCKS = 4;
    public static final int MAX_BLOCKS = 16;
    public static final int STEP_BLOCKS = 2;

    private VehicleCameraDistance() {}

    public static int sanitize(int blocks) {
        int bounded = Math.clamp(blocks, DEFAULT_BLOCKS, MAX_BLOCKS);
        return ((bounded + STEP_BLOCKS / 2) / STEP_BLOCKS) * STEP_BLOCKS;
    }

    public static float requested(float vanillaRequested, int blocks) {
        int distance = sanitize(blocks);
        // Preserve the exact default argument, and never amplify an invalid vanilla value.
        if (distance == DEFAULT_BLOCKS || !Float.isFinite(vanillaRequested) || vanillaRequested <= 0)
            return vanillaRequested;
        float result = vanillaRequested * (distance / (float) DEFAULT_BLOCKS);
        return Float.isFinite(result) ? result : vanillaRequested;
    }
}
