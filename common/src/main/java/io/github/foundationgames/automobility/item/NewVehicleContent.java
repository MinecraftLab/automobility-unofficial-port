package io.github.foundationgames.automobility.item;

import io.github.foundationgames.automobility.automobile.AutomobileData;
import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import io.github.foundationgames.automobility.automobile.AutomobileWheel;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/** Creative visibility only. A legacy server keeps every existing component and prefab. */
public final class NewVehicleContent {
    private static final Identifier FRAMES = Identifier.parse("automobility:automobile_frame");
    private static final Identifier WHEELS = Identifier.parse("automobility:automobile_wheel");
    private static final Set<Identifier> NEW_FRAMES = NewVehiclePrefabs.definitions().stream()
            .map(NewVehiclePrefabs.Definition::frame).collect(Collectors.toUnmodifiableSet());
    private static final Set<Identifier> NEW_WHEELS = NewVehiclePrefabs.definitions().stream()
            .map(NewVehiclePrefabs.Definition::wheel).collect(Collectors.toUnmodifiableSet());
    // Client initialization installs the negotiated-session supplier; no client class on a server.
    private static BooleanSupplier availability = () -> true;

    private NewVehicleContent() { }

    public static void setClientAvailability(BooleanSupplier supplier) {
        availability = Objects.requireNonNull(supplier);
    }

    public static boolean allows(Identifier registry, Identifier entry) {
        boolean newContent = FRAMES.equals(registry) && NEW_FRAMES.contains(entry)
                || WHEELS.equals(registry) && NEW_WHEELS.contains(entry);
        return !newContent || availability.getAsBoolean();
    }

    public static boolean allowsPrefab(AutomobileData data, HolderLookup.Provider registries) {
        return allows(FRAMES, data.frame().identifier()) && allows(WHEELS, data.wheel().identifier())
                && registries.lookup(AutomobileFrame.REGISTRY).flatMap(r -> r.get(data.frame())).isPresent()
                && registries.lookup(AutomobileWheel.REGISTRY).flatMap(r -> r.get(data.wheel())).isPresent()
                && registries.lookup(AutomobileEngine.REGISTRY).flatMap(r -> r.get(data.engine())).isPresent();
    }
}
