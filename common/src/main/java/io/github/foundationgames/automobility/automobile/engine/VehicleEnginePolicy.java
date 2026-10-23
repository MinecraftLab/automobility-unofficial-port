package io.github.foundationgames.automobility.automobile.engine;

import com.google.gson.GsonBuilder;
import io.github.foundationgames.automobility.Automobility;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/** Administrator policy, independent of optional mods and saved per-vehicle preferences. */
public final class VehicleEnginePolicy {
    private static final java.util.Map<net.minecraft.server.MinecraftServer, String> SESSIONS = new java.util.WeakHashMap<>();
    private static int maximumIdleSeconds = 3600;
    private static boolean allowNeverShutdown = true;
    private VehicleEnginePolicy() {}

    public static synchronized String session(net.minecraft.server.MinecraftServer server) {
        return SESSIONS.computeIfAbsent(server, ignored -> UUID.randomUUID().toString());
    }

    public static VehicleEngineSettings apply(VehicleEngineSettings value) {
        return value.restricted(maximumIdleSeconds, allowNeverShutdown);
    }
    public static int maximumIdleSeconds() { return maximumIdleSeconds; }
    public static boolean allowNeverShutdown() { return allowNeverShutdown; }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("automobility-vehicle-engine.json");
        try {
            var gson = new GsonBuilder().setPrettyPrinting().create();
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, gson.toJson(Map.of("maximumIdleSeconds", maximumIdleSeconds,
                        "allowNeverShutdown", allowNeverShutdown)) + "\n", java.nio.file.StandardOpenOption.CREATE_NEW);
                return;
            }
            var value = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            int maximum = value.get("maximumIdleSeconds").getAsInt();
            boolean allow = value.get("allowNeverShutdown").getAsBoolean();
            if (maximum < 0 || maximum > VehicleEngineSettings.MAX_IDLE_SECONDS
                    || !value.get("allowNeverShutdown").getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Invalid server engine policy");
            maximumIdleSeconds = maximum;
            allowNeverShutdown = allow;
        } catch (Exception error) {
            Automobility.LOG.warn("Could not read vehicle engine policy; using safe defaults and preserving the file", error);
        }
    }
}
