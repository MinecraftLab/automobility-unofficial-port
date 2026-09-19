package io.github.foundationgames.automobility.qa;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read-only observer for a real player's driving test. This separate client-only
 * source set never belongs in a production release or the server test fixture.
 * Samples demonstrate camera interpolation; they do not claim manual acceptance.
 */
public final class ClientDrivingQa implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ClientDrivingQa");
    private static final int MAX_TURN_SAMPLES = 120;
    private static final float TURN_EPSILON = 0.02f;

    private int observedTicks;
    private int skippedNoDriver;
    private int skippedNotTurning;
    private int turnSamples;
    private int interpolatedSamples;
    private int flatViewSamples;
    private int nonLinearSamples;
    private boolean complete;

    @Override
    public void onInitializeClient() {
        RemoteInterpolationQa.initialize();
        LOGGER.info("AUTO263_DRIVER_QA_READY fixture={} minecraft={} core={} momentum={} ria={} maxTurnSamples={} passive=true",
                version("automobility_client_driving_qa"), version("minecraft"),
                version("automobility"), version("momentum"), version("riautomobility"), MAX_TURN_SAMPLES);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (complete) return;
            observedTicks++;
            var player = client.player;
            if (player == null || !(player.getVehicle() instanceof AutomobileEntity auto)
                    || !auto.isDriving(player)) {
                skippedNoDriver++;
                periodicSummary();
                return;
            }

            float vehicleDelta = Mth.wrapDegrees(auto.getYRot() - auto.yRotO);
            if (Math.abs(vehicleDelta) < TURN_EPSILON) {
                skippedNotTurning++;
                periodicSummary();
                return;
            }

            float view0 = player.getViewYRot(0f);
            float viewHalf = player.getViewYRot(0.5f);
            float view1 = player.getViewYRot(1f);
            float viewSpan = Mth.wrapDegrees(view1 - view0);
            float midpointError = Mth.wrapDegrees(viewHalf - (view0 + viewSpan * 0.5f));
            boolean flatView = Math.abs(viewSpan) < TURN_EPSILON;
            boolean linear = Math.abs(midpointError) < 0.001f;
            if (flatView) flatViewSamples++;
            else if (linear) interpolatedSamples++;
            if (!linear) nonLinearSamples++;
            turnSamples++;

            LOGGER.info("AUTO263_DRIVER_QA_SAMPLE sample={} tick={} vehicle={} localAuthority={} steering={} vehicleYawOld={} vehicleYaw={} vehicleDelta={} playerYawOld={} playerYaw={} headYawOld={} headYaw={} view0={} viewHalf={} view1={} viewSpan={} midpointError={} flatView={} interpolated={}",
                    turnSamples, player.tickCount, auto.getId(), auto.isLocalInstanceAuthoritative(),
                    auto.input.steering, auto.yRotO, auto.getYRot(), vehicleDelta,
                    player.yRotO, player.getYRot(), player.yHeadRotO, player.yHeadRot,
                    view0, viewHalf, view1, viewSpan, midpointError, flatView, !flatView && linear);

            if (turnSamples >= MAX_TURN_SAMPLES) {
                complete = true;
                summary("sample-cap");
            } else {
                periodicSummary();
            }
        });
    }

    private void periodicSummary() {
        if (observedTicks % 200 == 0) summary("progress");
    }

    private void summary(String reason) {
        LOGGER.info("AUTO263_DRIVER_QA_SUMMARY reason={} observedTicks={} turnSamples={} interpolated={} flatView={} nonLinear={} skippedNoDriver={} skippedNotTurning={} complete={} manualAcceptance=NOT_PROVEN",
                reason, observedTicks, turnSamples, interpolatedSamples, flatViewSamples,
                nonLinearSamples, skippedNoDriver, skippedNotTurning, complete);
    }

    private static String version(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("absent");
    }
}
