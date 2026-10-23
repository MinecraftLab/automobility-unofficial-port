package io.github.foundationgames.automobility.automobile.driving;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.function.ToDoubleFunction;
/** Optional negotiated assistance: reduce only the ordinary positive motor contribution. */
public final class VehicleCruiseMotorLimit {
    private static ToDoubleFunction<AutomobileEntity> approvedTarget = car -> Double.NaN;
    private static java.util.function.BiConsumer<AutomobileEntity, MechanicalReason> clientCancellation = (car, reason) -> {};
    private static java.util.function.BiConsumer<AutomobileEntity, MechanicalReason> serverCancellation = (car, reason) -> {};
    public enum MechanicalReason { CLUTCH, NEUTRAL, ENGINE_STOPPED }
    private VehicleCruiseMotorLimit() {}
    /** Separate registrations preserve integrated-server side ownership in one JVM. */
    public static void installMechanicalCancellation(boolean client, java.util.function.BiConsumer<AutomobileEntity, MechanicalReason> callback) {
        java.util.Objects.requireNonNull(callback);
        if (client) clientCancellation = callback; else serverCancellation = callback;
    }
    public static void cancelForMechanics(AutomobileEntity car, MechanicalReason reason) {
        java.util.Objects.requireNonNull(car); java.util.Objects.requireNonNull(reason);
        (car.level().isClientSide() ? clientCancellation : serverCancellation).accept(car, reason);
    }
    public static boolean mayEngage(AutomobileEntity car) { return car != null && car.drivetrainAllowsCruise(); }
    public static void install(ToDoubleFunction<AutomobileEntity> policy) { approvedTarget = java.util.Objects.requireNonNull(policy); }
    /** The existing cruise lease policy is the only source of an approved target. NaN means inactive. */
    public static double approvedTargetMetresPerSecond(AutomobileEntity car) {
        double target = approvedTarget.applyAsDouble(car);
        return Double.isFinite(target) && target >= 0.0 ? target * 20.0 : Double.NaN;
    }
    public static float constrain(AutomobileEntity car, float before, float proposed) {
        return reduce(before, proposed, approvedTarget.applyAsDouble(car));
    }
    public static float reduce(float before, float proposed, double targetBlocksPerTick) {
        if (!Float.isFinite(before) || !Float.isFinite(proposed) || !Double.isFinite(targetBlocksPerTick)
                || before < 0 || targetBlocksPerTick < 0 || proposed <= before) return proposed;
        return (float) Math.max(before, Math.min(proposed, targetBlocksPerTick));
    }
}
