package io.github.foundationgames.automobility.entity;

final class HitboxCollisionPolicy {
    private HitboxCollisionPolicy() {
    }

    static boolean answersGeneralHardCollisionQuery(boolean parentAutomobilePresent) {
        return parentAutomobilePresent;
    }
}
