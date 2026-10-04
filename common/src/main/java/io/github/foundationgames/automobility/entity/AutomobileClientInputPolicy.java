package io.github.foundationgames.automobility.entity;

final class AutomobileClientInputPolicy {
    private AutomobileClientInputPolicy() {
    }

    static boolean isFresh(long lastAcceptedTick, long currentTick, long timeoutTicks) {
        return lastAcceptedTick != Long.MIN_VALUE
                && currentTick >= lastAcceptedTick
                && currentTick - lastAcceptedTick <= timeoutTicks;
    }
}
