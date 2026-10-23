package io.github.foundationgames.automobility.util.duck;

import net.minecraft.world.entity.player.Player;

/** Only vehicle-bound storage implements this; ordinary world inventories remain vanilla. */
public interface VehicleCargoAccess {
    boolean automobility$mayUseCargo(Player player);
}
