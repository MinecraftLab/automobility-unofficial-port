package io.github.foundationgames.automobility.util.network;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPacketRateLimiterTest {
    @Test
    void enforcesPerTickAndWindowLimitsPerPlayer() {
        var limiter = new PlayerPacketRateLimiter(2, 3, 20, 100);
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(first, 10));
        assertTrue(limiter.tryAcquire(first, 10));
        assertFalse(limiter.tryAcquire(first, 10), "per-tick burst must be bounded");

        assertTrue(limiter.tryAcquire(first, 11));
        assertFalse(limiter.tryAcquire(first, 12), "window total must be bounded");
        assertTrue(limiter.tryAcquire(second, 12), "players must have independent budgets");

        assertTrue(limiter.tryAcquire(first, 30), "a completed window must replenish the budget");
    }

    @Test
    void resetsSafelyWhenGameTimeMovesBackwards() {
        var limiter = new PlayerPacketRateLimiter(1, 1, 20, 100);
        var player = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(player, 200));
        assertFalse(limiter.tryAcquire(player, 201));
        assertTrue(limiter.tryAcquire(player, 5));
    }

    @Test
    void prunesInactivePlayers() {
        var limiter = new PlayerPacketRateLimiter(2, 10, 20, 100);
        var stale = UUID.randomUUID();
        var active = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(stale, 0));
        assertTrue(limiter.tryAcquire(active, 101));

        assertEquals(1, limiter.trackedPlayers());
    }
}
