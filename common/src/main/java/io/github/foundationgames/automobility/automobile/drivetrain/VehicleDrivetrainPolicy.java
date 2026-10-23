package io.github.foundationgames.automobility.automobile.drivetrain;

import io.github.foundationgames.automobility.automobile.driving.VehicleCruiseMotorLimit;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.Objects;

/** Read-only server-approved mode/cruise/handbrake policy. It neither samples keys nor owns physics. */
public final class VehicleDrivetrainPolicy {
    public record Policy(AutomaticShiftController.Mode mode,double approvedCruiseTargetMetresPerSecond,
                         boolean handbrakeHeld,boolean unsafeForRecovery) {
        public Policy(AutomaticShiftController.Mode mode,double target){this(mode,target,false,false);}
        public Policy {
            Objects.requireNonNull(mode);
            if(!Double.isNaN(approvedCruiseTargetMetresPerSecond)&&(!Double.isFinite(approvedCruiseTargetMetresPerSecond)||approvedCruiseTargetMetresPerSecond<0))throw new IllegalArgumentException("approved cruise target");
        }
    }
    @FunctionalInterface public interface Resolver { Policy resolve(AutomobileEntity car); }
    private static Resolver resolver=car->new Policy(switch(car.getVehicleDrivingMode()) {
        case TOURING->AutomaticShiftController.Mode.TOURING;case SPORT->AutomaticShiftController.Mode.SPORT;
        case INHERIT,BALANCED->AutomaticShiftController.Mode.BALANCED;
    },VehicleCruiseMotorLimit.approvedTargetMetresPerSecond(car),false,car.isDrifting()||car.burningOut());
    private VehicleDrivetrainPolicy() { }
    public static void install(Resolver policy){resolver=Objects.requireNonNull(policy);}
    public static Policy resolve(AutomobileEntity car){return Objects.requireNonNull(resolver.resolve(car));}
}
