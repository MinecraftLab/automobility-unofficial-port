package io.github.milkucha.momentum.api;

import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.accessor.ExternalEngineDriveAccess;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Optional contract for engines whose mod owns propulsion, braking and energy accounting.
 * Register the actual engine registry identifier during common initialization on both sides.
 * Registration persists across worlds and is independent of Momentum's configuration toggles.
 * Momentum retains steering, drift slip and presentation, but never directly changes an owned
 * engine's speed. The owner must reject free boost and exclude drift from regenerative recovery.
 */
public final class ExternalEngineDriveApi {
    public static final int API_VERSION = 1;
    private static final EngineOwnership ENGINES = new EngineOwnership();

    private ExternalEngineDriveApi() { }

    public static void registerEngine(Identifier engineId) {
        ENGINES.register(ResourceKey.create(AutomobileEngine.REGISTRY,
                Objects.requireNonNull(engineId, "engineId")));
    }

    public static boolean ownsDrive(AutomobileEntity automobile) {
        return automobile instanceof ExternalEngineDriveAccess access
                && ENGINES.owns(access.momentum$driveEngine());
    }

    /**
     * Reads one current logical-side sample; it does not alter input or charge a battery.
     * enabled refers to synchronized gameplay and movement settings. When false, the owner
     * uses its own defaults. Raw brake input and unsafeForRecovery remain meaningful when disabled.
     * handbrakeHeld is a configured drift-key braking request, never a request for reverse.
     * A server sample reads the driving passenger's ServerKeyState, never client static state.
     */
    public static DriveState state(AutomobileEntity automobile) {
        Objects.requireNonNull(automobile, "automobile");
        if (!(automobile instanceof ExternalEngineDriveAccess access)) {
            throw new IllegalStateException("Momentum vehicle drive access is unavailable");
        }
        return access.momentum$externalDriveState();
    }

    public record DriveState(boolean enabled, float coastDecay, float brakeDecay,
                             boolean brakeHeld, boolean handbrakeHeld, boolean unsafeForRecovery) { }
}
