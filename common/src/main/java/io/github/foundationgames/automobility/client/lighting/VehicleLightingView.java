package io.github.foundationgames.automobility.client.lighting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.multiplayer.ClientLevel;
import io.github.foundationgames.automobility.client.vehicle.VehicleClientEffects;
import io.github.foundationgames.automobility.client.vehicle.VehicleLightQuality;
import io.github.foundationgames.automobility.client.vehicle.VehicleCustomGraphicsSettings;

import java.lang.ref.WeakReference;

/** Captures the primary camera, never a shader shadow/deferred callback's view. */
public record VehicleLightingView(Vec3 position, Frustum frustum) {
    private static WeakReference<ClientLevel> qualityLevel = new WeakReference<>(null);
    private static long qualityTick = Long.MIN_VALUE;
    private static VehicleLightQuality quality = VehicleLightQuality.BALANCED;
    private static VehicleCustomGraphicsSettings graphics = VehicleCustomGraphicsSettings.forPreset(quality);

    /** Both renderers share one applied preset per world tick, even during rapid saves. */
    public static VehicleLightQuality quality(Minecraft client) {
        var level = client.level;
        if (level == null) return VehicleClientEffects.preferences().quality();
        if (qualityLevel.get() != level || qualityTick != level.getGameTime()) {
            qualityLevel = new WeakReference<>(level); qualityTick = level.getGameTime();
            var applied = VehicleClientEffects.preferences();
            quality = applied.quality();
            graphics = applied.graphics();
        }
        return quality;
    }

    public static VehicleCustomGraphicsSettings graphics(Minecraft client) {
        quality(client);
        return client.level == null ? VehicleClientEffects.preferences().graphics() : graphics;
    }

    public static VehicleLightQuality spillQuality(Minecraft client) { return graphics(client).spill().quality(); }

    /** Shared double-precision render clock, not a frozen automobile's interpolation clock. */
    public static double presentationTime(Minecraft client) {
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        return (double) client.level.getGameTime() + (Float.isFinite(partial) ? Math.max(0, Math.min(1, partial)) : 0);
    }
    public static VehicleLightingView capture(Minecraft client) {
        var camera = client.gameRenderer.mainCamera();
        Vec3 position = camera.position();
        return position != null && position.isFinite()
                ? new VehicleLightingView(position, camera.getCullFrustum()) : null;
    }

    public boolean visible(AABB bounds) { return frustum == null || frustum.isVisible(bounds); }

    /** Conservative volume relevance, not a claim of exact depth-visible illuminated area. */
    public double score(AABB bounds) {
        if (!visible(bounds)) return 0;
        double x = Math.max(bounds.minX, Math.min(bounds.maxX, position.x));
        double y = Math.max(bounds.minY, Math.min(bounds.maxY, position.y));
        double z = Math.max(bounds.minZ, Math.min(bounds.maxZ, position.z));
        return 1 / (16 + position.distanceToSqr(x, y, z));
    }

    public double pointScore(Vec3 point) {
        return (frustum == null || frustum.pointInFrustum(point.x, point.y, point.z) ? 1 : 0.05)
                / (16 + point.distanceToSqr(position));
    }
}
