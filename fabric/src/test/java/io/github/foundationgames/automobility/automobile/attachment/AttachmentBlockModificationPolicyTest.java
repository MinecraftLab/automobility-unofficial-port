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

    @Test
    void automatedAttachmentsFollowDedicatedServerSpawnProtection() {
        assertTrue(AttachmentBlockModificationPolicy.automationIsSpawnProtected(
                true, true, 16, 16, -16));
        assertFalse(AttachmentBlockModificationPolicy.automationIsSpawnProtected(
                true, true, 16, 17, 0));
        assertFalse(AttachmentBlockModificationPolicy.automationIsSpawnProtected(
                false, true, 16, 0, 0));
        assertFalse(AttachmentBlockModificationPolicy.automationIsSpawnProtected(
                true, false, 16, 0, 0));
        assertFalse(AttachmentBlockModificationPolicy.automationIsSpawnProtected(
                true, true, 0, 0, 0));
    }
}
