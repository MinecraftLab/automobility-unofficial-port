package io.github.foundationgames.automobility.fabric.vehicle;

import io.github.foundationgames.automobility.api.vehicle.*;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.ArrayList;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.loader.api.FabricLoader;

/** Only new Core consumes the optional server provider; old Core never links the addon implementation. */
public final class FabricVehicleQuiescenceBootstrap implements ModInitializer {
    @Override public void onInitialize() {
        var providers = new ArrayList<VehicleQuiescenceProvider>();
        boolean failed = false;
        try {
            for (var container : FabricLoader.getInstance().getEntrypointContainers(
                    VehicleQuiescenceProvider.ENTRYPOINT, VehicleQuiescenceProvider.class)) {
                try {
                    var provider = container.getEntrypoint();
                    if (!provider.id().startsWith(container.getProvider().getMetadata().getId() + ':')) failed = true;
                    else providers.add(provider);
                } catch (RuntimeException | LinkageError error) { failed = true; }
            }
        } catch (RuntimeException | LinkageError error) { failed = true; }
        VehicleQuiescenceProviders.install(providers, failed);
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof AutomobileEntity car && car.level() == level) VehicleQuiescenceProviders.entityUnload(car);
        });
    }
}
