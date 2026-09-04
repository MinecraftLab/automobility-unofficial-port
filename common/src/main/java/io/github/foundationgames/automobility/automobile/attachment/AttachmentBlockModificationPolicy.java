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
}
