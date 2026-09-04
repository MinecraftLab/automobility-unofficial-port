package io.github.foundationgames.automobility.entity;

final class DismountSearchPolicy {
    private DismountSearchPolicy() {
    }

    static float[] yawOffsets(float preferredSide) {
        float sideSign = Math.copySign(1, preferredSide);
        return new float[] {
                preferredSide,
                -preferredSide,
                0,
                180,
                45 * sideSign,
                -45 * sideSign,
                135 * sideSign,
                -135 * sideSign
        };
    }
}
