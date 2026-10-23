package io.github.milkucha.momentum;

/** One camera-lock contract for mouse input and the registered heading controller. */
public final class MomentumCameraPolicy {
    private MomentumCameraPolicy() {}

    public static boolean lockActive(boolean gameplayEnabled, boolean cameraEnabled, boolean lock) {
        return gameplayEnabled && cameraEnabled && lock;
    }
}
