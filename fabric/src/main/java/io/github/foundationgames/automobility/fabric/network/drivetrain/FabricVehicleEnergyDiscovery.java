package io.github.foundationgames.automobility.fabric.network.drivetrain;

import io.github.foundationgames.automobility.api.drivetrain.energy.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;

/** Only a new Core consumes this custom entrypoint; old Core never links provider classes. */
public final class FabricVehicleEnergyDiscovery {
    private FabricVehicleEnergyDiscovery() { }
    public static void install() {
        var loader = FabricLoader.getInstance(); var mods = new HashSet<String>(); var providers = new ArrayList<VehicleEnergyProvider>(); boolean failed = false;
        for (var mod : loader.getAllMods()) mods.add(mod.getMetadata().getId());
        try {
            for (var container : loader.getEntrypointContainers(VehicleEnergyProvider.ENTRYPOINT, VehicleEnergyProvider.class)) {
                try {
                    var provider = container.getEntrypoint(); String module = container.getProvider().getMetadata().getId(); String id = provider.id();
                    boolean permitted = id.startsWith(module + ":")
                            || module.equals("pumps_and_pistons_automobility") && id.startsWith("pumps_and_pistons:");
                    if (!permitted) failed = true; else providers.add(provider);
                } catch (RuntimeException | LinkageError error) { failed = true; }
            }
        } catch (RuntimeException | LinkageError error) { failed = true; }
        VehicleEnergyProviders.install(providers, mods, failed);
    }
}
