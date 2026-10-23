package io.github.foundationgames.automobility.client.lighting;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;
import io.github.foundationgames.automobility.automobile.light.VehicleSignalMode;
import io.github.foundationgames.automobility.client.vehicle.VehicleLightQuality;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.entity.render.AutomobileEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.WeakHashMap;

/**
 * Short coloured illumination on existing opaque block-model surfaces. No light-engine mutation,
 * transparent-surface reflection, entity shadow or shader GI is implied. Admission uses the shared lighting policy.
 */
public final class VehicleSmallLampRenderer {
    private static final double SURFACE_OFFSET = 0.0015;
    private static final float MIN_INTENSITY = 1.0f / 255;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<AutomobileEntity, SurfaceCache> CACHES = new WeakHashMap<>();
    private static final Set<Integer> SELECTED = new HashSet<>();
    private static final VehicleLightingPolicy.Scheduler SCHEDULER = new VehicleLightingPolicy.Scheduler();
    private static final Map<Integer, VehicleSmallLampLighting.Budget> QUOTAS = new HashMap<>();
    private static final LinkedHashMap<Integer, Snapshot> SNAPSHOTS = new LinkedHashMap<>();
    // Separate small cache; never mutates the existing headlight cache/counters.
    private static final HeadlightGeometryCache<GeometryKey, QuadGeometry> GEOMETRY =
            new HeadlightGeometryCache<>(1024, 10240);
    private static WeakReference<ClientLevel> selectedLevel = new WeakReference<>(null);
    private static Object selectedModels;
    private static VehicleLightQuality selectedQuality;
    private static long selectedTick = Long.MIN_VALUE;
    private static final GlobalWork GLOBAL = new GlobalWork();

    private VehicleSmallLampRenderer() {}

    /** Actual most recent submission diagnostics, or null before this car has submitted. */
    public static Snapshot snapshot(int entityId) { return SNAPSHOTS.get(entityId); }

    /** Absolute per-tick fleet counters, including rapid quality changes within the same tick. */
    public static GlobalSnapshot globalSnapshot() {
        return new GlobalSnapshot(GLOBAL.tick, GLOBAL.candidateVisits, GLOBAL.blocks, GLOBAL.modelQuads,
                GLOBAL.testedPatches, GLOBAL.patches, GLOBAL.rays);
    }

    public record GlobalSnapshot(long tick, int candidateVisits, int blocks, int modelQuads,
                                 int testedPatches, int patches, int rays) {}

    public record Snapshot(VehicleLightQuality quality, long tick, int entityId, int selectedCars,
                           int candidateVisits, int candidates, int blocks, int modelQuads, int testedPatches,
                           int patches, int rays, int drawnPatches, int activeWhite, int activeRed, int activeAmber,
                           VehicleSignalMode signalMode, boolean signalLit, List<Integer> activeColors,
                           HeadlightGeometryCache.Snapshot geometryCache, int activeBrakeRed, int activeReverseWhite,
                           boolean brakeLit, boolean reverseLit) {
        public Snapshot { activeColors = List.copyOf(activeColors); }
    }

    public static VehicleSmallLampLighting.Budget budget(VehicleLightQuality quality) {
        return VehicleSmallLampLighting.budget(quality);
    }

    /** Potential includes the signal's dark half, so frustum bounds never blink. */
    public static boolean hasPotentialLight(AutomobileEntity car) {
        return budget(VehicleLightingView.spillQuality(Minecraft.getInstance())).maxCars() > 0
                && !car.getFrame().isEmpty() && hasLayoutPotential(car, VehicleLampSnapshot.capture(car));
    }

    private static boolean hasLayoutPotential(AutomobileEntity car, VehicleLampSnapshot lamps) {
        if (car.getFrame().isEmpty() || !lamps.hasPotentialSmallLight()) return false;
        var primary = VehicleHeadlightLayout.forFrame(car.getFrame());
        var markers = VehicleMarkerLightLayout.forFrame(car.getFrame());
        var front = primary.faces().isEmpty() ? markers.frontFaces() : primary.faces();
        var popup = car.hasOpeningHood() ? car.getMx5VisualPose(Minecraft.getInstance().getDeltaTracker()
                .getGameTimeDeltaPartialTick(!car.level().tickRateManager().isEntityFrozen(car))) : null;
        return VehicleSmallLampLighting.hasEnabledFaces(front, markers.rearFaces(),
                VehicleTurnSignalLayout.forFrame(car.getFrame()), VehicleReverseLightLayout.forFrame(car.getFrame()).rearFaces(),
                lamps, popup);
    }

    public static AABB cullingBounds(AutomobileEntity car, float tickDelta, AABB headlightBounds) {
        VehicleLightQuality quality = VehicleLightingView.spillQuality(Minecraft.getInstance());
        if (budget(quality).maxCars() == 0 || car.getFrame().isEmpty()) return headlightBounds;
        VehicleLampSnapshot lamps = VehicleLampSnapshot.capture(car);
        if (!lamps.hasPotentialSmallLight()) return headlightBounds;
        var state = AutomobileEntityRenderer.headlightCullingState(car, tickDelta);
        if (state == null) return headlightBounds.minmax(car.getBoundingBox().inflate(budget(quality).radius() + 4));
        return VehicleSmallLampLighting.enclose(headlightBounds, sources(state, lamps, quality));
    }

    /** Read-only source geometry with the same chassis pose and captured mode/phase as the lens submission. */
    public static List<VehicleSmallLampLighting.Light> sources(AutomobileEntityRenderer.State state,
                                                              VehicleLampSnapshot lamps, VehicleLightQuality quality) {
        var car = state.automobile;
        if (car == null || car.getFrame().isEmpty() || budget(quality).maxCars() == 0) return List.of();
        var primary = VehicleHeadlightLayout.forFrame(car.getFrame());
        var markers = VehicleMarkerLightLayout.forFrame(car.getFrame());
        var signals = VehicleTurnSignalLayout.forFrame(car.getFrame());
        var reverse = VehicleReverseLightLayout.forFrame(car.getFrame());
        var front = primary.faces().isEmpty() ? markers.frontFaces() : primary.faces();
        float bounce = car.getSuspensionBounce(state.tickDelta) * 0.048f;
        float engineShake = car.engineRunning()
                ? (float)(Math.cos((car.getTime() + state.tickDelta) * 2.7) / 156) : 0;
        Matrix4f chassis = HeadlightLampGeometry.chassis(state.verticalOffset, state.angularRotation,
                state.yaw, car.getWheels().model().radius(), bounce, engineShake);
        Vec3 origin = renderOrigin(state);
        return VehicleSmallLampLighting.sources(front, markers.rearFaces(), signals, reverse.rearFaces(), chassis, origin,
                lamps, budget(quality).radius(), car.hasOpeningHood() ? state.mx5VisualPose : null);
    }

    public static void submit(AutomobileEntityRenderer.State state, PoseStack pose, SubmitNodeCollector submitter,
                              VehicleLampSnapshot lamps) {
        var car = state.automobile;
        if (car == null || state.isInvisible || car.getFrame().isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        VehicleLightQuality quality = VehicleLightingView.spillQuality(client);
        long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
        var budget = budget(quality);
        if (level == null || budget(quality).maxCars() == 0 || !lamps.hasPotentialSmallLight()
                || !hasLayoutPotential(car, lamps)
                || state.distanceToCameraSq > budget.maxCameraDistance() * budget.maxCameraDistance()) {
            remember(empty(quality, tick, car.getId(), lamps));
            return;
        }
        if (ComplementaryTemporalProjection.isShadowPass()) return;
        selectCars(client, level, quality);
        if (!SELECTED.contains(car.getId())) {
            remember(empty(quality, tick, car.getId(), lamps));
            return;
        }
        var contribution = SCHEDULER.contribution(car.getId());
        float fade = contribution == null ? 0 : contribution.weight(VehicleLightingView.presentationTime(client));
        if (fade <= 0) { remember(empty(quality, tick, car.getId(), lamps)); return; }
        // Only admitted visible contributors allocate transformed source vectors.
        List<VehicleSmallLampLighting.Light> sources = sources(state, lamps, quality);
        if (sources.isEmpty()) { remember(empty(quality, tick, car.getId(), lamps)); return; }
        SurfaceCache cached = CACHES.get(car);
        // At most one world-surface rebuild per car/tick. Modes refresh on the next tick; phase RGB is immediate.
        // A fresh tick always refreshes block state/visibility after edits, without retained clip/state snapshots.
        if (cached == null || cached.tick != tick || cached.quality != quality) {
            GLOBAL.begin(level, tick);
            cached = collect(client, level, car, sources, tick, lamps, quality, GLOBAL.remaining(QUOTAS.get(car.getId()), quality));
            GLOBAL.add(cached);
            CACHES.put(car, cached);
        }
        Map<Identifier, List<DrawPatch>> batches = new HashMap<>();
        int drawn = 0;
        for (SurfacePatch patch : cached.patches) {
            VehicleSmallLampLighting.Color[] colors = new VehicleSmallLampLighting.Color[4];
            float strongest = 0;
            for (int vertex = 0; vertex < 4; vertex++) {
                colors[vertex] = VehicleSmallLampLighting.color(sources, patch.vertices[vertex], patch.normal,
                        patch.visibleSources[vertex]);
                var color = colors[vertex];
                colors[vertex] = new VehicleSmallLampLighting.Color(color.rgb(), color.intensity() * fade);
                strongest = Math.max(strongest, colors[vertex].intensity());
            }
            if (strongest >= MIN_INTENSITY) {
                batches.computeIfAbsent(patch.atlas, ignored -> new ArrayList<>()).add(new DrawPatch(patch, colors));
                drawn++;
            }
        }
        Vec3 origin = renderOrigin(state);
        for (var entry : batches.entrySet()) {
            // Each immutable list/colour array belongs to this submission, including later shadow callbacks.
            List<DrawPatch> batch = List.copyOf(entry.getValue());
            submitter.submitCustomGeometry(pose, RenderTypes.eyes(entry.getKey()),
                    (transform, buffer) -> emit(batch, origin, transform, buffer));
        }
        int white = 0, red = 0, amber = 0, brake = 0, reverse = 0;
        List<Integer> colors = new ArrayList<>(4);
        for (var light : sources) {
            if (!light.active()) continue;
            switch (light.kind()) {
                case FRONT_WHITE -> white++;
                case REAR_RED -> red++;
                case SIGNAL_AMBER -> amber++;
                case BRAKE_RED -> brake++;
                case REVERSE_WHITE -> reverse++;
            }
            if (!colors.contains(light.color())) colors.add(light.color());
        }
        remember(new Snapshot(quality, tick, car.getId(), SELECTED.size(), cached.candidateVisits, cached.candidates,
                cached.blocks, cached.modelQuads, cached.testedPatches, cached.patches.size(), cached.rays, drawn,
                white, red, amber, lamps.signalMode(), lamps.signalLit(), colors, GEOMETRY.snapshot(),
                brake, reverse, lamps.brakeLit(), lamps.reverseLit()));
    }

    private static Vec3 renderOrigin(AutomobileEntityRenderer.State state) {
        Vec3 origin = new Vec3(state.x, state.y, state.z);
        return state.passengerOffset == null ? origin : origin.add(state.passengerOffset);
    }

    private static Snapshot empty(VehicleLightQuality quality, long tick, int entityId, VehicleLampSnapshot lamps) {
        return new Snapshot(quality, tick, entityId, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                lamps.signalMode(), lamps.signalLit(), List.of(), GEOMETRY.snapshot(), 0, 0,
                lamps.brakeLit(), lamps.reverseLit());
    }

    private static void remember(Snapshot snapshot) {
        SNAPSHOTS.remove(snapshot.entityId());
        SNAPSHOTS.put(snapshot.entityId(), snapshot);
        if (SNAPSHOTS.size() > 16) SNAPSHOTS.remove(SNAPSHOTS.keySet().iterator().next());
    }

    private static void selectCars(Minecraft client, ClientLevel level, VehicleLightQuality quality) {
        long tick = level.getGameTime();
        Object models = client.getModelManager().getBlockStateModelSet();
        GEOMETRY.selectGeneration(level, models);
        if (selectedLevel.get() == level && selectedModels == models && selectedQuality == quality && selectedTick == tick) return;
        boolean reloadedWithinTick = selectedLevel.get() == level && selectedTick == tick && selectedModels != models;
        if (selectedLevel.get() != level || selectedModels != models) {
            CACHES.clear();
            SNAPSHOTS.clear();
            SCHEDULER.reset();
        }
        selectedLevel = new WeakReference<>(level);
        selectedModels = models;
        selectedQuality = quality;
        selectedTick = tick;
        SELECTED.clear();
        QUOTAS.clear();
        if (reloadedWithinTick) return;
        var view = VehicleLightingView.capture(client);
        if (view == null) return;
        var camera = client.getCameraEntity();
        var ownCar = camera == null ? null : camera.getRootVehicle();
        var budget = budget(quality);
        List<VehicleLightingPolicy.Candidate> candidates = new ArrayList<>(VehicleLightingPolicy.MAX_CANDIDATES + 1);
        Comparator<VehicleLightingPolicy.Candidate> order = Comparator.comparingInt((VehicleLightingPolicy.Candidate candidate) ->
                SCHEDULER.contribution(candidate.id()) == null ? 1 : 0)
                .thenComparing(Comparator.comparingDouble(VehicleLightingPolicy.Candidate::score).reversed())
                .thenComparing(VehicleLightingPolicy.stableSourceOrder());
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof AutomobileEntity car) || car.isInvisible() || car.getFrame().isEmpty()) continue;
            var lamps = VehicleLampSnapshot.capture(car);
            if (!hasLayoutPotential(car, lamps)
                    || car.distanceToSqr(view.position()) > budget.maxCameraDistance() * budget.maxCameraDistance()) continue;
            int token = 31 * car.getFrame().model().modelId().hashCode() + lamps.lightMode().id();
            token = 31 * token + lamps.signalMode().id();
            token = 31 * token + (lamps.brakeLit() ? 1 : 0) + (lamps.reverseLit() ? 2 : 0);
            var source = new VehicleLightingPolicy.SourceKey(car.getUUID(), token);
            SCHEDULER.observeLive(tick, car.getId(), source);
            // Conservative bounds include the largest allowed local source offset.
            var bounds = car.getBoundingBox().inflate(budget.radius() + 8);
            double score = view.score(bounds);
            if (score <= 0) continue;
            candidates.add(new VehicleLightingPolicy.Candidate(car.getId(), score * (car == ownCar ? 1.5 : 1), 1, source));
            candidates.sort(order);
            if (candidates.size() > VehicleLightingPolicy.MAX_CANDIDATES) candidates.remove(VehicleLightingPolicy.MAX_CANDIDATES);
        }
        int activeLimit = VehicleLightingPolicy.smallActiveLimit(quality);
        var plan = SCHEDULER.update(tick, budget.maxCars(), activeLimit, activeLimit, candidates);
        var quotas = VehicleLightingPolicy.smallQuotas(budget, plan, (double)tick + 0.5);
        for (var item : plan) {
            SELECTED.add(item.id());
            QUOTAS.put(item.id(), quotas.get(item.id())); // Only the replaced pair divides its reservation.
        }
        CACHES.keySet().removeIf(car -> !SELECTED.contains(car.getId()));
    }

    private static SurfaceCache collect(Minecraft client, ClientLevel level, AutomobileEntity car,
                                        List<VehicleSmallLampLighting.Light> sources, long tick, VehicleLampSnapshot lamps,
                                        VehicleLightQuality quality, VehicleSmallLampLighting.Budget budget) {
        var candidates = VehicleSmallLampLighting.candidates(sources, budget);
        List<SurfacePatch> patches = new ArrayList<>(budget.maxPatches());
        List<BlockStateModelPart> parts = new ArrayList<>();
        RandomSource random = RandomSource.create();
        HeadlightVisibilityCache visibility = new HeadlightVisibilityCache(level, car);
        Work work = new Work(budget, visibility, lamps);
        outer: for (BlockPos position : candidates.cells()) {
            if (!level.isLoaded(position)) continue;
            BlockState state = level.getBlockState(position);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (work.blocks >= budget.maxBlocks()) break;
            work.blocks++;
            int faceMask = 0;
            for (int face = 0; face < DIRECTIONS.length; face++) {
                Direction direction = DIRECTIONS[face];
                BlockPos neighbor = position.relative(direction);
                if (!level.isLoaded(neighbor) || Block.shouldRenderFace(state, level.getBlockState(neighbor), direction)) {
                    faceMask |= 1 << face;
                }
            }
            parts.clear();
            long seed = state.getSeed(position);
            random.setSeed(seed);
            client.getModelManager().getBlockStateModelSet().get(state).collectParts(random, parts);
            for (var part : parts) {
                for (int face = -1; face < 6; face++) {
                    if (face >= 0 && (faceMask & (1 << face)) == 0) continue;
                    for (var quad : part.getQuads(face < 0 ? null : DIRECTIONS[face])) {
                        if (work.modelQuads >= budget.maxModelQuads()) break outer;
                        work.modelQuads++;
                        if (quad.materialInfo().layer() == ChunkSectionLayer.TRANSLUCENT) continue;
                        QuadGeometry geometry = geometry(state, seed,
                                VehicleSmallLampLighting.divisions(budget, sources, position), quad);
                        if (geometry.normal.lengthSqr() < 0.5) continue;
                        int tint = 0xFFFFFF;
                        if (quad.materialInfo().isTinted()) {
                            var tintSource = client.getBlockColors().getTintSource(state, quad.materialInfo().tintIndex());
                            if (tintSource != null) tint = tintSource.colorInWorld(state, level, position);
                        }
                        for (var local : geometry.patches) {
                            if (work.testedPatches >= budget.maxTestedPatches() || patches.size() >= budget.maxPatches()
                                    || (work.sharedVisibility == null && work.rays >= budget.maxRays())) break outer;
                            work.testedPatches++;
                            SurfacePatch patch = patch(visibility, sources, position, quad, geometry.normal, tint, local, work);
                            if (patch != null) patches.add(patch);
                        }
                    }
                }
            }
        }
        return new SurfaceCache(tick, quality, lamps.lightMode(), lamps.signalMode(), candidates.visitedCells(),
                candidates.cells().size(), work.blocks, work.modelQuads, work.testedPatches, work.rays, List.copyOf(patches));
    }

    private static QuadGeometry geometry(BlockState state, long seed, int divisions, BakedQuad quad) {
        GeometryKey key = new GeometryKey(state, seed, divisions, quad);
        QuadGeometry cached = GEOMETRY.get(key);
        if (cached != null) return cached;
        Vec3 normal = new Vec3(quad.position1()).subtract(new Vec3(quad.position0()))
                .cross(new Vec3(quad.position3()).subtract(new Vec3(quad.position0()))).normalize();
        if (normal.dot(new Vec3(quad.direction().getUnitVec3f())) < 0) normal = normal.reverse();
        List<HeadlightPatchGeometry> patches = new ArrayList<>(divisions * divisions);
        if (normal.lengthSqr() >= 0.5) {
            for (int row = 0; row < divisions; row++) {
                for (int column = 0; column < divisions; column++) {
                    patches.add(HeadlightPatchGeometry.create(quad.position0(), quad.position1(), quad.position2(),
                            quad.position3(), column, row, divisions));
                }
            }
        }
        cached = new QuadGeometry(normal, List.copyOf(patches));
        GEOMETRY.put(key, cached, 1 + patches.size());
        return cached;
    }

    private static SurfacePatch patch(HeadlightVisibilityCache visibility, List<VehicleSmallLampLighting.Light> sources,
                                      BlockPos position, BakedQuad quad, Vec3 normal, int tint,
                                      HeadlightPatchGeometry geometry, Work work) {
        Vec3[] vertices = new Vec3[4];
        int[] visible = new int[4];
        int visibleAny = 0;
        for (int vertex = 0; vertex < 4; vertex++) {
            Vec3 point = new Vec3(geometry.x(vertex) + position.getX() + normal.x * SURFACE_OFFSET,
                    geometry.y(vertex) + position.getY() + normal.y * SURFACE_OFFSET,
                    geometry.z(vertex) + position.getZ() + normal.z * SURFACE_OFFSET);
            vertices[vertex] = point;
        }
        if (work.sharedVisibility != null) {
            visible = work.sharedVisibility.sample(sources, vertices, normal);
            work.rays = work.sharedVisibility.rays();
            if (visible == null) return null;
            for (int mask : visible) visibleAny |= mask;
        } else for (int vertex = 0; vertex < 4; vertex++) {
            Vec3 point = vertices[vertex];
            for (int i = 0; i < sources.size(); i++) {
                var light = sources.get(i);
                if (!light.enabled() || light.potentialIntensity(point) < MIN_INTENSITY
                        || point.subtract(light.origin()).dot(normal) >= 0) continue;
                if (work.rays >= work.budget.maxRays()) break;
                work.rays++;
                // Check every lit vertex, never use a centre ray to light a partially occluded patch.
                if (VehicleSmallLampLighting.unobstructed(visibility, light.origin(), point)) {
                    visible[vertex] |= 1 << i;
                    visibleAny |= 1 << i;
                }
            }
        }
        if (visibleAny == 0) return null;
        var uv = geometry.uv();
        if (uv == null) uv = geometry.uv(UVPair.unpackU(quad.packedUV(0)), UVPair.unpackV(quad.packedUV(0)),
                UVPair.unpackU(quad.packedUV(1)), UVPair.unpackV(quad.packedUV(1)),
                UVPair.unpackU(quad.packedUV(2)), UVPair.unpackV(quad.packedUV(2)),
                UVPair.unpackU(quad.packedUV(3)), UVPair.unpackV(quad.packedUV(3)));
        return new SurfacePatch(quad.materialInfo().sprite().atlasLocation(), vertices, uv.u(), uv.v(), normal, tint, visible);
    }

    private static void emit(List<DrawPatch> patches, Vec3 origin, PoseStack.Pose pose, VertexConsumer buffer) {
        Matrix4f viewPose = ComplementaryTemporalProjection.apply(HeadlightViewLayering.apply(pose.pose()));
        for (DrawPatch draw : patches) {
            SurfacePatch patch = draw.patch;
            for (int vertex = 0; vertex < 4; vertex++) {
                var color = draw.colors[vertex];
                int rgb = color.rgb();
                int red = ((rgb >> 16) & 255) * ((patch.tint >> 16) & 255) / 255;
                int green = ((rgb >> 8) & 255) * ((patch.tint >> 8) & 255) / 255;
                int blue = (rgb & 255) * (patch.tint & 255) / 255;
                int alpha = Math.min((int)(VehicleSmallLampLighting.MAX_INTENSITY * 255),
                        Math.max(0, (int)(color.intensity() * 255)));
                Vec3 point = patch.vertices[vertex];
                buffer.addVertex(viewPose, (float)(point.x - origin.x), (float)(point.y - origin.y), (float)(point.z - origin.z))
                        .setColor(red, green, blue, alpha).setUv(patch.u[vertex], patch.v[vertex])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(HeadlightLampGeometry.FULL_BRIGHT)
                        .setNormal(pose, (float)patch.normal.x, (float)patch.normal.y, (float)patch.normal.z);
            }
        }
    }

    private static final class Work {
        private final VehicleSmallLampLighting.Budget budget;
        private final VehicleSmallLampVisibility sharedVisibility;
        private int blocks, modelQuads, testedPatches, rays;
        private Work(VehicleSmallLampLighting.Budget budget, HeadlightVisibilityCache visibility, VehicleLampSnapshot lamps) {
            this.budget = budget;
            // Extra rear functions can exhaust BALANCED's existing ray cap. Keep accepted legacy
            // admission for its original states; share exact vertices atomically for the new ones.
            sharedVisibility = budget.divisions() > 2 || lamps.brakeLit() || lamps.reverseLit()
                    ? new VehicleSmallLampVisibility(budget.maxRays(),
                            (origin, point) -> VehicleSmallLampLighting.unobstructed(visibility, origin, point)) : null;
        }
    }

    /** The shared preset is tick-latched; generation reloads cannot reset spent fleet work. */
    private static final class GlobalWork {
        private WeakReference<ClientLevel> level = new WeakReference<>(null);
        private long tick = Long.MIN_VALUE;
        private int candidateVisits, blocks, modelQuads, testedPatches, patches, rays;

        private void begin(ClientLevel level, long tick) {
            if (this.level.get() == level && this.tick == tick) return;
            this.level = new WeakReference<>(level);
            this.tick = tick;
            candidateVisits = blocks = modelQuads = testedPatches = patches = rays = 0;
        }

        private VehicleSmallLampLighting.Budget remaining(VehicleSmallLampLighting.Budget requested, VehicleLightQuality quality) {
            var ceiling = budget(quality);
            int cars = ceiling.maxCars();
            return new VehicleSmallLampLighting.Budget(requested.radius(), requested.maxCars(),
                    Math.min(requested.maxCandidateVisits(), left(ceiling.maxGlobalCandidateVisits(), candidateVisits)),
                    requested.maxCandidates(), Math.min(requested.maxBlocks(), left(cars * ceiling.maxBlocks(), blocks)),
                    Math.min(requested.maxModelQuads(), left(cars * ceiling.maxModelQuads(), modelQuads)),
                    Math.min(requested.maxTestedPatches(), left(cars * ceiling.maxTestedPatches(), testedPatches)),
                    Math.min(requested.maxPatches(), left(ceiling.maxGlobalPatches(), patches)),
                    Math.min(requested.maxRays(), left(ceiling.maxGlobalRays(), rays)),
                    requested.divisions(), requested.maxCameraDistance());
        }

        private static int left(int ceiling, int used) { return Math.max(0, ceiling - used); }

        private void add(SurfaceCache cache) {
            candidateVisits += cache.candidateVisits;
            blocks += cache.blocks;
            modelQuads += cache.modelQuads;
            testedPatches += cache.testedPatches;
            patches += cache.patches.size();
            rays += cache.rays;
        }
    }

    private record SurfaceCache(long tick, VehicleLightQuality quality, VehicleLightMode lightMode, VehicleSignalMode signalMode,
                                int candidateVisits, int candidates, int blocks, int modelQuads, int testedPatches, int rays,
                                List<SurfacePatch> patches) {}
    private record SurfacePatch(Identifier atlas, Vec3[] vertices, float[] u, float[] v, Vec3 normal, int tint, int[] visibleSources) {}
    private record DrawPatch(SurfacePatch patch, VehicleSmallLampLighting.Color[] colors) {}
    private record QuadGeometry(Vec3 normal, List<HeadlightPatchGeometry> patches) {}
    private record GeometryKey(BlockState state, long seed, int divisions, BakedQuad quad) {
        @Override public boolean equals(Object other) {
            return other instanceof GeometryKey key && state == key.state && seed == key.seed
                    && divisions == key.divisions && quad == key.quad;
        }
        @Override public int hashCode() {
            return 31 * (31 * (31 * System.identityHashCode(state) + Long.hashCode(seed)) + divisions)
                    + System.identityHashCode(quad);
        }
    }
}
