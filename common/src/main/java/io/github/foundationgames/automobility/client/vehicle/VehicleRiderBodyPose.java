package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.world.entity.Entity;

/** Seated player body follows any automobile; vanilla still computes the relative head look. */
public final class VehicleRiderBodyPose {
    private VehicleRiderBodyPose() { }

    public static boolean appliesTo(Entity mount) {
        return mount instanceof AutomobileEntity;
    }

    public static float bodyYawForVehicle(boolean automobilePassenger, float vehicleYaw, float vanillaBodyYaw) {
        return automobilePassenger ? vehicleYaw : vanillaBodyYaw;
    }
}
