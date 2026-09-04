package io.github.foundationgames.automobility.automobile.attachment.rear;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AttachmentMenuValidityTest {
    @Test
    void activeNearbyAttachmentIsValid() {
        assertTrue(AttachmentMenuValidity.isValid(false, true, true, 64.0));
    }

    @Test
    void removedReplacedOrDeadAttachmentIsInvalid() {
        assertFalse(AttachmentMenuValidity.isValid(true, true, true, 0));
        assertFalse(AttachmentMenuValidity.isValid(false, false, true, 0));
        assertFalse(AttachmentMenuValidity.isValid(false, true, false, 0));
    }

    @Test
    void remoteAccessIsInvalid() {
        assertFalse(AttachmentMenuValidity.isValid(false, true, true, Math.nextUp(64.0)));
    }
}
