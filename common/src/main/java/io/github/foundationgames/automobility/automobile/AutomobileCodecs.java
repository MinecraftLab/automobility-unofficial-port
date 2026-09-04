package io.github.foundationgames.automobility.automobile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

final class AutomobileCodecs {
    static final Codec<Float> POSITIVE_FINITE_FLOAT = Codec.FLOAT.validate(value -> {
        if (Float.isFinite(value) && value > 0) {
            return DataResult.success(value);
        }
        return DataResult.error(() -> "Value must be finite and greater than zero: " + value);
    });

    private AutomobileCodecs() {
    }
}
