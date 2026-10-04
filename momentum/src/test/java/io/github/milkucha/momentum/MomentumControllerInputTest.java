package io.github.milkucha.momentum;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MomentumControllerInputTest {
    @AfterEach
    void resetSource() {
        MomentumControllerInput.resetForTests();
    }

    @Test
    void defaultsToNoControllerActions() {
        assertEquals(MomentumControllerInput.none(), MomentumControllerInput.poll());
    }

    @Test
    void forwardsActionsFromOptionalIntegration() {
        MomentumControllerInput.ActionSnapshot expected =
                new MomentumControllerInput.ActionSnapshot(true, false, true, false);
        MomentumControllerInput.install(() -> expected);

        assertEquals(expected, MomentumControllerInput.poll());
    }

    @Test
    void treatsNullIntegrationSnapshotAsNoInput() {
        MomentumControllerInput.install(() -> null);

        assertEquals(MomentumControllerInput.none(), MomentumControllerInput.poll());
    }
}
