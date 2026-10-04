package io.github.foundationgames.automobility.automobile.attachment;

final class AttachmentBlockModificationPolicy {
    private AttachmentBlockModificationPolicy() {
    }

    static boolean playerHasDirectPermission(boolean mayBuild, boolean mayInteract, boolean spawnProtected) {
        return mayBuild && mayInteract && !spawnProtected;
    }

    static boolean protectedFromAllAttachmentActions(boolean mayInteract, boolean spawnProtected) {
        return !mayInteract || spawnProtected;
    }

    static boolean automationIsSpawnProtected(boolean spawnDimension, boolean operatorsConfigured,
                                               int protectionRadius, long distanceX, long distanceZ) {
        return spawnDimension
                && operatorsConfigured
                && protectionRadius > 0
                && Math.max(Math.abs(distanceX), Math.abs(distanceZ)) <= protectionRadius;
    }
}
