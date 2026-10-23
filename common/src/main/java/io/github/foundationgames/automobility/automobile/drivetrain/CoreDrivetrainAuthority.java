package io.github.foundationgames.automobility.automobile.drivetrain;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.server.level.ServerPlayer;

/** Vehicle pose authority is separate from the unchanged legacy 86-byte content/input payload. */
public final class CoreDrivetrainAuthority {
    public interface ConnectionAccess {
        void drivetrain$refreshAuthority(AutomobileEntity car, boolean active);
    }
    private CoreDrivetrainAuthority() { }
    public static void refresh(AutomobileEntity car) {
        if (!car.level().isClientSide() && car.getControllingPassenger() instanceof ServerPlayer player
                && player.getRootVehicle() == car && player.connection instanceof ConnectionAccess access) {
            access.drivetrain$refreshAuthority(car, car.drivetrainOwnsLongitudinalMotion());
        }
    }
}
