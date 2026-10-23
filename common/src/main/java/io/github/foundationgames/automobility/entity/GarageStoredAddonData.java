package io.github.foundationgames.automobility.entity;

import java.util.Set;

/** Raw root state needs an explicitly qualified transfer, even with a vanilla engine. */
public final class GarageStoredAddonData {
    private GarageStoredAddonData() {}

    public static boolean requiresQualifiedTransfer(Set<String> rootKeys) {
        return rootKeys.stream().anyMatch(key -> key.startsWith("AutoFuel") || key.equals("AutoTrip")
                || key.startsWith("powerworks:"));
    }
}
