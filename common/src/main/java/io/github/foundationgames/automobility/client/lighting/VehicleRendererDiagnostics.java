package io.github.foundationgames.automobility.client.lighting;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;

/** On-demand support snapshot. Actual GPU backend, never an inference from saved options. */
public final class VehicleRendererDiagnostics {
    public enum Backend { OPEN_GL, VULKAN, UNKNOWN }
    public record Snapshot(String minecraft, String core, String sodium, String backend, String gpu, String vendor, String driver, String iris, String shaderPack, String shaderPackName,
                           VehicleRenderPassPolicy.Pass pass, ComplementaryTemporalProjection.Status temporal,
                           DirectRendererSupport.Snapshot direct) {}
    private VehicleRendererDiagnostics() {}
    public static Backend backend(String name) {
        return "OpenGL".equals(name) ? Backend.OPEN_GL : "Vulkan".equals(name) ? Backend.VULKAN : Backend.UNKNOWN;
    }
    public static Snapshot capture() {
        String backend="unknown", gpu="unknown", vendor="unknown", driver="unknown";
        try {
            var device=RenderSystem.tryGetDevice();
            if(device!=null) {
                var info=device.getDeviceInfo();
                backend=info.backendName();gpu=info.name();vendor=info.vendorName();driver=info.driverInfo();
            }
        } catch(RuntimeException | LinkageError ignored) { }
        String iris=FabricLoader.getInstance().getModContainer("iris")
                .map(mod->mod.getMetadata().getVersion().getFriendlyString()).orElse("");
        return new Snapshot(version("minecraft"),version("automobility"),version("sodium"),backend,gpu,vendor,driver,iris,ComplementaryTemporalProjection.shaderPackState(),ComplementaryTemporalProjection.shaderPackName(),
                ComplementaryTemporalProjection.passState(),ComplementaryTemporalProjection.status(),DirectRendererSupport.snapshot());
    }
    private static String version(String id) {
        return FabricLoader.getInstance().getModContainer(id).map(mod->mod.getMetadata().getVersion().getFriendlyString()).orElse("");
    }
}
