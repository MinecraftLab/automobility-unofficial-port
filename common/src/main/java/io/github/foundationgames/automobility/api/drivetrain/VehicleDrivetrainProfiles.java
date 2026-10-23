package io.github.foundationgames.automobility.api.drivetrain;

import io.github.foundationgames.automobility.automobile.drivetrain.EvMotorProfile;
import java.util.*;

/** Immutable installed engine definitions, shared by runtime and unattached garage previews. */
public final class VehicleDrivetrainProfiles {
    public record ElectricProfile(EvMotorProfile motor,double fixedReductionRatio,double parkAxleHoldingTorqueNm,
                                  double coastRegenerationCurveFraction) {
        /** Initial game calibration, subject to the actual owner's capability, headroom and power cap. */
        public ElectricProfile(EvMotorProfile motor,double fixedReductionRatio,double parkAxleHoldingTorqueNm) {
            this(motor,fixedReductionRatio,parkAxleHoldingTorqueNm,.15);
        }
        public ElectricProfile {
            Objects.requireNonNull(motor);
            if(!Double.isFinite(fixedReductionRatio)||fixedReductionRatio<=0||!Double.isFinite(parkAxleHoldingTorqueNm)||parkAxleHoldingTorqueNm<0)throw new IllegalArgumentException("electric profile");
            if(!Double.isFinite(coastRegenerationCurveFraction)||coastRegenerationCurveFraction<0||coastRegenerationCurveFraction>1)throw new IllegalArgumentException("coast regeneration calibration");
        }
    }
    private static Map<String,ElectricProfile> electric=Map.of();
    private static boolean installed;
    private VehicleDrivetrainProfiles() { }
    public static synchronized void installDiscoveredElectric(Map<String,ElectricProfile> profiles){
        if(installed)throw new IllegalStateException("electric profiles already installed");
        electric=Map.copyOf(profiles);installed=true;
    }
    public static Optional<ElectricProfile> electric(String engineRegistryId){return Optional.ofNullable(electric.get(engineRegistryId));}
    public static Map<String,ElectricProfile> electricProfiles(){return electric;}
}
