package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.api.drivetrain.VehicleMechanicalAccess;
import io.github.foundationgames.automobility.automobile.drivetrain.*;
import io.github.foundationgames.automobility.automobile.drivetrain.metadata.RuntimeMetadata018;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.Optional;

/** Read-only projection shared by instruments, the engine loop and optional cabin acoustics. */
public final class VehiclePowertrainPresentation {
    public record Reading(DrivetrainProtocol.Snapshot snapshot, double signedRpm, double maximumRpm, boolean electric) {
        public DrivetrainSoundModel.Tone tone() {
            return DrivetrainSoundModel.sample(signedRpm, maximumRpm, snapshot.load(), snapshot.enginePhase(), electric);
        }
        public String rangeLabel() {
            if (snapshot.parkingEngaged()) return "P";
            if (snapshot.gear() < 0) return "R";
            if (snapshot.gear() == 0) return "N";
            if (electric) return "D";
            String gear = Integer.toString(snapshot.gear());
            return snapshot.phase() == AutomaticShiftController.Phase.DRIVE ? gear : gear + "›" + snapshot.targetGear();
        }
    }
    private VehiclePowertrainPresentation() { }
    public static Optional<Reading> reading(AutomobileEntity car) {
        if (car == null || car.isRemoved()) return Optional.empty();
        Optional<DrivetrainProtocol.Snapshot> state = (Object)car instanceof VehicleMechanicalAccess access
                ? access.drivetrainSnapshot() : CoreDrivetrainBridge.clientSnapshot(car);
        if (state.isEmpty() || !state.get().identity().vehicle().equals(car.getUUID())) return Optional.empty();
        var snapshot = state.get();
        double rpm = Units.radiansPerSecondToRpm(snapshot.engineOmega());
        var metadata = CoreDrivetrainBridge.clientMetadata(car).orElse(null);
        if (metadata == null || !metadata.pairs(snapshot)) return Optional.empty();
        return CoreDrivetrainBridge.clientDefinition(car, metadata).map(selection -> {
            var engine = selection.engine();
            return new Reading(snapshot, rpm, engine.combustion() != null ? engine.combustion().redlineRpm() : engine.electric().maximumRpm(), engine.electric() != null);
        });
    }
    /** Actual receipt-qualified SI interval measurements and typed unavailability; no peak-times-throttle estimate. */
    public static Optional<RuntimeMetadata018.Measured> measured(AutomobileEntity car) {
        return car == null || car.isRemoved() ? Optional.empty() : CoreDrivetrainBridge.clientMetadata(car).map(RuntimeMetadata018::measured);
    }
}
