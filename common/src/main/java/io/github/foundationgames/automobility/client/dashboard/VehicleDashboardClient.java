package io.github.foundationgames.automobility.client.dashboard;

import io.github.foundationgames.automobility.api.dashboard.DashboardProvider;
import io.github.foundationgames.automobility.api.dashboard.DashboardProviders;
import io.github.foundationgames.automobility.api.dashboard.DashboardSnapshot;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Client-only optional discovery and captured state of the exact car being ridden, driver or passenger. */
public final class VehicleDashboardClient {
    private static final System.Logger LOGGER = System.getLogger("automobility/dashboard");
    private static final DashboardProviders PROVIDERS = new DashboardProviders();
    private static boolean initialized;
    private static Object world;
    private static WeakReference<AutomobileEntity> vehicle = new WeakReference<>(null);
    private static DashboardSnapshot snapshot;
    private record LoadedEntry(WeakReference<AutomobileEntity> car, Object world, long tick, DashboardSnapshot snapshot) {}
    private static final Map<UUID, LoadedEntry> NEARBY = new LinkedHashMap<>(32, .75f, true);
    private static Object nearbyWorld;


    private VehicleDashboardClient() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        // All addons have initialized before discovery/tick registration. No server-side entrypoint reads this API.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            discover();
            ClientTickEvents.END_CLIENT_TICK.register(VehicleDashboardClient::capture);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { clear(); NEARBY.clear(); nearbyWorld = null; });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("automobility", "vehicle_radio_dashboard"),
                (graphics, tickDelta) -> VehicleDashboardHud.renderStandalone(graphics));
    }

    private static void discover() {
        for (var container : FabricLoader.getInstance().getEntrypointContainers(DashboardProviders.ENTRYPOINT, DashboardProvider.class)) {
            try { PROVIDERS.register(container.getEntrypoint()); }
            catch (RuntimeException | LinkageError failure) {
                LOGGER.log(System.Logger.Level.WARNING, "Optional dashboard provider unavailable: "
                        + container.getProvider().getMetadata().getId(), failure);
            }
        }
    }

    private static void capture(Minecraft client) {
        if (nearbyWorld != client.level) { NEARBY.clear(); nearbyWorld = client.level; }
        if (client.level == null || client.player == null || !(client.player.getVehicle() instanceof AutomobileEntity car)
                || car.isRemoved() || client.level.getEntity(car.getId()) != car) { clear(); return; }
        // Never keep the previous vehicle's frame while a new provider/key is being resolved.
        snapshot = null;
        world = client.level;
        vehicle = new WeakReference<>(car);
        snapshot = snapshotForLoadedCar(car).orElse(null);
    }

    /** Read-only hook for the future combined instruments renderer; no player controls or playback work. */
    public static Optional<DashboardSnapshot> snapshotFor(AutomobileEntity car) {
        var client = Minecraft.getInstance();
        if (car == null || snapshot == null || client.level == null || client.level != world
                || vehicle.get() != car || car.isRemoved() || client.level.getEntity(car.getId()) != car
                || !snapshot.key().vehicleId().equals(car.getUUID()) || snapshot.key().entityId() != car.getId()
                || !snapshot.key().dimensionId().equals(client.level.dimension().identifier().toString())) return Optional.empty();
        return Optional.of(snapshot);
    }

    /** One captured read per loaded car/game tick, capped at128 identities and24blocks. No audio actions. */
    public static Optional<DashboardSnapshot> snapshotForLoadedCar(AutomobileEntity car) {
        var client = Minecraft.getInstance();
        if (car == null || car.isRemoved() || client.level == null || client.player == null
                || car.level() != client.level || client.level.getEntity(car.getId()) != car || client.player.distanceToSqr(car) > 24 * 24) return Optional.empty();
        if (nearbyWorld != client.level) { NEARBY.clear(); nearbyWorld = client.level; }
        long tick = client.level.getGameTime();
        var cached = NEARBY.get(car.getUUID());
        if (cached != null && cached.car().get() == car && cached.world() == client.level && cached.tick() == tick)
            return Optional.ofNullable(cached.snapshot());
        String dimension = client.level.dimension().identifier().toString();
        var key = PROVIDERS.resolveKey(dimension, car.getUUID(), car.getId());
        var observed = key.flatMap(PROVIDERS::snapshot).orElse(null);
        NEARBY.put(car.getUUID(), new LoadedEntry(new WeakReference<>(car), client.level, tick, observed));
        while (NEARBY.size() > 128) NEARBY.remove(NEARBY.keySet().iterator().next());
        return Optional.ofNullable(observed);
    }

    public static List<String> providerIds() { return PROVIDERS.ids(); }

    private static void clear() {
        snapshot = null;
        world = null;
        vehicle = new WeakReference<>(null);
    }
}
