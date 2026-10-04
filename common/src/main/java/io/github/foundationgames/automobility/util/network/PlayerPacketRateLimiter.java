package io.github.foundationgames.automobility.util.network;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server-tick based packet budget with independent limits for each player.
 *
 * <p>This class deliberately does not retain {@code ServerPlayer} instances. UUID entries are
 * periodically pruned so reconnects and long-running public servers cannot grow the map forever.</p>
 */
final class PlayerPacketRateLimiter {
    private final int maxPerTick;
    private final int maxPerWindow;
    private final long windowTicks;
    private final long staleAfterTicks;
    private final Map<UUID, Window> players = new HashMap<>();
    private long lastCleanupTick = Long.MIN_VALUE;

    PlayerPacketRateLimiter(int maxPerTick, int maxPerWindow, long windowTicks, long staleAfterTicks) {
        if (maxPerTick <= 0 || maxPerWindow <= 0 || windowTicks <= 0 || staleAfterTicks <= 0) {
            throw new IllegalArgumentException("Packet limits and tick intervals must be positive");
        }

        this.maxPerTick = maxPerTick;
        this.maxPerWindow = maxPerWindow;
        this.windowTicks = windowTicks;
        this.staleAfterTicks = staleAfterTicks;
    }

    boolean tryAcquire(UUID playerId, long currentTick) {
        cleanupIfNeeded(currentTick);

        var window = this.players.computeIfAbsent(playerId, ignored -> new Window(currentTick));
        if (currentTick < window.lastSeenTick) {
            window.reset(currentTick);
        } else if (currentTick - window.windowStartTick >= this.windowTicks) {
            window.resetWindow(currentTick);
        }

        if (currentTick != window.currentTick) {
            window.currentTick = currentTick;
            window.requestsThisTick = 0;
        }
        window.lastSeenTick = currentTick;

        if (window.requestsThisTick >= this.maxPerTick || window.requestsThisWindow >= this.maxPerWindow) {
            return false;
        }

        window.requestsThisTick++;
        window.requestsThisWindow++;
        return true;
    }

    int trackedPlayers() {
        return this.players.size();
    }

    private void cleanupIfNeeded(long currentTick) {
        if (this.lastCleanupTick != Long.MIN_VALUE
                && currentTick >= this.lastCleanupTick
                && currentTick - this.lastCleanupTick < this.staleAfterTicks) {
            return;
        }

        Iterator<Window> iterator = this.players.values().iterator();
        while (iterator.hasNext()) {
            var window = iterator.next();
            if (currentTick >= window.lastSeenTick && currentTick - window.lastSeenTick >= this.staleAfterTicks) {
                iterator.remove();
            }
        }
        this.lastCleanupTick = currentTick;
    }

    private static final class Window {
        private long windowStartTick;
        private long currentTick;
        private long lastSeenTick;
        private int requestsThisTick;
        private int requestsThisWindow;

        private Window(long currentTick) {
            reset(currentTick);
        }

        private void reset(long currentTick) {
            this.lastSeenTick = currentTick;
            resetWindow(currentTick);
        }

        private void resetWindow(long currentTick) {
            this.windowStartTick = currentTick;
            this.currentTick = currentTick;
            this.requestsThisTick = 0;
            this.requestsThisWindow = 0;
        }
    }
}
