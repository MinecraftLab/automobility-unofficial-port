package io.github.foundationgames.automobility.automobile.driving;

import io.github.foundationgames.automobility.automobile.AutomobileStats;
import io.github.foundationgames.automobility.entity.GarageService;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Immutable parts comparison, independent of world entities and renderer ownership.
 * Acceleration/handling/grip are production parts indices, not measured acceleration times
 * or cornering limits. Speed is the soft acceleration threshold in blocks/second, never top speed.
 */
public final class GarageDrivingStats {
    public record Snapshot(float acceleration, float speedBlocksPerSecond, float handling,
                           float grip, float braking, boolean extended) {}

    @FunctionalInterface
    public interface Provider {
        Snapshot resolve(Snapshot original, float rawThreshold, Identifier frameId, VehicleDrivingMode selected);
    }

    private static volatile Provider provider = (original, rawThreshold, frameId, selected) -> original;

    private GarageDrivingStats() {}

    public static void install(Provider implementation) {
        provider = Objects.requireNonNull(implementation);
    }

    /** Reads the current authoritative gameplay snapshot through the installed driving provider. */
    public static Snapshot snapshot(GarageService.Components parts, VehicleDrivingMode mode) {
        if (parts == null || parts.frame() == null || parts.wheel() == null || parts.engine() == null)
            return null;
        var frame = parts.frame().value();
        var wheel = parts.wheel().value();
        var engine = parts.engine().value();
        if (frame.empty() || wheel.empty() || engine.empty()) return null;
        var stats = new AutomobileStats();
        stats.from(frame, wheel, engine);
        // Deliberately bypass the runtime comfortable-speed mixin: apply the selected tune once.
        var original = new Snapshot(stats.getAcceleration(), stats.getBaseComfortableSpeed() * 20,
                stats.getHandling(), stats.getGrip(), .15f, false);
        Identifier frameId = parts.frame().unwrapKey().map(key -> key.identifier()).orElse(null);
        return provider.resolve(original, stats.getBaseComfortableSpeed(), frameId,
                mode == null ? VehicleDrivingMode.INHERIT : mode);
    }
}
