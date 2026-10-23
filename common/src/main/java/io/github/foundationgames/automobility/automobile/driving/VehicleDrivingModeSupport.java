package io.github.foundationgames.automobility.automobile.driving;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Optional provider installed by Momentum; no Momentum or client classes are linked from core. */
public final class VehicleDrivingModeSupport {
    public static final int SUPPORTED = 1;
    public static final int ALLOWED = 2;
    public static final double STOPPED_SPEED = 0.02;
    private static BooleanSupplier policy;
    private VehicleDrivingModeSupport() {}

    public static void install(BooleanSupplier allowed) { policy = Objects.requireNonNull(allowed); }
    public static int capabilities() {
        return policy == null ? 0 : SUPPORTED | (policy.getAsBoolean() ? ALLOWED : 0);
    }

    /** Both local velocity and server-observed travel must have settled before changing the tune. */
    public static boolean stationary(AutomobileEntity car) {
        double speed = Math.max(car.calculateEffectiveSpeed(), Math.abs(car.getHSpeed()));
        speed = Math.max(speed, car.getDeltaMovement().horizontalDistance());
        if (!car.level().isClientSide()) speed = Math.max(speed, car.getMeasuredMovement().horizontalDistance());
        return Double.isFinite(speed) && speed <= STOPPED_SPEED;
    }

    /** Recheck the live server policy; advertised client capabilities are only a UI hint. */
    public static boolean mayChange(AutomobileEntity car, VehicleDrivingMode requested) {
        if (requested == null) return false;
        int status = capabilities();
        return mayChange(car.getVehicleDrivingMode(), requested, (status & SUPPORTED) != 0,
                (status & ALLOWED) != 0, stationary(car));
    }

    public static boolean mayChange(VehicleDrivingMode current, VehicleDrivingMode requested,
                                    boolean supported, boolean allowed, boolean stationary) {
        return current != null && requested != null && (current == requested
                || supported && stationary && (requested == VehicleDrivingMode.INHERIT || allowed));
    }
}
