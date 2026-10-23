package io.github.foundationgames.automobility.automobile.render.obj;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.lang.reflect.Method;
import java.util.function.Supplier;
import java.util.HashMap;
import java.util.Map;

/** No optional Iris ABI linkage. Unknown/custom draw contexts retain the original stream. */
final class ObjDrawCompatibility {
    private static volatile boolean vanillaShaderResources;
    private ObjDrawCompatibility() { }

    static boolean permits(RenderType actualType) {
        return standardCutout(actualType) && vanillaShaderResources && IrisHolder.ACCESS.inactive();
    }

    static boolean standardCutout(RenderType actualType) {
        try {
            return actualType != null && actualType.pipeline() == RenderPipelines.ENTITY_CUTOUT
                    && !actualType.isOutline() && !actualType.hasBlending() && !actualType.sortOnUpload();
        } catch (RuntimeException | LinkageError rejected) { return false; }
    }

    /** Resolve effective shader provenance once per reload, never in the frame loop. */
    static void onReload(ResourceManager resources) {
        vanillaShaderResources = false;
        try {
            var sources = new HashMap<Identifier, String>();
            resources.listResources("shaders", id -> id.getNamespace().equals("minecraft")
                    && (id.getPath().endsWith(".vsh") || id.getPath().endsWith(".fsh")
                    || id.getPath().endsWith(".glsl") || id.getPath().endsWith(".json")))
                    .forEach((id, resource) -> sources.put(id, resource.sourcePackId()));
            vanillaShaderResources = vanillaSources(sources);
        } catch (RuntimeException | LinkageError rejected) { /* Unknown resource context: old stream. */ }
    }

    static boolean vanillaSources(Map<Identifier, String> sources) {
        return sources.containsKey(Identifier.fromNamespaceAndPath("minecraft", "shaders/core/entity.vsh"))
                && sources.containsKey(Identifier.fromNamespaceAndPath("minecraft", "shaders/core/entity.fsh"))
                && sources.values().stream().allMatch("vanilla"::equals);
    }

    private static final class IrisHolder {
        private static final IrisAccess ACCESS = IrisAccess.resolve();
    }

    record IrisAccess(boolean absent, Object api, Method inUse, Method shadow) {
        static IrisAccess resolve() {
            try {
                return resolve(FabricLoader.getInstance().isModLoaded("iris"), () -> {
                    try { return Class.forName("net.irisshaders.iris.api.v0.IrisApi", false,
                            ObjDrawCompatibility.class.getClassLoader()); }
                    catch (ClassNotFoundException missing) { throw new IllegalStateException(missing); }
                });
            } catch (RuntimeException | LinkageError rejected) {
                return new IrisAccess(false, null, null, null);
            }
        }

        static IrisAccess resolve(boolean installed, Supplier<Class<?>> apiType) {
            if (!installed) return new IrisAccess(true, null, null, null);
            try {
                Class<?> type = apiType.get();
                return new IrisAccess(false, type.getMethod("getInstance").invoke(null),
                        type.getMethod("isShaderPackInUse"), type.getMethod("isRenderingShadowPass"));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError rejected) {
                return new IrisAccess(false, null, null, null);
            }
        }

        boolean inactive() {
            if (absent) return true;
            if (api == null || inUse == null || shadow == null) return false;
            try { return !(boolean) inUse.invoke(api) && !(boolean) shadow.invoke(api); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError rejected) { return false; }
        }
    }
}
