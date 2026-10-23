package io.github.milkucha.momentum;

import io.github.foundationgames.automobility.automobile.driving.VehicleDrivingModeSupport;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.milkucha.momentum.network.GameplayConfigSync;
import net.minecraft.server.MinecraftServer;

/** Called only on the server thread by an authorized command or the local integrated host. */
public final class MomentumDrivingModel {
    private MomentumDrivingModel() {}

    public static boolean mayChange(MinecraftServer server) {
        for (var level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof AutomobileEntity car && !VehicleDrivingModeSupport.stationary(car))
                    return false;
            }
        }
        return true;
    }

    public record ReloadResult(boolean modelChangeRequested, boolean modelChangeBlocked, int recipients) {}

    /** Entire operation runs on the server thread; client F6 passes allowModelChange=false. */
    public static ReloadResult reload(MinecraftServer server, boolean allowModelChange) {
        boolean active = MomentumConfig.get().enabled;
        var candidate = MomentumConfig.readReloadCandidate();
        boolean requested = candidate.enabled != active;
        boolean permitted = allowModelChange && (!requested || mayChange(server));
        MomentumConfig.reconcileReload(candidate, active, permitted);
        MomentumConfig.installAndSave(candidate);
        return new ReloadResult(requested, requested && !permitted, GameplayConfigSync.sendToAll(server));
    }

    public static boolean change(MinecraftServer server, boolean extended) {
        var config = MomentumConfig.get();
        if (config.enabled != extended && !mayChange(server)) return false;
        config.enabled = extended;
        config.save();
        GameplayConfigSync.sendToAll(server);
        return true;
    }
}
