package io.github.foundationgames.automobility.automobile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

final class AutomobileCodecs {
    // These bounds leave substantial headroom over bundled parts while preserving the
    // finite acceleration, traction and wheel-rendering invariants used by the simulation.
    static final float FRAME_WEIGHT_MIN = 0.01f;
    static final float FRAME_WEIGHT_MAX = 1.5f;
    static final float ENGINE_TORQUE_MIN = 0f;
    static final float ENGINE_TORQUE_MAX = 3f;
    static final float ENGINE_SPEED_MIN = 0f;
    static final float ENGINE_SPEED_MAX = 4f;
    static final float WHEEL_SIZE_MIN = 0f;
    static final float WHEEL_SIZE_MAX = 4f;
    static final float WHEEL_GRIP_MIN = 0f;
    static final float WHEEL_GRIP_MAX = 1f;
    static final float WHEEL_RADIUS_MIN = 0.25f;
    static final float WHEEL_RADIUS_MAX = 64f;
    static final float WHEEL_SCALE_MIN = 0.01f;
    static final float WHEEL_SCALE_MAX = 2f;

    static final Codec<Float> FRAME_WEIGHT = finiteRange("Frame weight", FRAME_WEIGHT_MIN, FRAME_WEIGHT_MAX);
    static final Codec<Float> ENGINE_TORQUE = finiteRange("Engine torque", ENGINE_TORQUE_MIN, ENGINE_TORQUE_MAX);
    static final Codec<Float> ENGINE_SPEED = finiteRange("Engine speed", ENGINE_SPEED_MIN, ENGINE_SPEED_MAX);
    static final Codec<Float> WHEEL_SIZE = finiteRange("Wheel size", WHEEL_SIZE_MIN, WHEEL_SIZE_MAX);
    static final Codec<Float> WHEEL_GRIP = finiteRange("Wheel grip", WHEEL_GRIP_MIN, WHEEL_GRIP_MAX);
    static final Codec<Float> WHEEL_RADIUS = finiteRange("Wheel radius", WHEEL_RADIUS_MIN, WHEEL_RADIUS_MAX);
    static final Codec<Float> WHEEL_SCALE = finiteRange("Wheel scale", WHEEL_SCALE_MIN, WHEEL_SCALE_MAX);

    static Codec<Float> finiteRange(String name, float min, float max) {
        return Codec.FLOAT.validate(value -> {
            if (Float.isFinite(value) && value >= min && value <= max) {
                return DataResult.success(value);
            }
            return DataResult.error(() -> name + " must be finite and in [" + min + ", " + max + "]: " + value);
        });
    }

    private AutomobileCodecs() {
    }
}
