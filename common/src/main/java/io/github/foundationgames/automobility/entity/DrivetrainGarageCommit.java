package io.github.foundationgames.automobility.entity;

import io.github.foundationgames.automobility.automobile.drivetrain.CoreDrivetrainBridge;
import io.github.foundationgames.automobility.automobile.drivetrain.DrivetrainSettingsEnvelope;
import io.github.foundationgames.automobility.automobile.garage.GarageTransferParticipant.Request;

/** Only the Core transaction producer in this package calls publish after its successful COMMIT checkpoint. */
public final class DrivetrainGarageCommit {
    private record Scope(AutomobileEntity car, DrivetrainSettingsEnvelope.GarageReceipt receipt) { }
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private DrivetrainGarageCommit() { }
    static void publish(AutomobileEntity car, Request request) {
        if (CURRENT.get() != null || car.level().isClientSide() || !car.getUUID().equals(request.vehicle()))
            throw new IllegalStateException("invalid drivetrain commit publication scope");
        var receipt = new DrivetrainSettingsEnvelope.GarageReceipt(request.transaction(), request.vehicle(), request.generation(),
                DrivetrainSettingsEnvelope.GarageDecision.COMMIT);
        CURRENT.set(new Scope(car, receipt));
        try { CoreDrivetrainBridge.committedGarage(car, receipt); } finally { CURRENT.remove(); }
    }
    public static boolean permits(AutomobileEntity car, DrivetrainSettingsEnvelope.GarageReceipt receipt) {
        var current = CURRENT.get();
        return current != null && current.car() == car && current.receipt().equals(receipt)
                && !car.level().isClientSide() && car.getUUID().equals(receipt.vehicle());
    }
}
