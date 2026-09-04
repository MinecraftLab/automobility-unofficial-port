package io.github.foundationgames.automobility.automobile.attachment.rear;

final class AttachmentMenuValidity {
    static final double MAX_DISTANCE_SQUARED = 64.0;

    private AttachmentMenuValidity() {
    }

    static boolean isValid(boolean removed, boolean automobileAlive, boolean currentAttachment, double distanceSquared) {
        return !removed
                && automobileAlive
                && currentAttachment
                && Double.isFinite(distanceSquared)
                && distanceSquared <= MAX_DISTANCE_SQUARED;
    }
}
