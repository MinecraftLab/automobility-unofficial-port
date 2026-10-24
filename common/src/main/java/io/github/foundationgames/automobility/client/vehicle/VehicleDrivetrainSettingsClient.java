package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.automobile.drivetrain.DrivetrainSettingsEnvelope.Settings;
import io.github.foundationgames.automobility.automobile.drivetrain.DrivetrainSettingsMessages.Message;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.Objects;
import java.util.Optional;

/** Client UI bridge. Only a real server reply is an accepted preference update. */
public final class VehicleDrivetrainSettingsClient {
    public interface Adapter {
        boolean available(AutomobileEntity car);
        void query(AutomobileEntity car);
        Optional<Message> view(AutomobileEntity car);
        boolean save(AutomobileEntity car, Settings requested, boolean resetUnrecognized);
        boolean pending(AutomobileEntity car);
        default boolean saving(AutomobileEntity car) { return pending(car); }
    }
    private static Adapter adapter;
    private VehicleDrivetrainSettingsClient() { }
    public static void install(Adapter value) { adapter = Objects.requireNonNull(value); }
    public static boolean available(AutomobileEntity car) { return adapter != null && adapter.available(car); }
    public static void query(AutomobileEntity car) { if (available(car)) adapter.query(car); }
    public static Optional<Message> view(AutomobileEntity car) { return available(car) ? adapter.view(car) : Optional.empty(); }
    public static boolean save(AutomobileEntity car, Settings requested, boolean reset) { return available(car) && adapter.save(car, requested, reset); }
    public static boolean pending(AutomobileEntity car) { return adapter != null && adapter.pending(car); }
    public static boolean saving(AutomobileEntity car) { return adapter != null && adapter.saving(car); }
}
