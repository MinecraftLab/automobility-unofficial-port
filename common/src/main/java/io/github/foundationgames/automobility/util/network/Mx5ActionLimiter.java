package io.github.foundationgames.automobility.util.network;

import net.minecraft.server.MinecraftServer;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Same bounded per-player budget as existing settings, isolated per server lifecycle. */
public final class Mx5ActionLimiter {
    private static final Map<MinecraftServer, PlayerPacketRateLimiter> SERVERS = new IdentityHashMap<>();
    private Mx5ActionLimiter() { }
    public static boolean tryAcquire(MinecraftServer server, UUID player, long tick) {
        return SERVERS.computeIfAbsent(server, ignored -> new PlayerPacketRateLimiter(4, 16, 20, 1200))
                .tryAcquire(player, tick);
    }
    public static void clear(MinecraftServer server) { SERVERS.remove(server); }
}
