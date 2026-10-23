package io.github.foundationgames.automobility.client.lighting.direct;

import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

/** Isolates the exact private Iris ABI. No dependency is added to the production artifact. */
final class DirectPinnedIris {
    private Object api, captured;
    private Method inUse, shadow, pack, manager, pipeline, version, textures;
    private Method entity, blockEntity, item, setEntity, setBlockEntity, setItem;
    private Field renderingBEs, renderingLevel;
    private Field skipExtension;
    private VertexFormat extendedEntity;

    static DirectPinnedIris resolve() {
        var result = new DirectPinnedIris();
        try {
            var installed = FabricLoader.getInstance().getModContainer("iris");
            if (installed.isEmpty() || !"1.11.4+mc26.2".equals(installed.get().getMetadata().getVersion().getFriendlyString())) return result;
            var loader = DirectPinnedIris.class.getClassLoader();
            var apiType = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, loader);
            result.api = apiType.getMethod("getInstance").invoke(null);
            result.inUse = apiType.getMethod("isShaderPackInUse");
            result.shadow = apiType.getMethod("isRenderingShadowPass");
            var iris = Class.forName("net.irisshaders.iris.Iris", false, loader);
            result.pack = iris.getMethod("getCurrentPackName");
            result.manager = iris.getMethod("getPipelineManager");
            var pm = result.manager.getReturnType();
            result.pipeline = pm.getMethod("getPipelineNullable");
            result.version = pm.getMethod("getVersionCounterForSodiumShaderReload");
            var state = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState", false, loader);
            result.captured = state.getField("INSTANCE").get(null);
            result.entity = state.getMethod("getCurrentRenderedEntity");
            result.blockEntity = state.getMethod("getCurrentRenderedBlockEntity");
            result.item = state.getMethod("getCurrentRenderedItem");
            result.setEntity = state.getMethod("setCurrentEntity", int.class);
            result.setBlockEntity = state.getMethod("setCurrentBlockEntity", int.class);
            result.setItem = state.getMethod("setCurrentRenderedItem", int.class);
            result.textures = state.getMethod("getTextureReloadCount");
            var immediate = Class.forName("net.irisshaders.iris.vertices.ImmediateState", false, loader);
            result.renderingBEs = immediate.getField("isRenderingBEs");
            result.renderingLevel = immediate.getField("isRenderingLevel");
            result.skipExtension = immediate.getField("skipExtension");
            var formats = Class.forName("net.irisshaders.iris.vertices.IrisVertexFormats", false, loader);
            result.extendedEntity = (VertexFormat) formats.getField("ENTITY").get(null);
            if (result.extendedEntity.getVertexSize() != 56 || DefaultVertexFormat.ENTITY.getVertexSize() != 36)
                result.api = null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError rejected) {
            result.api = null;
        }
        return result;
    }

    boolean available() { return api != null && captured != null && textures != null && renderingLevel != null; }

    Snapshot capture() {
        if (!available()) return null;
        try {
            if (!(boolean) inUse.invoke(api) || (boolean) shadow.invoke(api) || !renderingLevel.getBoolean(null)
                    || skipExtension == null || !(skipExtension.get(null) instanceof ThreadLocal<?> extension)
                    || !Boolean.FALSE.equals(extension.get())) return null;
            Object selected = pack.invoke(null);
            if (!"ComplementaryUnbound_r5.9 + EuphoriaPatches_1.10.0".equals(selected)) return null;
            Object pm = manager.invoke(null), active = pipeline.invoke(pm);
            if (active == null) return null;
            return new Snapshot(pm, active, (int) version.invoke(pm), (int) textures.invoke(captured), ids());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError rejected) { return null; }
    }

    boolean supportsFormat(VertexFormat format) {
        return format == extendedEntity || format == DefaultVertexFormat.ENTITY;
    }

    boolean isExtendedEntity(VertexFormat format) { return format == extendedEntity && format.getVertexSize() == 56; }
    boolean renderingLevel()throws IllegalAccessException{return renderingLevel.getBoolean(null);}
    void setRenderingLevel(boolean value)throws IllegalAccessException{renderingLevel.setBoolean(null,value);}

    Ids ids() {
        try { return new Ids((int) entity.invoke(captured), (int) blockEntity.invoke(captured), (int) item.invoke(captured), renderingBEs.getBoolean(null)); }
        catch (ReflectiveOperationException rejected) { throw new IllegalStateException("Pinned Iris context read failed", rejected); }
    }

    void setIds(Ids ids) {
        try { setEntity.invoke(captured, ids.entity()); setBlockEntity.invoke(captured, ids.blockEntity()); setItem.invoke(captured, ids.item()); renderingBEs.setBoolean(null, ids.renderingBEs()); }
        catch (ReflectiveOperationException rejected) { throw new IllegalStateException("Pinned Iris context restore failed", rejected); }
    }

    record Ids(int entity, int blockEntity, int item, boolean renderingBEs) { }
    record Snapshot(Object manager, Object pipeline, int version, int textures, Ids ids) {
        boolean sameEpoch(Snapshot other) {
            return manager == other.manager && pipeline == other.pipeline && version == other.version && textures == other.textures;
        }
    }
}
