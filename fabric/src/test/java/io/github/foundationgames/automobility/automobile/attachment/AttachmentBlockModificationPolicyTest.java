package io.github.foundationgames.automobility.automobile.attachment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AttachmentBlockModificationPolicyTest {
    @Test
    void directPlayerPermissionRequiresEveryVanillaGate() {
        assertTrue(AttachmentBlockModificationPolicy.playerHasDirectPermission(true, true, false));
        assertFalse(AttachmentBlockModificationPolicy.playerHasDirectPermission(false, true, false));
        assertFalse(AttachmentBlockModificationPolicy.playerHasDirectPermission(true, false, false));
        assertFalse(AttachmentBlockModificationPolicy.playerHasDirectPermission(true, true, true));
    }

    @Test
    void worldBorderAndSpawnProtectionCannotBeBypassedByAutomation() {
        assertTrue(AttachmentBlockModificationPolicy.protectedFromAllAttachmentActions(false, false));
        assertTrue(AttachmentBlockModificationPolicy.protectedFromAllAttachmentActions(true, true));
        assertFalse(AttachmentBlockModificationPolicy.protectedFromAllAttachmentActions(true, false));
    }
}
