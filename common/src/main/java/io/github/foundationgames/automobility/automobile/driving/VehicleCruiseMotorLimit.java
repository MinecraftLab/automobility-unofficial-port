package io.github.foundationgames.automobility.automobile.driving;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.function.ToDoubleFunction;
/** Optional negotiated assistance: reduce only the ordinary positive motor contribution. */
public final class VehicleCruiseMotorLimit {
    private static ToDoubleFunction<AutomobileEntity> approvedTarget = car -> Double.NaN;
    private VehicleCruiseMotorLimit() {}
    public static void install(ToDoubleFunction<AutomobileEntity> policy) { approvedTarget = java.util.Objects.requireNonNull(policy); }
    public static float constrain(AutomobileEntity car, float before, float proposed) {
        return reduce(before, proposed, approvedTarget.applyAsDouble(car));
    }
    public static float reduce(float before, float proposed, double targetBlocksPerTick) {
        if (!Float.isFinite(before) || !Float.isFinite(proposed) || !Double.isFinite(targetBlocksPerTick)
                || before < 0 || targetBlocksPerTick < 0 || proposed <= before) return proposed;
        return (float) Math.max(before, Math.min(proposed, targetBlocksPerTick));
    }
}
