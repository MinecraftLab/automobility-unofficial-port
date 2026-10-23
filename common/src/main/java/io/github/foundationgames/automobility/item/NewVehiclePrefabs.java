package io.github.foundationgames.automobility.item;

import io.github.foundationgames.automobility.automobile.AutomobileData;
import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import io.github.foundationgames.automobility.automobile.AutomobileWheel;
import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.Objects;

/** Common-side definitions: no renderer or optional addon is loaded here. */
public final class NewVehiclePrefabs {
    private static final List<Definition> DEFINITIONS = List.of(
            definition("defender_90", "defender_terrain"),
            definition("vw_t3", "t3_steel"),
            definition("mx5_na", "mx5_alloy"));

    private NewVehiclePrefabs() {}

    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    public static void register() {
        for (Definition definition : DEFINITIONS) {
            AutomobileItem.addPrefabs(AutomobileData.prefab(definition.frame(),
                    ResourceKey.create(AutomobileFrame.REGISTRY, definition.frame()),
                    ResourceKey.create(AutomobileWheel.REGISTRY, definition.wheel()),
                    AutomobileEngine.IRON, VehicleLightMode.AUTO));
        }
    }

    private static Definition definition(String frame, String wheel) {
        return new Definition(Identifier.fromNamespaceAndPath("automobility", frame),
                Identifier.fromNamespaceAndPath("automobility", wheel));
    }

    public record Definition(Identifier frame, Identifier wheel) {
        public Definition {
            Objects.requireNonNull(frame);
            Objects.requireNonNull(wheel);
        }
    }
}
