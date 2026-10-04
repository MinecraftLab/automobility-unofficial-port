package io.github.foundationgames.automobility.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarageServicePolicyTest {
    @Test
    void onlyAnUnoccupiedUnmountedStoppedGroundedCarCanBeServiced() {
        for (int flags = 0; flags < 16; flags++) {
            boolean passengers = (flags & 1) != 0;
            boolean riding = (flags & 2) != 0;
            boolean running = (flags & 4) != 0;
            boolean grounded = (flags & 8) != 0;

            assertEquals(flags == 8,
                    GarageService.stationary(passengers, riding, running, grounded, 0, 0, 0),
                    "vehicle state flags=" + flags);
        }
    }

    @Test
    void acceptsZeroAndSubnormalMotionIncludingSignedZero() {
        for (double horizontal : new double[] {0, -0.0, Double.MIN_VALUE, -Double.MIN_VALUE}) {
            for (double vertical : new double[] {0, -0.0, Double.MIN_VALUE, -Double.MIN_VALUE}) {
                for (double velocitySquared : new double[] {0, -0.0, Double.MIN_VALUE}) {
                    assertTrue(stationary(horizontal, vertical, velocitySquared));
                }
            }
        }
    }

    @Test
    void acceptsBothHorizontalThresholdsInclusively() {
        assertTrue(stationary(0.01, 0, 0));
        assertTrue(stationary(-0.01, 0, 0));
        assertTrue(stationary(Math.nextDown(0.01), 0, 0));
        assertTrue(stationary(Math.nextUp(-0.01), 0, 0));
    }

    @Test
    void rejectsHorizontalMotionImmediatelyOutsideEitherThreshold() {
        assertFalse(stationary(Math.nextUp(0.01), 0, 0));
        assertFalse(stationary(Math.nextDown(-0.01), 0, 0));
        assertFalse(stationary(Double.MAX_VALUE, 0, 0));
        assertFalse(stationary(-Double.MAX_VALUE, 0, 0));
    }

    @Test
    void acceptsBothVerticalThresholdsInclusively() {
        assertTrue(stationary(0, 0.035, 0));
        assertTrue(stationary(0, -0.035, 0));
        assertTrue(stationary(0, Math.nextDown(0.035), 0));
        assertTrue(stationary(0, Math.nextUp(-0.035), 0));
    }

    @Test
    void rejectsVerticalMotionImmediatelyOutsideEitherThreshold() {
        assertFalse(stationary(0, Math.nextUp(0.035), 0));
        assertFalse(stationary(0, Math.nextDown(-0.035), 0));
        assertFalse(stationary(0, Double.MAX_VALUE, 0));
        assertFalse(stationary(0, -Double.MAX_VALUE, 0));
    }

    @Test
    void acceptsSquaredVelocityThresholdInclusively() {
        assertTrue(stationary(0, 0, 0.0025));
        assertTrue(stationary(0, 0, Math.nextDown(0.0025)));
        assertFalse(stationary(0, 0, Math.nextUp(0.0025)));
        assertFalse(stationary(0, 0, Double.MAX_VALUE));
    }

    @Test
    void rejectsNegativeSquaredVelocityEvenWhenItsMagnitudeIsTiny() {
        for (double velocitySquared : new double[] {-Double.MIN_VALUE, -0.001, -1, -Double.MAX_VALUE}) {
            assertFalse(stationary(0, 0, velocitySquared), "velocitySquared=" + velocitySquared);
        }
    }

    @Test
    void rejectsEveryNonFiniteValueInEachMotionField() {
        for (double value : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertFalse(stationary(value, 0, 0), "horizontal=" + value);
            assertFalse(stationary(0, value, 0), "vertical=" + value);
            assertFalse(stationary(0, 0, value), "velocitySquared=" + value);
        }
    }

    @Test
    void requiresAllMotionChecksEvenWhenTheOtherFieldsAreAtAllowedLimits() {
        for (double horizontal : new double[] {-0.01, 0.01}) {
            for (double vertical : new double[] {-0.035, 0.035}) {
                assertTrue(stationary(horizontal, vertical, 0.0025));
                assertFalse(stationary(horizontal, vertical, Math.nextUp(0.0025)));
                assertFalse(stationary(Math.copySign(Math.nextUp(0.01), horizontal), vertical, 0.0025));
                assertFalse(stationary(horizontal, Math.copySign(Math.nextUp(0.035), vertical), 0.0025));
            }
        }
    }

    @Test
    void acceptsExactPartsAndOutputCapacityForCommonWheelCounts() {
        for (int required : new int[] {1, 2, 3, 4, 6, 8, 64}) {
            assertTrue(GarageService.validCount(required, required, required), "required=" + required);
            assertTrue(GarageService.validCount(required, required, 64), "required=" + required);
        }
    }

    @Test
    void acceptsSurplusInputWithoutTreatingOutputCapacityAsAnInputLimit() {
        assertTrue(GarageService.validCount(4, 5, 4));
        assertTrue(GarageService.validCount(4, 64, 4));
        assertTrue(GarageService.validCount(1, Integer.MAX_VALUE, 1));
    }

    @Test
    void rejectsZeroAndNegativeRequiredCountsEvenWithAbundantParts() {
        for (int required : new int[] {0, -1, Integer.MIN_VALUE}) {
            assertFalse(GarageService.validCount(required, Integer.MAX_VALUE, Integer.MAX_VALUE));
        }
    }

    @Test
    void rejectsInsufficientOrNegativeAvailableParts() {
        for (int available : new int[] {Integer.MIN_VALUE, -1, 0, 1, 2, 3}) {
            assertFalse(GarageService.validCount(4, available, 64), "available=" + available);
        }
    }

    @Test
    void rejectsRequiredCountsThatDoNotFitTheReturnedStack() {
        for (int capacity : new int[] {Integer.MIN_VALUE, -1, 0, 1, 2, 3}) {
            assertFalse(GarageService.validCount(4, 64, capacity), "capacity=" + capacity);
        }
        assertFalse(GarageService.validCount(65, 65, 64));
        assertFalse(GarageService.validCount(Integer.MAX_VALUE, Integer.MAX_VALUE, 64));
    }

    @Test
    void integerBoundaryChecksDoNotOverflow() {
        assertTrue(GarageService.validCount(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertFalse(GarageService.validCount(Integer.MAX_VALUE, Integer.MAX_VALUE - 1, Integer.MAX_VALUE));
        assertFalse(GarageService.validCount(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE - 1));
        assertFalse(GarageService.validCount(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE));
    }

    private static boolean stationary(double horizontal, double vertical, double velocitySquared) {
        return GarageService.stationary(false, false, false, true, horizontal, vertical, velocitySquared);
    }
}
