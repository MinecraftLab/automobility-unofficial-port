package io.github.foundationgames.automobility.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class DismountSearchPolicyTest {
    @Test
    void searchesPreferredSidesThenFrontBackAndDiagonals() {
        assertArrayEquals(
                new float[] { 90, -90, 0, 180, 45, -45, 135, -135 },
                DismountSearchPolicy.yawOffsets(90)
        );
        assertArrayEquals(
                new float[] { -90, 90, 0, 180, -45, 45, -135, 135 },
                DismountSearchPolicy.yawOffsets(-90)
        );
    }
}
