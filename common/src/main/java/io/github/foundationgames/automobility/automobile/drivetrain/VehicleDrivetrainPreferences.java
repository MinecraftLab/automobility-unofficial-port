package io.github.foundationgames.automobility.automobile.drivetrain;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/** Effective server preferences; client prediction uses only the matching server reply, never local NBT guesses. */
public final class VehicleDrivetrainPreferences {
    private static Supplier<Optional<DrivetrainSettingsPolicy>> serverPolicy = () -> Optional.of(DrivetrainSettingsPolicy.defaults());
    private static Function<AutomobileEntity, Optional<DrivetrainSettingsEnvelope.Settings>> client = car -> Optional.empty();
    private VehicleDrivetrainPreferences() { }
    public static void installServerPolicy(Supplier<Optional<DrivetrainSettingsPolicy>> policy) { serverPolicy = Objects.requireNonNull(policy); }
    public static void installClientResolver(Function<AutomobileEntity, Optional<DrivetrainSettingsEnvelope.Settings>> resolver) { client = Objects.requireNonNull(resolver); }
    public static Optional<DrivetrainSettingsPolicy> serverPolicy() { return Objects.requireNonNull(serverPolicy.get()); }
    public static Optional<DrivetrainSettingsEnvelope.Settings> resolve(AutomobileEntity car) {
        if (car == null || car.isRemoved()) return Optional.empty();
        if (car.level().isClientSide()) return client.apply(car);
        if (!((Object)car instanceof CoreDrivetrainSettingsAccess carrier)) return Optional.empty();
        var envelope = carrier.drivetrain$settingsEnvelope();
        if (envelope.isEmpty()) return Optional.empty();
        var settings = envelope.get().status() == DrivetrainSettingsEnvelope.Status.ABSENT
                ? Optional.of(DrivetrainSettingsPolicy.defaultSettings()) : envelope.get().settings();
        return settings.flatMap(value -> serverPolicy().map(policy -> policy.apply(value)));
    }
}
