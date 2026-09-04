package io.github.foundationgames.automobility.automobile;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutomobileCodecSafetyTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void positiveFiniteValuesAreAccepted() {
        var result = AutomobileCodecs.POSITIVE_FINITE_FLOAT.parse(JsonOps.INSTANCE, new JsonPrimitive(0.25f));

        assertEquals(0.25f, result.getOrThrow());
    }

    @Test
    void zeroAndNegativeValuesAreRejected() {
        assertTrue(AutomobileCodecs.POSITIVE_FINITE_FLOAT.parse(JsonOps.INSTANCE, new JsonPrimitive(0)).isError());
        assertTrue(AutomobileCodecs.POSITIVE_FINITE_FLOAT.parse(JsonOps.INSTANCE, new JsonPrimitive(-1)).isError());
    }

    @Test
    void nonFiniteValuesAreRejected() {
        assertTrue(AutomobileCodecs.POSITIVE_FINITE_FLOAT.parse(JsonOps.INSTANCE, new JsonPrimitive(Float.NaN)).isError());
        assertTrue(AutomobileCodecs.POSITIVE_FINITE_FLOAT.parse(JsonOps.INSTANCE, new JsonPrimitive(Float.POSITIVE_INFINITY)).isError());
    }

    @Test
    void frameCodecRejectsZeroWeight() {
        var invalid = new AutomobileFrame(
                false,
                0,
                1,
                List.of(),
                HornSoundDefinition.DEFAULT,
                AutomobileFrame.EMPTY.model()
        );

        assertTrue(AutomobileFrame.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, invalid).isError());
    }

    @Test
    void wheelDisplayCodecRejectsZeroRadius() {
        var invalid = new AutomobileWheel.WheelModel(
                0,
                1,
                net.minecraft.resources.Identifier.withDefaultNamespace("empty"),
                net.minecraft.resources.Identifier.withDefaultNamespace("empty")
        );

        assertTrue(AutomobileWheel.WheelModel.CODEC.encodeStart(JsonOps.INSTANCE, invalid).isError());
    }
}
