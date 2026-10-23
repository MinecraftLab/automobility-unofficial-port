package io.github.foundationgames.automobility.api.drivetrain.energy;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.api.drivetrain.VehicleDrivetrainProfiles;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Optional real-storage owner. Discovering/binding it never grants mechanical ownership. */
public interface VehicleEnergyProvider {
    String ENTRYPOINT = "automobility:drivetrain_energy_provider";
    String id();
    EnergyDemand.Source source();
    /** Read-only classification of this actual vehicle/engine; an empty tank is still managed. */
    boolean manages(AutomobileEntity car);
    /** Bind once per mechanical identity and reuse with that runtime's EnergyTransactionGuard. */
    Binding bind(AutomobileEntity car, EnergyDemand.Identity identity);
    default Map<String,VehicleDrivetrainProfiles.ElectricProfile> electricProfiles(){return Map.of();}
    /** Read-only actual physical motor identity; missing/malformed raw data never mints a battery. */
    default Optional<UUID> physicalMotorId(AutomobileEntity car){return Optional.empty();}

    interface Binding extends EnergyParticipant {
        /** Owner-owned pricing, including its real config, source policy, losses and fractional debt. */
        EnergyPlan price(Preview preview);
    }
}
