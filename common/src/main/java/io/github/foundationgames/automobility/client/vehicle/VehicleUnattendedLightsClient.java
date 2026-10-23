package io.github.foundationgames.automobility.client.vehicle;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.platform.Platform;
import io.github.foundationgames.automobility.util.network.ClientPackets;
import io.github.foundationgames.automobility.util.network.VehicleUnattendedLightPackets;
import net.minecraft.client.Minecraft;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

/** Bounded capability acknowledgements tied to actual client connection, world and vehicle objects. */
public final class VehicleUnattendedLightsClient {
    private static final WeakHashMap<AutomobileEntity,VehicleUnattendedLightPackets.State> STATES = new WeakHashMap<>();
    private static WeakReference<Object> connection = new WeakReference<>(null), level = new WeakReference<>(null);
    private VehicleUnattendedLightsClient() {}
    private static void bind(Minecraft client) {
        if (connection.get() != client.getConnection() || level.get() != client.level) {
            STATES.clear(); connection = new WeakReference<>(client.getConnection()); level = new WeakReference<>(client.level);
        }
    }
    public static VehicleUnattendedLightPackets.State state(AutomobileEntity car) {
        bind(Minecraft.getInstance()); return STATES.get(car);
    }
    public static boolean request(AutomobileEntity car, Integer seconds) {
        var client = Minecraft.getInstance(); bind(client);
        if (!VehicleClientControls.isDriver(client, car) || !car.getVehicleAccess().mayConfigure(client.player.getUUID())) return false;
        if (seconds != null && state(car) == null) return false;
        var request = new VehicleUnattendedLightPackets.Request(car.getId(), car.getUUID(), seconds != null, seconds == null ? 0 : seconds);
        Platform.get().clientSendPacket(Automobility.rl("vehicle_unattended_lights"), VehicleUnattendedLightPackets.encodeRequest(request));
        return true;
    }
    public static void init() {
        ClientPackets.registerReceiver(Automobility.rl("vehicle_unattended_lights_state"), (client, buf) -> {
            final VehicleUnattendedLightPackets.State state;
            try { state = VehicleUnattendedLightPackets.decodeState(buf); } catch (RuntimeException ignored) { return; }
            var receivedConnection = client.getConnection(); var receivedLevel = client.level;
            if (receivedConnection == null || receivedLevel == null) return;
            client.execute(() -> {
                if (client.getConnection() != receivedConnection || client.level != receivedLevel
                        || !(receivedLevel.getEntity(state.entity()) instanceof AutomobileEntity car)
                        || !car.getUUID().equals(state.identity())) return;
                bind(client);
                STATES.remove(car); STATES.put(car, state);
                while (STATES.size() > 32) STATES.remove(STATES.keySet().iterator().next());
            });
        });
    }
}
