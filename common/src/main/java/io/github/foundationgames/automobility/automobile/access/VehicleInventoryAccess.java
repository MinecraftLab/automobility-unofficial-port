package io.github.foundationgames.automobility.automobile.access;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.world.entity.player.Player;
import java.util.UUID;

/** Inventory access is distinct from driving; reuse the existing owner/key/configure policy. */
public final class VehicleInventoryAccess {
    private VehicleInventoryAccess() {}

    public static boolean mayUse(AutomobileEntity car, Player player) {
        if (car == null || player == null) return false;
        return allowed(car.getVehicleAccess(), player.getUUID(), player.isAlive(), car.isAlive(),
                car.isRemoved(), player.level() == car.level(), player.distanceToSqr(car));
    }

    static boolean allowed(VehicleAccessState access, UUID player, boolean playerAlive, boolean carAlive,
                           boolean removed, boolean sameLevel, double distanceSquared) {
        return access != null && player != null && playerAlive && carAlive && !removed && sameLevel
                && Double.isFinite(distanceSquared) && distanceSquared >= 0 && distanceSquared <= 64
                && (access.mayConfigure(player) || access.hasKey(player));
    }
}
