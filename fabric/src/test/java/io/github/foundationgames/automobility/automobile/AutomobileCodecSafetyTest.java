package io.github.foundationgames.automobility.automobile;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvents;
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
    void boundedPhysicsCodecEndpointsAreAccepted() {
        assertRangeEndpoints(AutomobileCodecs.FRAME_WEIGHT, AutomobileCodecs.FRAME_WEIGHT_MIN, AutomobileCodecs.FRAME_WEIGHT_MAX);
        assertRangeEndpoints(AutomobileCodecs.ENGINE_TORQUE, AutomobileCodecs.ENGINE_TORQUE_MIN, AutomobileCodecs.ENGINE_TORQUE_MAX);
        assertRangeEndpoints(AutomobileCodecs.ENGINE_SPEED, AutomobileCodecs.ENGINE_SPEED_MIN, AutomobileCodecs.ENGINE_SPEED_MAX);
        assertRangeEndpoints(AutomobileCodecs.WHEEL_SIZE, AutomobileCodecs.WHEEL_SIZE_MIN, AutomobileCodecs.WHEEL_SIZE_MAX);
        assertRangeEndpoints(AutomobileCodecs.WHEEL_GRIP, AutomobileCodecs.WHEEL_GRIP_MIN, AutomobileCodecs.WHEEL_GRIP_MAX);
        assertRangeEndpoints(AutomobileCodecs.WHEEL_RADIUS, AutomobileCodecs.WHEEL_RADIUS_MIN, AutomobileCodecs.WHEEL_RADIUS_MAX);
        assertRangeEndpoints(AutomobileCodecs.WHEEL_SCALE, AutomobileCodecs.WHEEL_SCALE_MIN, AutomobileCodecs.WHEEL_SCALE_MAX);
    }

    @Test
    void valuesImmediatelyOutsidePhysicsRangesAreRejected() {
        assertOutsideRangeRejected(AutomobileCodecs.FRAME_WEIGHT, AutomobileCodecs.FRAME_WEIGHT_MIN, AutomobileCodecs.FRAME_WEIGHT_MAX);
        assertOutsideRangeRejected(AutomobileCodecs.ENGINE_TORQUE, AutomobileCodecs.ENGINE_TORQUE_MIN, AutomobileCodecs.ENGINE_TORQUE_MAX);
        assertOutsideRangeRejected(AutomobileCodecs.ENGINE_SPEED, AutomobileCodecs.ENGINE_SPEED_MIN, AutomobileCodecs.ENGINE_SPEED_MAX);
        assertOutsideRangeRejected(AutomobileCodecs.WHEEL_SIZE, AutomobileCodecs.WHEEL_SIZE_MIN, AutomobileCodecs.WHEEL_SIZE_MAX);
        assertOutsideRangeRejected(AutomobileCodecs.WHEEL_GRIP, AutomobileCodecs.WHEEL_GRIP_MIN, AutomobileCodecs.WHEEL_GRIP_MAX);
        assertOutsideRangeRejected(AutomobileCodecs.WHEEL_RADIUS, AutomobileCodecs.WHEEL_RADIUS_MIN, AutomobileCodecs.WHEEL_RADIUS_MAX);
        assertOutsideRangeRejected(AutomobileCodecs.WHEEL_SCALE, AutomobileCodecs.WHEEL_SCALE_MIN, AutomobileCodecs.WHEEL_SCALE_MAX);
    }

    @Test
    void everyPhysicsRangeRejectsNonFiniteValues() {
        for (var codec : List.of(
                AutomobileCodecs.FRAME_WEIGHT,
                AutomobileCodecs.ENGINE_TORQUE,
                AutomobileCodecs.ENGINE_SPEED,
                AutomobileCodecs.WHEEL_SIZE,
                AutomobileCodecs.WHEEL_GRIP,
                AutomobileCodecs.WHEEL_RADIUS,
                AutomobileCodecs.WHEEL_SCALE
        )) {
            assertRejected(codec, Float.NaN);
            assertRejected(codec, Float.POSITIVE_INFINITY);
            assertRejected(codec, Float.NEGATIVE_INFINITY);
        }
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
    void defaultStatsStartWithOperationalHandling() {
        var stats = new AutomobileStats();

        assertTrue(stats.getHandling() >= AutomobileStats.MIN_HANDLING);
        assertTrue(Float.isFinite(2 / stats.getHandling()));
    }

    @Test
    void engineCodecUsesTorqueAndSpeedBounds() {
        var excessiveTorque = AutomobileEngine.create(
                false,
                Math.nextUp(AutomobileCodecs.ENGINE_TORQUE_MAX),
                AutomobileCodecs.ENGINE_SPEED_MAX,
                SoundEvents.MINECART_INSIDE,
                AutomobileEngine.EMPTY.model()
        );
        var excessiveSpeed = AutomobileEngine.create(
                false,
                AutomobileCodecs.ENGINE_TORQUE_MAX,
                Math.nextUp(AutomobileCodecs.ENGINE_SPEED_MAX),
                SoundEvents.MINECART_INSIDE,
                AutomobileEngine.EMPTY.model()
        );

        assertTrue(AutomobileEngine.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, excessiveTorque).isError());
        assertTrue(AutomobileEngine.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, excessiveSpeed).isError());
    }

    @Test
    void wheelCodecUsesSizeAndGripBounds() {
        var excessiveSize = new AutomobileWheel(
                false,
                Math.nextUp(AutomobileCodecs.WHEEL_SIZE_MAX),
                AutomobileCodecs.WHEEL_GRIP_MAX,
                AutomobileWheel.EMPTY.model()
        );
        var excessiveGrip = new AutomobileWheel(
                false,
                AutomobileCodecs.WHEEL_SIZE_MAX,
                Math.nextUp(AutomobileCodecs.WHEEL_GRIP_MAX),
                AutomobileWheel.EMPTY.model()
        );

        assertTrue(AutomobileWheel.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, excessiveSize).isError());
        assertTrue(AutomobileWheel.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, excessiveGrip).isError());
    }

    @Test
    void wheelDisplayCodecRejectsUnderflowRadius() {
        var invalid = new AutomobileWheel.WheelModel(
                Float.MIN_VALUE,
                1,
                net.minecraft.resources.Identifier.withDefaultNamespace("empty"),
                net.minecraft.resources.Identifier.withDefaultNamespace("empty")
        );

        assertTrue(AutomobileWheel.WheelModel.CODEC.encodeStart(JsonOps.INSTANCE, invalid).isError());
    }

    @Test
    void wheelPositionCodecUsesScaleBounds() {
        var invalid = new WheelBase.WheelPos(
                0,
                0,
                Math.nextUp(AutomobileCodecs.WHEEL_SCALE_MAX),
                0,
                WheelBase.WheelEnd.FRONT,
                WheelBase.WheelSide.LEFT
        );

        assertTrue(WheelBase.WheelPos.CODEC.encodeStart(JsonOps.INSTANCE, invalid).isError());
    }

    @Test
    void endpointPhysicsStatsStayFiniteAndAvoidAccelerationPole() {
        for (float weight : endpoints(AutomobileCodecs.FRAME_WEIGHT_MIN, AutomobileCodecs.FRAME_WEIGHT_MAX)) {
            for (float size : endpoints(AutomobileCodecs.WHEEL_SIZE_MIN, AutomobileCodecs.WHEEL_SIZE_MAX)) {
                for (float grip : endpoints(AutomobileCodecs.WHEEL_GRIP_MIN, AutomobileCodecs.WHEEL_GRIP_MAX)) {
                    for (float torque : endpoints(AutomobileCodecs.ENGINE_TORQUE_MIN, AutomobileCodecs.ENGINE_TORQUE_MAX)) {
                        for (float speed : endpoints(AutomobileCodecs.ENGINE_SPEED_MIN, AutomobileCodecs.ENGINE_SPEED_MAX)) {
                            var stats = new AutomobileStats();
                            stats.from(frame(weight), wheel(size, grip), engine(torque, speed));
                            assertStatsFinite(stats);
                            assertTrue(stats.getAcceleration() >= AutomobileStats.MIN_ACCELERATION);
                            assertTrue(stats.getHandling() >= AutomobileStats.MIN_HANDLING);
                            assertTrue(stats.getComfortableSpeed() >= 0);
                            float accelerationNumerator = 0.9f * ((stats.getAcceleration() + 1) / 2);
                            float accelerationDenominatorAtRest = 18.5f - (stats.getAcceleration() * 5.3f);
                            float accelerationStepAtRest = accelerationNumerator / accelerationDenominatorAtRest;
                            assertTrue(accelerationNumerator >= 0);
                            assertTrue(accelerationDenominatorAtRest > 0);
                            assertTrue(Float.isFinite(accelerationStepAtRest));
                            assertTrue(accelerationStepAtRest >= 0);
                            assertTrue(Float.isFinite(2 / stats.getHandling()));
                            float surfaceGrip = stats.calculateSurfaceGrip(1);
                            assertTrue(Float.isFinite(surfaceGrip));
                            assertTrue(surfaceGrip >= 0);
                            assertTrue(surfaceGrip <= 1);
                        }
                    }
                }
            }
        }

        var maximumAccelerationStats = new AutomobileStats();
        maximumAccelerationStats.from(
                frame(AutomobileCodecs.FRAME_WEIGHT_MIN),
                wheel(AutomobileCodecs.WHEEL_SIZE_MIN, AutomobileCodecs.WHEEL_GRIP_MIN),
                engine(AutomobileCodecs.ENGINE_TORQUE_MAX, AutomobileCodecs.ENGINE_SPEED_MAX)
        );
        float accelerationDenominatorAtRest = 18.5f - (maximumAccelerationStats.getAcceleration() * 5.3f);
        assertTrue(accelerationDenominatorAtRest > 0);
    }

    @Test
    void riaUnofficialSixPhysicsValuesRoundTripThroughDirectCodecs() {
        var registryOps = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
                .createSerializationContext(NbtOps.INSTANCE);
        for (float weight : new float[]{0.6f, 0.8f, 0.95f, 1.05f, 1.11f, 1.2f, 1.6f, 1.9f, 2.1f, 2.22f, 1.5f, 0.98f}) {
            var original = frame(weight);
            var encoded = AutomobileFrame.DIRECT_CODEC.encodeStart(registryOps, original).getOrThrow();
            var decoded = AutomobileFrame.DIRECT_CODEC.parse(registryOps, encoded).getOrThrow();
            assertEquals(weight, decoded.weight());
        }

        String[] wheelNames = {"dmc12", "standard_formula"};
        float[][] wheelDimensions = {{8.12f, 8.05f}, {10.9f, 12.2f}};
        for (int i = 0; i < wheelNames.length; i++) {
            var original = new AutomobileWheel(
                    false,
                    1.1f,
                    0.8f,
                    new AutomobileWheel.WheelModel(
                            wheelDimensions[i][0],
                            wheelDimensions[i][1],
                            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                    "riautomobility", "textures/entity/automobile/wheel/" + wheelNames[i] + ".png"),
                            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                    "riautomobility", "wheel/" + wheelNames[i])
                    )
            );
            var encoded = AutomobileWheel.DIRECT_CODEC.encodeStart(registryOps, original).getOrThrow();
            var decoded = AutomobileWheel.DIRECT_CODEC.parse(registryOps, encoded).getOrThrow();
            assertEquals(original.size(), decoded.size());
            assertEquals(original.grip(), decoded.grip());
            assertEquals(wheelDimensions[i][0], decoded.model().radius());
            assertEquals(wheelDimensions[i][1], decoded.model().width());
        }
    }

    @Test
    void radiusEndpointsKeepWheelMathFinite() {
        float minimumCircumference = (float) (2 * (AutomobileCodecs.WHEEL_RADIUS_MIN / 16) * Math.PI);
        float maximumEffectiveRadius = AutomobileCodecs.WHEEL_RADIUS_MAX * AutomobileCodecs.WHEEL_SCALE_MAX;
        double skidRadicand = (maximumEffectiveRadius * maximumEffectiveRadius)
                - Math.pow(maximumEffectiveRadius - 2, 2);

        assertTrue(Float.isFinite(minimumCircumference));
        assertTrue(minimumCircumference > 0);
        assertTrue(Float.isFinite(300 * (1 / minimumCircumference)));
        assertTrue(Double.isFinite(skidRadicand));
        assertTrue(skidRadicand >= 0);
        assertTrue(Double.isFinite(Math.sqrt(skidRadicand)));
    }

    @Test
    void maximumGripDoesNotAmplifyPreviousVelocity() {
        var stats = new AutomobileStats();
        stats.from(
                frame(AutomobileCodecs.FRAME_WEIGHT_MAX),
                wheel(AutomobileCodecs.WHEEL_SIZE_MIN, AutomobileCodecs.WHEEL_GRIP_MAX),
                AutomobileEngine.EMPTY
        );

        for (float normalizedSurfaceFriction : new float[]{-1f, 0f, 0.25f, 0.5f, 0.75f, 1f, 2f}) {
            float movementBlend = stats.calculateSurfaceGrip(normalizedSurfaceFriction);
            assertTrue(Float.isFinite(movementBlend));
            assertTrue(movementBlend >= 0);
            assertTrue(movementBlend <= 1);
        }
    }

    private static AutomobileFrame frame(float weight) {
        return new AutomobileFrame(
                false,
                weight,
                1,
                List.of(),
                HornSoundDefinition.DEFAULT,
                AutomobileFrame.EMPTY.model()
        );
    }

    private static AutomobileWheel wheel(float size, float grip) {
        return new AutomobileWheel(false, size, grip, AutomobileWheel.EMPTY.model());
    }

    private static AutomobileEngine engine(float torque, float speed) {
        return AutomobileEngine.create(
                false,
                torque,
                speed,
                SoundEvents.MINECART_INSIDE,
                AutomobileEngine.EMPTY.model()
        );
    }

    private static float[] endpoints(float min, float max) {
        return new float[]{min, max};
    }

    private static void assertStatsFinite(AutomobileStats stats) {
        assertTrue(Float.isFinite(stats.getAcceleration()));
        assertTrue(Float.isFinite(stats.getComfortableSpeed()));
        assertTrue(Float.isFinite(stats.getHandling()));
        assertTrue(Float.isFinite(stats.getGrip()));
    }

    private static void assertRangeEndpoints(Codec<Float> codec, float min, float max) {
        assertEquals(min, codec.parse(JsonOps.INSTANCE, new JsonPrimitive(min)).getOrThrow());
        assertEquals(max, codec.parse(JsonOps.INSTANCE, new JsonPrimitive(max)).getOrThrow());
    }

    private static void assertOutsideRangeRejected(Codec<Float> codec, float min, float max) {
        assertRejected(codec, Math.nextDown(min));
        assertRejected(codec, Math.nextUp(max));
    }

    private static void assertRejected(Codec<Float> codec, float value) {
        assertTrue(codec.parse(JsonOps.INSTANCE, new JsonPrimitive(value)).isError());
    }
}
