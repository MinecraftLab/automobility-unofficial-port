package io.github.foundationgames.automobility.automobile.garage;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Read-only global gate for owner load/tick/refuel/charge/attach/drive hooks. */
public final class GarageTransferAccess {
    private GarageTransferAccess() {}
    public interface Authority {
        boolean blocked(MinecraftServer server, UUID vehicle);
        boolean quietLoading(MinecraftServer server, UUID vehicle);
        /** Actual object/world/UUID/generation admission; includes stale copies after finalized transactions. */
        boolean blocked(AutomobileEntity car);
        /** Only the exact bound object in the current server-thread materialization scope. */
        boolean quietLoading(AutomobileEntity car);
    }
    private static volatile Authority authority;
    private static final Map<String, GarageTransferParticipant> OWNERS = new TreeMap<>();
    private static boolean sealed;
    public static synchronized boolean register(GarageTransferParticipant owner) {
        if (sealed || owner == null || owner.apiVersion() != GarageTransferParticipant.API_VERSION
                || owner.capsuleVersion() < 1 || owner.id() == null || !owner.id().matches("[a-z0-9_]{1,48}")
                || OWNERS.containsKey(owner.id()) || OWNERS.size() >= 16) return false;
        OWNERS.put(owner.id(), owner); return true;
    }
    public static synchronized List<GarageTransferParticipant> participants() { return List.copyOf(OWNERS.values()); }
    public static synchronized void sealRegistration() { sealed = true; }
    public static void install(Authority value) {
        if (value == null || authority != null) throw new IllegalStateException("Garage authority installs once");
        authority = value;
    }
    /** Missing initialization cannot open an unresolved server-side operation. */
    public static boolean blocked(MinecraftServer server, UUID vehicle) {
        var current = authority;
        return server != null && vehicle != null && (current == null || current.blocked(server, vehicle));
    }
    public static boolean blocked(AutomobileEntity car) {
        var current = authority;
        return car != null && car.level() instanceof ServerLevel && (current == null || current.blocked(car));
    }
    public static boolean quietLoading(MinecraftServer server, UUID vehicle) {
        var current = authority;
        return current != null && server != null && vehicle != null && current.quietLoading(server, vehicle);
    }
    public static boolean quietLoading(AutomobileEntity car) {
        var current = authority;
        return current != null && car != null && car.level() instanceof ServerLevel && current.quietLoading(car);
    }
}
