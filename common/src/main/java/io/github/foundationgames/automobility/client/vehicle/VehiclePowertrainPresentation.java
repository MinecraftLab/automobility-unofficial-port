package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.api.drivetrain.VehicleMechanicalAccess;
import io.github.foundationgames.automobility.automobile.drivetrain.*;
import io.github.foundationgames.automobility.automobile.drivetrain.metadata.RuntimeMetadata018;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.Optional;
import java.util.WeakHashMap;
import io.github.foundationgames.automobility.client.vehicle.energy.VehicleEnergyAdapters;

/** Read-only projection shared by instruments, the engine loop and optional cabin acoustics. */
public final class VehiclePowertrainPresentation {
    public record Reading(DrivetrainProtocol.Snapshot snapshot, double signedRpm, double maximumRpm, boolean electric,
                          DrivetrainSoundModel.Voice voice) {
        /** Retain the previous constructor consumed by optional integrations. */
        public Reading(DrivetrainProtocol.Snapshot snapshot, double signedRpm, double maximumRpm, boolean electric) {
            this(snapshot, signedRpm, maximumRpm, electric, electric ? DrivetrainSoundModel.Voice.ELECTRIC : DrivetrainSoundModel.Voice.DEFAULT);
        }
        public DrivetrainSoundModel.Tone tone() {
            return DrivetrainSoundModel.sample(signedRpm, maximumRpm, snapshot.load(), snapshot.enginePhase(), voice);
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
    private record VoiceCache(long tick, Object engine, DrivetrainSoundModel.Voice voice) { }
    private static final WeakHashMap<AutomobileEntity, VoiceCache> VOICES = new WeakHashMap<>();
    private VehiclePowertrainPresentation() { }
    /** Uses the producer's required fuel, including its engine-specific configuration, for nearby cars too. */
    public static DrivetrainSoundModel.Voice voice(AutomobileEntity car) {
        if (car == null || car.isRemoved()) return DrivetrainSoundModel.Voice.DEFAULT;
        long tick = car.level().getGameTime();
        var previous = VOICES.get(car);
        if (previous != null && previous.tick() == tick && previous.engine() == car.getEngine()) return previous.voice();
        var energy = VehicleEnergyAdapters.read(car);
        var voice = energy.kind() == VehicleEnergyReading.Kind.BATTERY ? DrivetrainSoundModel.Voice.ELECTRIC
                : energy.kind() == VehicleEnergyReading.Kind.FUEL ? energy.diesel() ? DrivetrainSoundModel.Voice.DIESEL : DrivetrainSoundModel.Voice.PETROL
                : DrivetrainSoundModel.Voice.DEFAULT;
        VOICES.put(car, new VoiceCache(tick, car.getEngine(), voice));
        return voice;
    }
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
            return new Reading(snapshot, rpm, engine.combustion() != null ? engine.combustion().redlineRpm() : engine.electric().maximumRpm(), engine.electric() != null, engine.electric() != null ? DrivetrainSoundModel.Voice.ELECTRIC : voice(car));
        });
    }
    /** Actual receipt-qualified SI interval measurements and typed unavailability; no peak-times-throttle estimate. */
    public static Optional<RuntimeMetadata018.Measured> measured(AutomobileEntity car) {
        return car == null || car.isRemoved() ? Optional.empty() : CoreDrivetrainBridge.clientMetadata(car).map(RuntimeMetadata018::measured);
    }
}
