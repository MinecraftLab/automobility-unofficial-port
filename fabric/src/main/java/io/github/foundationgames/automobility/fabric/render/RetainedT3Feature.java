package io.github.foundationgames.automobility.fabric.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.PrimitiveTopology;
import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.render.obj.BakedObj;
import io.github.foundationgames.automobility.automobile.render.obj.RetainedObjSubmission;
import io.github.foundationgames.automobility.automobile.render.obj.RetainedMeshBudget;
import io.github.foundationgames.automobility.automobile.render.obj.RetainedVertexBudget;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.FabricOrderedSubmitNodeCollector;
import net.fabricmc.fabric.api.client.rendering.v1.FeatureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhases;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;

import java.util.LinkedHashMap;
import java.util.List;

/** Opt-in, pinned Iris/Euphoria experiment. Deliberately not a generic renderer replacement. */
public final class RetainedT3Feature implements FeatureRenderer<RetainedT3Feature.Submit> {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("automobility.experimental.retainedT3", "true"));
    private static final FeatureRendererType<Submit> TYPE = FeatureRendererType.create("automobility:retained_t3");
    private static final LinkedHashMap<Key, Mesh> CACHE = new LinkedHashMap<>(16, .75f, true);
    private static final PinnedIrisContext IRIS = PinnedIrisContext.resolve();
    private static final int MAX_VARIANTS = 16;
    private static final long MAX_RESIDENT_BYTES = 32L * 1024 * 1024;
    private static final RetainedMeshBudget BUDGET = new RetainedMeshBudget(64L * 1024 * 1024);
    private static long residentBytes, retiredBytes, budgetRejects;
    private static final java.util.Map<FeatureFrameContext, java.util.List<RetainedMeshBudget.Reservation>> PREPARED = new java.util.IdentityHashMap<>();
    private static long submissions, draws, uploads, fallbacks;
    private static long shadowSubmissions, shadowDraws, shadowUploads;
    private static Object lastPipeline, lastDevice;
    private static int lastVersion = -1, lastTextures = -1;

    public static void initialize() {
        if (!ENABLED || !IRIS.available()) return;
        FeatureRendererRegistry.register(TYPE, RetainedT3Feature::new);
        RetainedObjSubmission.install(RetainedT3Feature::submit);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            RetainedObjSubmission.disable();
            beginFrame();
            clear();
            Automobility.LOG.info("PreparedLights final: {}", io.github.foundationgames.automobility.client.lighting.VehicleHeadlightRenderer.preparedCacheSnapshot());
            Automobility.LOG.info("ParkedRefresh final: {}", io.github.foundationgames.automobility.client.lighting.VehicleHeadlightRenderer.parkedRefreshSnapshot());
            Automobility.LOG.info("TemporalProjection final: {}", io.github.foundationgames.automobility.client.lighting.ComplementaryTemporalProjection.diagnosticSnapshot());
            Automobility.LOG.info("RetainedT3 final: submissions={} draws={} uploads={} fallbacks={} budgetRejects={} residentBytes={} retiredBytes={} liveBytes={} reservedBytes={} peakCommittedOrReservedBytes={}",
                    submissions, draws, uploads, fallbacks, budgetRejects, residentBytes, retiredBytes,
                    BUDGET.liveBytes(), BUDGET.reservedBytes(), BUDGET.peakBytes());
            Automobility.LOG.info("RetainedShadow final: submissions={} draws={} uploads={}", shadowSubmissions, shadowDraws, shadowUploads);
        });
        Automobility.LOG.info("Private RetainedT3 experiment armed; original renderer remains fallback");
    }

    /** Before extraction, never between prepare and execute. */
    public static void beginFrame() {
        if (!ENABLED) return;
        BUDGET.cancelPending();
        PREPARED.clear();
    }

    private static boolean submit(RetainedObjSubmission.Request request) {
        if (!(request.collector() instanceof FabricOrderedSubmitNodeCollector collector)
                || request.type().pipeline() != RenderPipelines.ENTITY_CUTOUT
                || request.type().isOutline() || request.type().hasBlending() || request.type().sortOnUpload()) return false;
        var captured = IRIS.capture();
        if (captured == null) return false;
        // The pinned extended ENTITY stride is well below this conservative bound.
        // Reserve before consuming the node so pressure uses the original renderer.
        long upperBytes = RetainedVertexBudget.upperBytes(request.bake().idVertex().length);
        var reservation = BUDGET.reserve(upperBytes);
        if (reservation == null) { budgetRejects++; return false; }
        try { collector.submitCustom(SubmitRenderPhases.SOLID, new Submit(request, captured, reservation)); }
        catch (RuntimeException error) { reservation.close(); throw error; }
        submissions++;
        if (captured.shadowPass()) shadowSubmissions++;
        return true;
    }

    public record Submit(RetainedObjSubmission.Request request, PinnedIrisContext.Snapshot iris,
                         RetainedMeshBudget.Reservation reservation) implements SubmitNode {
        @Override public FeatureRendererType<Submit> featureType() { return TYPE; }
    }

    @Override
    public void prepareGroup(FeatureFrameContext context, List<Submit> submits, boolean strictlyOrdered) {
        var owned = PREPARED.computeIfAbsent(context, unused -> new java.util.ArrayList<>());
        for (var submit : submits) owned.add(submit.reservation());
    }

    @Override public void finishExecute(FeatureFrameContext context) {
        var owned = PREPARED.remove(context);
        if (owned != null) owned.forEach(RetainedMeshBudget.Reservation::close);
    }

    @Override
    public void executeGroup(FeatureFrameContext context, int groupIndex, List<Submit> submits, boolean strictlyOrdered) {
        try { for (var submit : submits) draw(submit); }
        finally { submits.forEach(submit -> submit.reservation().close()); }
    }

    private static void draw(Submit submit) {
        try (var reservation = submit.reservation()) { drawReserved(submit); }
    }

    private static void drawReserved(Submit submit) {
        var request = submit.request();
        var previous = IRIS.ids();
        try {
            IRIS.setIds(submit.iris().ids());
            IRIS.requirePass(submit.iris().shadowPass());
            var now = IRIS.capture();
            if (now != null) submit.iris().requireSamePass(now);
            if (now == null || !now.sameEpoch(submit.iris())) {
                // A consumed node must not disappear after a reload/context rejection.
                legacyDraw(request, submit.reservation());
                return;
            }
            Object device = RenderSystem.getDevice();
            if (lastPipeline != now.pipeline() || lastDevice != device || lastVersion != now.version()
                    || lastTextures != now.textures()) {
                clear(); lastPipeline = now.pipeline(); lastDevice = device;
                lastVersion = now.version(); lastTextures = now.textures();
            }
            // Iris selects different actual shader pipelines/vertex semantics per pass.
            // Never borrow a main-pass upload for a shadow pass (or vice versa).
            var key = new Key(request.bake(), request.light(), request.overlay(), request.color(), submit.iris().ids(), now.shadowPass());
            var mesh = CACHE.get(key);
            if (mesh == null) {
                try {
                    mesh = upload(request, false, submit.reservation());
                } catch (RuntimeException rejected) {
                    Automobility.LOG.warn("RetainedT3 mesh rejected; drawing original stream", rejected);
                    legacyDraw(request, submit.reservation());
                    return;
                }
                CACHE.put(key, mesh); residentBytes += mesh.bytes(); uploads++;
                if (now.shadowPass()) shadowUploads++;
                while (CACHE.size() > MAX_VARIANTS || residentBytes > MAX_RESIDENT_BYTES) {
                    var first = CACHE.entrySet().iterator(); var old = first.next().getValue(); first.remove();
                    residentBytes -= old.bytes(); retire(old);
                }
            }
            var stack = RenderSystem.getModelViewStack();
            stack.pushMatrix();
            try {
                // Both native prepare and Iris setup must observe exactly this same matrix.
                // Shadow extraction already placed shadowMV in request.pose; the current
                // global stack is identity there. Do not multiply shadowMV a second time.
                // CapturedRenderingState.gbufferModelView stays camera-only and untouched.
                stack.mul(request.pose().pose());
                var prepared = request.type().prepare();
                var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                prepared.drawFromBuffer(mesh.vertices(), sequential.getBuffer(mesh.indexCount()),
                        sequential.type(), 0, 0, mesh.indexCount());
            } finally { stack.popMatrix(); }
            if (now.shadowPass() && ++shadowDraws == 1) {
                Automobility.LOG.info("RetainedShadow actual Iris draw: format={} vertices={} indices={}",
                        mesh.format(), mesh.vertexCount(), mesh.indexCount());
            }
            if (++draws == 1) {
                Automobility.LOG.info("RetainedT3 actual Iris draw: format={} vertices={} indices={}",
                        mesh.format(), mesh.vertexCount(), mesh.indexCount());
            }
        } finally {
            try { IRIS.setIds(previous); }
            finally { submit.reservation().close(); }
        }
    }

    private static Mesh upload(RetainedObjSubmission.Request request, boolean transformed,
                               RetainedMeshBudget.Reservation reservation) {
        try (var bytes = new ByteBufferBuilder(4 * 1024 * 1024)) {
            // Let the real Iris-patched builder generate ENTITY extension/tangent/midUV.
            var builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.ENTITY);
            request.emitter().render(transformed ? request.pose() : new PoseStack().last(), builder);
            try (MeshData data = builder.buildOrThrow()) {
                var state = data.drawState();
                int size = data.vertexBuffer().remaining();
                if (!IRIS.supportsFormat(state.format())
                        || state.vertexCount() != RetainedVertexBudget.vertices(request.bake().idVertex().length)
                        || !reservation.permits(size)) throw new IllegalStateException("Pinned ENTITY layout/count exceeded reservation");
                var vertices = RenderSystem.getDevice().createBuffer(() -> "Automobility retained T3 body",
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, data.vertexBuffer());
                reservation.allocated(size);
                return new Mesh(vertices, state.indexCount(), state.vertexCount(), state.format().toString(), size);
            }
        }
    }

    private static void legacyDraw(RetainedObjSubmission.Request request, RetainedMeshBudget.Reservation reservation) {
        fallbacks++;
        var mesh = upload(request, true, reservation);
        try {
            var prepared = request.type().prepare();
            var sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            prepared.drawFromBuffer(mesh.vertices(), sequential.getBuffer(mesh.indexCount()),
                    sequential.type(), 0, 0, mesh.indexCount());
        } finally { retire(mesh); }
    }

    private static void clear() { CACHE.values().forEach(RetainedT3Feature::retire); CACHE.clear(); residentBytes = 0; }
    private static void retire(Mesh mesh) {
        retiredBytes += mesh.bytes();
        RenderSystem.queueFencedTask(() -> {
            try { mesh.vertices().close(); }
            finally { retiredBytes -= mesh.bytes(); BUDGET.freed(mesh.bytes()); }
        });
    }
    private record Mesh(GpuBuffer vertices, int indexCount, int vertexCount, String format, int bytes) { }
    private record Key(BakedObj bake, int light, int overlay, int color, PinnedIrisContext.Ids ids, boolean shadowPass) { }
}
