package io.github.foundationgames.automobility.api.drivetrain;

import io.github.foundationgames.automobility.automobile.drivetrain.DrivetrainProtocol;
import java.util.Optional;

/**
 * Core's mechanical ownership is distinct from optional paid-propulsion ownership.
 * Once selected, the negotiated runtime retains longitudinal ownership during resource denial
 * or an unavailable owner and produces an unpowered coast. Those failures never enable a
 * second legacy propulsion/debit path. Vehicles that never qualify retain their legacy path.
 */
public interface VehicleMechanicalAccess {
    boolean drivetrainOwnsLongitudinalMotion();
    Optional<DrivetrainProtocol.Snapshot> drivetrainSnapshot();

    static boolean active(Object vehicle) {
        return vehicle instanceof VehicleMechanicalAccess access && access.drivetrainOwnsLongitudinalMotion();
    }
}
