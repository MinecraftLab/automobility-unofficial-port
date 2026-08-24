package io.github.foundationgames.automobility.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HitboxCollisionPolicyTest {
    @Test
    void generalHardCollisionQueryRequiresAParentAutomobile() {
        assertTrue(HitboxCollisionPolicy.answersGeneralHardCollisionQuery(true));
        assertFalse(HitboxCollisionPolicy.answersGeneralHardCollisionQuery(false));
    }
}
