package io.github.foundationgames.automobility.automobile.attachment.rear;

public final class AttachmentMenuValidity {
    private static final double MAX_DISTANCE_SQUARED = 64.0;

    private AttachmentMenuValidity() {
    }

    public static boolean isValid(boolean removed, boolean automobileAlive, boolean sameLevel,
                                  boolean currentAttachment, double distanceSquared) {
        return !removed
                && automobileAlive
                && sameLevel
                && currentAttachment
                && Double.isFinite(distanceSquared)
                && distanceSquared <= MAX_DISTANCE_SQUARED;
    }
}
