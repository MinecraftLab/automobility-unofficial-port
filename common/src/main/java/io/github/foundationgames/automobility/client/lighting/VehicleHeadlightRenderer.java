package io.github.foundationgames.automobility.client.lighting;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;
import io.github.foundationgames.automobility.automobile.light.Mx5PopupGeometry;
import io.github.foundationgames.automobility.automobile.light.Mx5VisualPose;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.entity.AutomobileTerrainRevisions;
import io.github.foundationgames.automobility.entity.render.AutomobileEntityRenderer;
import io.github.foundationgames.automobility.client.vehicle.VehicleLightQuality;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Experimental built-in surface lighting: reuses real opaque block-model geometry and UVs,
 * with an emissive translucent copy weighted by two moving, block-occluded spotlights.
 * Uses a standard Minecraft pipeline, so no external lighting mod or shaderpack is required.
 * It is not a physical light source inside a shaderpack's reflections, fog or GI. Transparent
 * surfaces and entity shadows are deliberately outside this first renderer's scope.
 */
public final class VehicleHeadlightRenderer {
    public static final double LOW_RANGE = 22;
    public static final double HIGH_RANGE = 38;
    private static final int FAN_RAYS = 55;
    private static final int MAX_BLOCKS = HeadlightSurfaceDiscovery.MAX_BLOCKS;
    private static final int MAX_NEAR_BLOCKS = 128;
    private static final int MAX_PATCHES = MAX_BLOCKS * 4 + MAX_NEAR_BLOCKS * 12;
    private static final int MAX_TESTED_PATCHES = MAX_BLOCKS * 24 + MAX_NEAR_BLOCKS * 72;
    private static final int MAX_DISCOVERY_RAYS = FAN_RAYS + HeadlightRoadFootprint.MAX_COLUMNS;
    private static final float MIN_DRAW_INTENSITY = 1.0f / 255;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final double SURFACE_OFFSET = 0.0015;
    private static final Map<AutomobileEntity, SurfaceCache> CACHES = new WeakHashMap<>();
    private static final Map<AutomobileEntity, AtlasCapacity> ATLAS_CAPACITIES = new WeakHashMap<>();
    private static final boolean PREPARED_ENABLED = Boolean.parseBoolean(System.getProperty("automobility.experimental.preparedLights", "true"));
    private static final boolean STABLE_ENGINE_PROJECTION = Boolean.parseBoolean(System.getProperty("automobility.experimental.stableEngineProjection", "true"));
    private static final boolean PERSISTENT_PARKED = Boolean.parseBoolean(System.getProperty("automobility.experimental.persistentParkedLights", "true"));
    private static final boolean MOVING_PHASE = PERSISTENT_PARKED && Boolean.parseBoolean(System.getProperty("automobility.experimental.movingDirtyPhase", "true"));
    private static final boolean REGIONAL_TERRAIN = MOVING_PHASE && HeadlightTerrainCertificate.ENABLED;
    private static final boolean PHASED_PARKED = !PERSISTENT_PARKED && Boolean.getBoolean("automobility.experimental.phasedParkedLights");
    private static final boolean PARKED_CACHE = PERSISTENT_PARKED || PHASED_PARKED;
    private static final HeadlightPreparedCache<Map<Identifier, LitBatch>> PREPARED = new HeadlightPreparedCache<>(32);
    private static long exhaustedPatches;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final double[] SCORE_SAMPLES = {0.2, 0.5, 0.8};
    // Weight counts both raw quads and subdivisions, including lazily initialized UVs.
    private static final HeadlightGeometryCache<GeometryKey, QuadGeometry> GEOMETRY_CACHE =
            new HeadlightGeometryCache<>(4096, 65536);
    private static final Set<Integer> SELECTED = new HashSet<>();
    private static final VehicleLightingPolicy.Scheduler SCHEDULER = new VehicleLightingPolicy.Scheduler();
    private static final Map<Integer, MainWork> WORK = new HashMap<>();
    private static VehicleLightQuality selectedQuality = VehicleLightQuality.BALANCED;
    private static long workTick = Long.MIN_VALUE;
    private static int selectorEntityVisits, selectorPoses;
    private static WeakReference<ClientLevel> selectedLevel = new WeakReference<>(null);
    private static Object selectedModels;
    private static long selectedTick = Long.MIN_VALUE;
    private static long presentationFrame, roleFrame = Long.MIN_VALUE;
    private static final VehicleProjectionPolicy.Selector ROLE_SELECTOR = new VehicleProjectionPolicy.Selector();
    private static final List<ProjectionSource> ROLE_SOURCES = new ArrayList<>();
    private static VehicleProjectionPolicy.Plan rolePlan;
    private record ProjectionSource(WeakReference<AutomobileEntity> reference, VehicleProjectionPolicy.Key key, AABB bounds) {
        AutomobileEntity car() { return reference.get(); }
    }

    private static VehicleProjectionPolicy.Key projectionKey(AutomobileEntity car) {
        var key = sourceKey(car);
        return new VehicleProjectionPolicy.Key(car.getId(), key.identity(), key.lightToken());
    }
    /** Mounted identity is independent of whether its projected footprint is on screen. */
    private static java.util.UUID mountedPrimary(Minecraft client, ClientLevel level) {
        if (client.player != null && client.player.getRootVehicle() instanceof AutomobileEntity car
                && car.level() == level && !car.isRemoved()
                && io.github.foundationgames.automobility.client.vehicle.VehicleClientControls.isDriver(client, car))
            return car.getUUID();
        var camera = client.getCameraEntity();
        if (camera != null && camera.getRootVehicle() instanceof AutomobileEntity car
                && car.level() == level && !car.isRemoved()) return car.getUUID();
        return null;
    }
    /** Frame-local presentation roles reuse the tick's bounded, already charged WORK envelope. */
    private static void selectRoles(Minecraft client, ClientLevel level) {
        if (roleFrame == presentationFrame) return;
        roleFrame = presentationFrame;
        var view = VehicleLightingView.capture(client);
        List<VehicleProjectionPolicy.Candidate> candidates = new ArrayList<>(ROLE_SOURCES.size());
        for (var source : ROLE_SOURCES) {
            var car = source.car();
            if (car == null || car.level() != level || car.isRemoved() || !source.key().equals(projectionKey(car))) continue;
            boolean visible = view != null && view.visible(source.bounds());
            double score = view == null ? 0 : view.score(source.bounds());
            if (score == 0 && view != null) score = 0.05 / (16 + car.distanceToSqr(view.position()));
            var driver = car.getControllingPassenger();
            boolean driven = driver != null && driver.isAlive() && car.isDriving(driver);
            candidates.add(new VehicleProjectionPolicy.Candidate(source.key(), score, visible, driven));
        }
        rolePlan = ROLE_SELECTOR.select(level.getGameTime(), selectedQuality,
                VehicleLightingView.graphics(client).profile(), mountedPrimary(client, level), candidates);
        List<Integer> wanted = rolePlan.roles().entrySet().stream()
                .filter(e -> e.getValue() == VehicleProjectionPolicy.Role.FULL)
                .sorted(Comparator.comparingInt((Map.Entry<VehicleProjectionPolicy.Key,VehicleProjectionPolicy.Role> e) ->
                        e.getKey().identity().equals(rolePlan.mainIdentity()) ? 0 : rolePlan.priorityFull().contains(e.getKey()) ? 1 : 2)
                        .thenComparingInt(e -> e.getKey().id()))
                .map(e -> e.getKey().id()).toList();
        VehicleProjectionReservations.transferUnused(WORK, wanted, MainWork::unused);

    }
    public static VehicleProjectionPolicy.Plan projectionPlanSnapshot() { return rolePlan; }
    public static VehicleConeRenderer.Snapshot coneSnapshot() { return VehicleConeRenderer.snapshot(); }


    private VehicleHeadlightRenderer() {}

    /** Serial is independent of the retained body feature and never performs world work. */
    public static void beginFrame(){
        presentationFrame++; roleFrame=Long.MIN_VALUE; VehicleConeRenderer.beginFrame();
        if (selectedLevel.get() != Minecraft.getInstance().level) {
            ROLE_SOURCES.clear(); ROLE_SELECTOR.reset(); rolePlan=null;
        }
        if(PARKED_CACHE)ParkedRefresh.beginFrame();
    }
    public static String parkedRefreshSnapshot(){return "enabled="+PARKED_CACHE+" persistent="+PERSISTENT_PARKED+" phased="+PHASED_PARKED+" "+ParkedRefresh.snapshot()+" stableEngineProjection="+STABLE_ENGINE_PROJECTION+" movingDirtyPhase="+MOVING_PHASE;}

    /** Conservative bounds keep a car's light visible while its body is outside the frustum. */
    public static AABB cullingBounds(AutomobileEntity automobile) {
        AABB body = automobile.getBoundingBox();
        if (!automobile.getProjectionLightMode().emitsHeadlightBeam()) {
            return body;
        }
        double range = automobile.getProjectionLightMode() == VehicleLightMode.HIGH ? HIGH_RANGE : LOW_RANGE;
        // Full orientation-independent bound also covers steep chassis pitch and interpolation.
        return body.inflate(range + 4);
    }

    /** Pose-aware bound; unsupported passenger/invalid poses retain the original conservative bound. */
    public static AABB cullingBounds(AutomobileEntity automobile, float tickDelta) {
        if (!automobile.getProjectionLightMode().emitsHeadlightBeam()) return automobile.getBoundingBox();
        AABB fallback = cullingBounds(automobile);
        var state = AutomobileEntityRenderer.headlightCullingState(automobile, tickDelta);
        return state == null ? fallback : HeadlightCullingBounds.enclose(
                automobile.getBoundingBox(), beams(state), 2.0, fallback).inflate(VehicleProjectionPolicy.VIEW_MARGIN);
    }

    /** Read-only counters for opt-in native cache mutation/reload acceptance. */
    public static HeadlightGeometryCache.Snapshot geometryCacheSnapshot() {
        return GEOMETRY_CACHE.snapshot();
    }

    public static String preparedCacheSnapshot() {
        return "enabled=" + PREPARED_ENABLED + " " + PREPARED.snapshot() + " exhaustedPatches=" + exhaustedPatches;
    }

    /** Optional read-only observations; candidate reservations are not actual visit/CPU counts. */
    public record LightingWorkSnapshot(VehicleLightQuality quality, long tick, int activeCars,
                                        int selectorEntityVisits, int selectorPoses, int discoveryRays,
                                        int discoveryCandidateReservations, int blocks, int modelQuads,
                                        int testedPatches, int patches, int visibilityRays) {}

    public static LightingWorkSnapshot lightingWorkSnapshot() {
        int discovery = 0, candidates = 0, blocks = 0, quads = 0, tested = 0, patches = 0, visibility = 0;
        for (var item : WORK.values()) {
            discovery += item.discoveryRays; candidates += item.candidates; blocks += item.blocks;
            quads += item.modelQuads; tested += item.testedPatches; patches += item.patches; visibility += item.visibilityRays;
            if (item.detail != null) {
                tested += item.detail.testedPatches; patches += item.detail.patches; visibility += item.detail.visibilityRays;
            }
        }
        return new LightingWorkSnapshot(selectedQuality, workTick, SELECTED.size(), selectorEntityVisits, selectorPoses,
                discovery, candidates, blocks, quads, tested, patches, visibility);
    }

    /** Call before applying the car's local pose; the incoming stack is at its entity origin. */
    public static void submit(AutomobileEntityRenderer.State state, PoseStack pose, SubmitNodeCollector submitter) {
        AutomobileEntity automobile = state.automobile;
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null || automobile == null || state.isInvisible || automobile.getFrame().isEmpty()
                || !automobile.getProjectionLightMode().emitsHeadlightBeam() || state.distanceToCameraSq > 96 * 96) {
            return;
        }
        if (ComplementaryTemporalProjection.isShadowPass()) return;
        selectCars(client, level);
        if (!SELECTED.contains(automobile.getId())) {
            return;
        }
        var role = rolePlan == null ? VehicleProjectionPolicy.Role.LAMP : rolePlan.role(projectionKey(automobile));
        boolean sameInstance = false;
        for (var source : ROLE_SOURCES) if (source.car() == automobile) { sameInstance = true; break; }
        if (!sameInstance || role == VehicleProjectionPolicy.Role.LAMP) return;
        List<HeadlightBeam> beams = beams(state);
        if (beams.isEmpty()) return;
        if (role == VehicleProjectionPolicy.Role.CONE) {
            VehicleConeRenderer.submit(state, pose, submitter, beams);
            return;
        }
        var contribution = SCHEDULER.contribution(automobile.getId());
        // This weight refines native geometry, never switches the base light on/off.
        float refinement = contribution == null ? 0 : contribution.weight(VehicleLightingView.presentationTime(client));
        long tick = level.getGameTime();
        SurfaceCache cached = CACHES.get(automobile);
        int topology = topology(automobile.getFrame().model().modelId(), automobile.getEffectiveVehicleLightMode(), state.mx5VisualPose);
        cached=PERSISTENT_PARKED?ParkedRefresh.persistentSurface(client,level,automobile,beams,tick,topology,cached)
                :PHASED_PARKED?ParkedRefresh.surface(client,level,automobile,beams,tick,topology,cached)
                :legacySurfaces(client,level,automobile,beams,tick,topology,cached);
        if(cached==null)return;
        AtlasCapacity capacity = ATLAS_CAPACITIES.get(automobile);
        if (capacity == null || capacity.source != cached) {
            Map<Identifier, Integer> counts = new HashMap<>();
            for (SurfacePatch patch : cached.patches) counts.merge(patch.atlas, 1, Integer::sum);
            capacity = new AtlasCapacity(cached, counts);
            ATLAS_CAPACITIES.put(automobile, capacity);
        }
        Map<Identifier, LitBatch> batches = PREPARED_ENABLED
                ? PREPARED.get(automobile.getId(), cached, beams, refinement) : null;
        if (batches == null) {
            batches = prepareBatches(cached, beams, refinement, capacity.counts);
            if (PREPARED_ENABLED) PREPARED.put(automobile.getId(), cached, beams, refinement, batches);
        }
        Vec3 origin = renderOrigin(state);
        for (var batch : batches.entrySet()) {
            LitBatch patches = batch.getValue();
            // Fresh origin/pose; original native bucket and global quad sorting.
            submitter.submitCustomGeometry(pose, RenderTypes.eyes(batch.getKey()),
                    (transform, buffer) -> emit(patches, origin, transform, buffer));
        }
    }

    /** Exact006 tick/topology freshness and existing WORK accounting, including fallback. */
    private static SurfaceCache legacySurfaces(Minecraft client,ClientLevel level,AutomobileEntity car,
                                               List<HeadlightBeam> beams,long tick,int topology,SurfaceCache cached){
        if(cached==null || cached.tick!=tick || cached.topology!=topology){
            var work=WORK.get(car.getId());
            if(work==null || !work.canRebuild())return null;
            cached=PARKED_CACHE?ParkedRefresh.collect(client,level,car,beams,tick,topology,work)
                    :collectSurfaces(client,level,car,beams,tick,topology,work);
            CACHES.put(car,cached);
        }
        return cached;
    }

    private static Map<Identifier, LitBatch> prepareBatches(SurfaceCache cached, List<HeadlightBeam> beams,
                                                            float refinement, Map<Identifier, Integer> counts) {
        Map<Identifier, LitBatch> batches = new HashMap<>();
        // Only transition frames need parent fields. Cache scalars locally to
        // this submission, never visibility or mutable world/entity state.
        Map<SurfacePatch, float[]> parentFields = refinement < 1 ? new java.util.IdentityHashMap<>() : null;
        java.util.function.Function<Identifier, LitBatch> factory = atlas -> new LitBatch(counts.get(atlas));
        for (SurfacePatch patch : cached.patches) {
            float intensity0 = intensityAt(patch, beams, 0, refinement, parentFields);
            float intensity1 = intensityAt(patch, beams, 1, refinement, parentFields);
            float intensity2 = intensityAt(patch, beams, 2, refinement, parentFields);
            float intensity3 = intensityAt(patch, beams, 3, refinement, parentFields);
            float strongest = Math.max(Math.max(intensity0, intensity1), Math.max(intensity2, intensity3));
            if (strongest >= MIN_DRAW_INTENSITY) {
                batches.computeIfAbsent(patch.atlas, factory)
                        .add(patch, intensity0, intensity1, intensity2, intensity3);
            }
        }
        // No patch/batch mutates after publication. Preserve original HashMap order.
        return java.util.Collections.unmodifiableMap(batches);
    }

    /** Chassis projection; opt-in stabilization excludes only cosmetic engine vibration. */
    public static List<HeadlightBeam> beams(AutomobileEntityRenderer.State state) {
        AutomobileEntity automobile = state.automobile;
        float bounce = automobile.getSuspensionBounce(state.tickDelta) * 0.048f;
        float engineShake = HeadlightProjectionMotion.engineShake(automobile.engineRunning(),
                automobile.getTime(), state.tickDelta, STABLE_ENGINE_PROJECTION);
        Matrix4f chassis = HeadlightLampGeometry.chassis(state.verticalOffset, state.angularRotation,
                state.yaw, automobile.getWheels().model().radius(), bounce, engineShake);
        return beams(VehicleHeadlightLayout.forFrame(automobile.getFrame()), automobile.getFrame().model().modelId(),
                chassis, renderOrigin(state), automobile.getEffectiveVehicleLightMode(), state.mx5VisualPose);
    }

    static int topology(Identifier model, VehicleLightMode normal, Mx5VisualPose visual) {
        return Mx5PopupGeometry.appliesTo(model) ? (1 << 8) | visual.beamMode(true).id()
                | (visual.beamMode(false).id() << 3) : normal.id();
    }

    static List<HeadlightBeam> beams(VehicleHeadlightLayout layout, Identifier model, Matrix4f chassis,
                                     Vec3 origin, VehicleLightMode normal, Mx5VisualPose visual) {
        boolean mx5 = Mx5PopupGeometry.appliesTo(model);
        List<HeadlightBeam> result = new ArrayList<>(2);
        for (Vec3 lamp : layout.lamps()) {
            boolean left = lamp.x < 0; // Authored popup side, not the driver's blink indicator side.
            var mode = mx5 ? visual.beamMode(left) : normal;
            if (!mode.emitsHeadlightBeam()) continue;
            boolean high = mode == VehicleLightMode.HIGH;
            float dip = (float)Math.toRadians(high ? 2 : 6);
            Vec3 forward = new Vec3(chassis.transformDirection(new Vector3f(0, (float)Math.sin(dip), -(float)Math.cos(dip)))).normalize();
            Vec3 right = new Vec3(chassis.transformDirection(new Vector3f(1, 0, 0))).normalize();
            Vec3 up = right.cross(forward).normalize();
            Vec3 local = mx5 ? Mx5PopupGeometry.position(lamp, visual.progress(left)) : lamp;
            // Keep the world translation in double precision, including far from the world origin.
            Vec3 position = new Vec3(chassis.transformPosition(local.toVector3f())).add(origin);
            result.add(new HeadlightBeam(position, forward, right, up,
                    high ? HIGH_RANGE : LOW_RANGE, Math.tan(Math.toRadians(high ? 21 : 29)), Math.tan(Math.toRadians(high ? 11 : 16))));
        }
        return result;
    }

    private static Vec3 renderOrigin(AutomobileEntityRenderer.State state) {
        Vec3 position = new Vec3(state.x, state.y, state.z);
        return state.passengerOffset == null ? position : position.add(state.passengerOffset);
    }

    private static VehicleLightingPolicy.SourceKey sourceKey(AutomobileEntity car) {
        return new VehicleLightingPolicy.SourceKey(car.getUUID(),
                31 * car.getFrame().model().modelId().hashCode() + car.getProjectionLightMode().id());
    }

    private static void selectCars(Minecraft client, ClientLevel level) {
        long tick = level.getGameTime();
        Object models = client.getModelManager().getBlockStateModelSet();
        GEOMETRY_CACHE.selectGeneration(level, models);
        if (selectedLevel.get() == level && selectedTick == tick && selectedModels == models) {
            selectRoles(client, level);
            return;
        }
        boolean newLevel = selectedLevel.get() != level;
        boolean reloadedWithinTick = !newLevel && selectedTick == tick && selectedModels != models;
        if (newLevel || selectedModels != models) {
            CACHES.clear();
            ATLAS_CAPACITIES.clear();
            PREPARED.clear();
            SCHEDULER.reset();
            ROLE_SELECTOR.reset(); rolePlan = null; roleFrame = Long.MIN_VALUE;
            VehicleConeRenderer.invalidate();
        }
        // A quality save is latched once per tick. Resource reloads cannot mint a new
        // reservation for an already charged entity in this same world/tick.
        if (newLevel || workTick != tick) {
            WORK.clear(); workTick = tick;
            selectedQuality = VehicleLightingView.quality(client);
        }
        selectedLevel = new WeakReference<>(level);
        selectedModels = models;
        selectedTick = tick;
        SELECTED.clear(); ROLE_SOURCES.clear(); roleFrame = Long.MIN_VALUE;
        selectorEntityVisits = selectorPoses = 0;
        // Old batches remain immutable. Defer recollection after a same-tick model
        // reload rather than resetting accounting and rebuilding another whole fleet.
        if (reloadedWithinTick) return;
        var view = VehicleLightingView.capture(client);
        if (view == null) {
            return;
        }
        var cameraEntity = client.getCameraEntity();
        var ownCar = cameraEntity == null ? null : cameraEntity.getRootVehicle();
        List<BroadCandidate> nearest = new ArrayList<>(VehicleLightingPolicy.MAX_CANDIDATES + 1);
        var mainIdentity = mountedPrimary(client, level);
        Comparator<BroadCandidate> broadOrder = Comparator.comparingInt((BroadCandidate candidate) ->
                VehicleProjectionPolicy.admissionPriority(
                        mainIdentity != null && mainIdentity.equals(candidate.car().getUUID()),
                        candidate.car().getControllingPassenger() instanceof net.minecraft.world.entity.LivingEntity driver
                                && driver.isAlive() && candidate.car().isDriving(driver),
                        SCHEDULER.contribution(candidate.car().getId()) != null))
                .thenComparing(Comparator.comparingDouble(BroadCandidate::score).reversed())
                .thenComparingInt(candidate -> candidate.car().getId());
        for (var entity : level.entitiesForRendering()) {
            selectorEntityVisits++;
            if (entity instanceof AutomobileEntity car && !car.isInvisible() && !car.getFrame().isEmpty()
                    && car.getProjectionLightMode().emitsHeadlightBeam()
                    && car.distanceToSqr(view.position()) < 96 * 96) {
                SCHEDULER.observeLive(tick, car.getId(), sourceKey(car));
                double score = view.score(cullingBounds(car));
                if (score <= 0) continue;
                nearest.add(new BroadCandidate(car, score * (car == ownCar ? 1.5 : 1)));
                nearest.sort(broadOrder);
                if (nearest.size() > VehicleLightingPolicy.MAX_CANDIDATES) {
                    nearest.remove(VehicleLightingPolicy.MAX_CANDIDATES);
                }
            }
        }
        List<VehicleLightingPolicy.Candidate> candidates = new ArrayList<>(nearest.size());
        for (var broad : nearest) {
            selectorPoses++;
            var car = broad.car();
            float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(!car.level().tickRateManager().isEntityFrozen(car));
            var state = AutomobileEntityRenderer.headlightCullingState(car, partial);
            if (state == null) {
                // Retain a conservative reservation for supported render submissions
                // with additional passenger offsets; actual submit still supplies the pose.
                candidates.add(new VehicleLightingPolicy.Candidate(car.getId(), broad.score(), 1, sourceKey(car)));
                continue;
            }
            var beams = beams(state);
            if (beams.isEmpty()) continue;
            var origin = beams.getFirst().origin();
            var lampBounds = new AABB(origin.x, origin.y, origin.z, origin.x, origin.y, origin.z);
            var bound = HeadlightCullingBounds.enclose(lampBounds, beams, 2, cullingBounds(car))
                    .inflate(VehicleProjectionPolicy.VIEW_MARGIN);
            if (!view.visible(bound)) continue;
            double score = view.score(bound) * 0.1;
            for (var beam : beams) {
                for (double fraction : SCORE_SAMPLES)
                    score += view.pointScore(beam.origin().add(beam.forward().scale(beam.range() * fraction)));
            }
            // Logical admission cost is fixed. Grid/pose changes cannot evict an incumbent;
            // actual discovery/geometry reservations are assigned to the entire plan below.
            candidates.add(new VehicleLightingPolicy.Candidate(car.getId(), score * (car == ownCar ? 1.5 : 1), 1, sourceKey(car)));
        }
        // Presentation profiles retain the qualified HIGH optical/detail envelope.
        // mainBudget(ECONOMY) itself remains the original coverage floor.
        var budget = VehicleLightingPolicy.mainBudget(VehicleLightQuality.HIGH);
        // All bounded cars retain identity/cache membership. Only FULL roles reserve
        // surface work; cone scans have their separate bounded per-frame allowance.
        for (var item : candidates) SELECTED.add(item.id());
        for (var broad : nearest) if (SELECTED.contains(broad.car().getId())) {
            var car = broad.car();
            float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(!car.level().tickRateManager().isEntityFrozen(car));
            ROLE_SOURCES.add(new ProjectionSource(new WeakReference<>(car), projectionKey(car), cullingBounds(car, partial)));
        }
        selectRoles(client, level);
        Set<Integer> fullIds = new HashSet<>();
        for (var entry : rolePlan.roles().entrySet())
            if (entry.getValue() == VehicleProjectionPolicy.Role.FULL) fullIds.add(entry.getKey().id());
        var fullCandidates = candidates.stream().filter(item -> fullIds.contains(item.id())).toList();
        boolean fine = budget.nearBlocks() > 0;
        var plan = SCHEDULER.update(tick, fine ? budget.maxCars() : 0,
                fine ? budget.maxActiveCars() : 0, fine ? budget.maxActiveCars() : 0, fullCandidates);
        var quotas = VehicleLightingPolicy.coverageQuotas(budget, fullCandidates, plan, (double)tick + 0.5);
        for (var item : fullCandidates) WORK.computeIfAbsent(item.id(), ignored -> new MainWork(quotas.get(item.id())));
        CACHES.keySet().removeIf(car -> !SELECTED.contains(car.getId()));
        ATLAS_CAPACITIES.keySet().removeIf(car -> !SELECTED.contains(car.getId()));
        PREPARED.retain(SELECTED);
    }

    private static SurfaceCache collectSurfaces(Minecraft client, ClientLevel level, AutomobileEntity automobile,
                                               List<HeadlightBeam> beams, long tick, int topology, MainWork work) {
        if (work == null) return new SurfaceCache(tick, topology, List.of());
        if (work.quota.discoveryRays() - work.discoveryRays < FAN_RAYS)
            return new SurfaceCache(tick, topology, List.of());
        LinkedHashSet<BlockPos> blocks = new LinkedHashSet<>();
        BlockFacts facts = new BlockFacts(level);
        HeadlightVisibilityCache visibility = new HeadlightVisibilityCache(level, automobile);
        HeadlightBeam beam = beams.getFirst();
        var columns = HeadlightRoadFootprint.columns(beam.origin().x, beam.origin().z,
                beam.forward().x, beam.forward().z, beam.right().x, beam.right().z,
                beam.range(), beam.horizontalTangent(), Math.max(0,
                        Math.min(HeadlightRoadFootprint.MAX_COLUMNS, work.quota.discoveryRays() - work.discoveryRays - FAN_RAYS)));
        if (FAN_RAYS + columns.size() > work.quota.discoveryRays() - work.discoveryRays)
            return new SurfaceCache(tick, topology, List.of());
        int rays = 0;
        // Actual first hits seed the connected wall/ceiling surface search below.
        for (int y = -2; y <= 2; y++) {
            for (int x = -5; x <= 5; x++) {
                Vec3 direction = beam.forward().add(beam.right().scale(x * beam.horizontalTangent() / 5))
                        .add(beam.up().scale(y * beam.verticalTangent() / 2)).normalize();
                addHit(level, visibility, beam.origin(), beam.origin().add(direction.scale(beam.range())), blocks, work);
                rays++;
            }
        }
        // Quota-limited probes span the complete range, never a near-to-far prefix.
        // Connected discovery fills existing exposed surfaces between probes. Isolated
        // surfaces under reduced quotas still require native appearance qualification.
        for (var column : columns) {
            if (++rays > MAX_DISCOVERY_RAYS) {
                break;
            }
            double y = beam.origin().y + beam.forward().y * column.distance() + beam.right().y * column.across();
            Vec3 center = new Vec3(column.x() + 0.5, y, column.z() + 0.5);
            addHit(level, visibility, center.add(0, 3, 0), center.add(0, -5, 0), blocks, work);
        }
        List<HeadlightSurfaceDiscovery.Cell> seeds = new ArrayList<>(blocks.size());
        for (BlockPos position : blocks) {
            seeds.add(new HeadlightSurfaceDiscovery.Cell(position.getX(), position.getY(), position.getZ()));
        }
        blocks.clear();
        for (var cell : HeadlightSurfaceDiscovery.expand(seeds, beams,
                candidate -> exposedBlock(facts, new BlockPos(candidate.x(), candidate.y(), candidate.z())),
                Math.min(MAX_BLOCKS, work.quota.blocks() - work.blocks),
                Math.min(HeadlightSurfaceDiscovery.MAX_CANDIDATES, work.quota.candidates() - work.candidates))) {
            blocks.add(new BlockPos(cell.x(), cell.y(), cell.z()));
        }
        // Reserve the bounded discovery visit allowance conservatively for this rebuild.
        work.candidates = work.quota.candidates();
        List<SurfacePatch> patches = new ArrayList<>();
        List<BlockStateModelPart> parts = new ArrayList<>();
        RandomSource random = RandomSource.create();
        int testedPatches = 0;
        int nearBlocks = 0;
        outer: for (BlockPos position : blocks) {
            if (work.blocks >= work.quota.blocks()) break;
            work.blocks++;
            BlockFact current = facts.at(position);
            if (!current.loaded) continue;
            var blockState = current.state;
            if (blockState.isAir() || !blockState.getFluidState().isEmpty()) {
                continue;
            }
            int divisions = 2;
            boolean refine = false;
            // Only the separate detail reservation can request native child tiles.
            if (work.detail != null && nearBlocks < Math.min(MAX_NEAR_BLOCKS, work.detail.quota.nearBlocks())
                    && HeadlightPatchMath.blockCenterDistanceSquared(position.getX(), position.getY(), position.getZ(), beam.origin()) < 8 * 8) {
                refine = true; nearBlocks++;
            }
            int faceMask = facts.faceMask(position, blockState);
            parts.clear();
            long seed = blockState.getSeed(position);
            random.setSeed(seed);
            HeadlightStaticSurfaceScope.observeModel(client.getModelManager().getBlockStateModelSet().get(blockState),level,blockState).collectParts(random, parts);
            for (BlockStateModelPart part : parts) {
                for (int face = -1; face < 6; face++) {
                    Direction direction = face < 0 ? null : DIRECTIONS[face];
                    // A fresh mask per block/rebuild preserves partial shapes and unloaded neighbors.
                    // Unculled (-1) quads remain eligible regardless of the native face mask.
                    if (face >= 0 && (faceMask & (1 << face)) == 0) continue;
                    for (BakedQuad quad : part.getQuads(direction)) {
                        if (work.modelQuads >= work.quota.modelQuads()) break outer;
                        work.modelQuads++;
                        if (quad.materialInfo().layer() == ChunkSectionLayer.TRANSLUCENT) {
                            continue;
                        }
                        QuadGeometry geometry = geometry(blockState, seed, divisions, quad);
                        Vec3 normal = facingNormal(beams, position, quad, geometry.normal);
                        if (normal == null) {
                            continue;
                        }
                        boolean affineUv = refine && HeadlightRefinement.affineUv(
                                UVPair.unpackU(quad.packedUV(0)), UVPair.unpackV(quad.packedUV(0)),
                                UVPair.unpackU(quad.packedUV(1)), UVPair.unpackV(quad.packedUV(1)),
                                UVPair.unpackU(quad.packedUV(2)), UVPair.unpackV(quad.packedUV(2)),
                                UVPair.unpackU(quad.packedUV(3)), UVPair.unpackV(quad.packedUV(3)));
                        QuadGeometry children = affineUv ? geometry(blockState, seed, 4, quad) : null;
                        int tint = 0xFFFFFF;
                        if (quad.materialInfo().isTinted()) {
                            var tintSource = client.getBlockColors().getTintSource(blockState, quad.materialInfo().tintIndex());
                            if (tintSource != null) {
                                tint = tintSource.colorInWorld(blockState, level, position);
                            }
                        }
                        for (int row = 0; row < divisions; row++) {
                            for (int column = 0; column < divisions; column++) {
                                if (++testedPatches > MAX_TESTED_PATCHES || work.testedPatches >= work.quota.testedPatches()) {
                                    break outer;
                                }
                                work.testedPatches++;
                                SurfacePatch patch = patch(visibility, beams, position, quad, normal, tint,
                                        geometry.patches[row * divisions + column], work);
                                if (patch != null) work.patches++;
                                if (children != null && work.detail != null && work.detail.canRefine(beams.size())
                                        && HeadlightRefinement.affine(geometry.patches[row * divisions + column])) {
                                    // Complete all four child tests before replacing the parent.
                                    // Even fully occluded children replace it: no ghost fallback.
                                    for (int childRow = 0; childRow < 2; childRow++) {
                                        for (int childColumn = 0; childColumn < 2; childColumn++) {
                                            work.detail.testedPatches++;
                                            var child = patch(visibility, beams, position, quad, normal, tint,
                                                    children.patches[(row * 2 + childRow) * 4 + column * 2 + childColumn], work.detail);
                                            if (child != null) {
                                                patches.add(child.refinedFrom(patch, childColumn, childRow));
                                                work.detail.patches++;
                                            }
                                        }
                                    }
                                } else if (patch != null) {
                                    patches.add(patch);
                                }
                                if (work.patches >= work.quota.patches()) break outer;
                            }
                        }
                    }
                }
            }
        }
        return new SurfaceCache(tick, topology, List.copyOf(patches));
    }

    private static void addHit(ClientLevel level, HeadlightVisibilityCache visibility, Vec3 from, Vec3 to, Set<BlockPos> blocks, MainWork work) {
        if (work.discoveryRays >= work.quota.discoveryRays()) return;
        work.discoveryRays++;
        var hit = visibility.clip(from, to);
        if (hit.getType() == HitResult.Type.BLOCK && blocks.size() < MAX_BLOCKS && level.isLoaded(hit.getBlockPos())) {
            blocks.add(hit.getBlockPos().immutable());
        }
    }

    private static boolean exposedBlock(BlockFacts facts, BlockPos position) {
        BlockFact current = facts.at(position);
        if (!current.loaded) return false;
        var state = current.state;
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        if (!state.isSolidRender()) return true;
        for (Direction direction : DIRECTIONS) {
            BlockFact neighbor = facts.at(position.relative(direction));
            if (neighbor.loaded && !neighbor.state.isSolidRender()) return true;
        }
        return false;
    }

    private static QuadGeometry geometry(BlockState state, long seed, int divisions, BakedQuad quad) {
        GeometryKey key = new GeometryKey(state, seed, divisions, quad);
        QuadGeometry cached = GEOMETRY_CACHE.get(key);
        if (cached != null) return cached;
        Vec3 normal = new Vec3(quad.position1()).subtract(new Vec3(quad.position0()))
                .cross(new Vec3(quad.position3()).subtract(new Vec3(quad.position0()))).normalize();
        if (normal.dot(new Vec3(quad.direction().getUnitVec3f())) < 0) normal = normal.reverse();
        HeadlightPatchGeometry[] patches = new HeadlightPatchGeometry[normal.lengthSqr() < 0.5 ? 0 : divisions * divisions];
        for (int row = 0; row < divisions && patches.length > 0; row++) {
            for (int column = 0; column < divisions; column++) {
                patches[row * divisions + column] = HeadlightPatchGeometry.create(
                        quad.position0(), quad.position1(), quad.position2(), quad.position3(), column, row, divisions);
            }
        }
        cached = new QuadGeometry(normal, patches);
        GEOMETRY_CACHE.put(key, cached, 1 + patches.length);
        return cached;
    }

    private static Vec3 facingNormal(List<HeadlightBeam> beams, BlockPos position, BakedQuad quad, Vec3 normal) {
        if (normal.lengthSqr() < 0.5) return null;
        double x = HeadlightPatchMath.world(quad.position0().x(), position.getX(), normal.x, SURFACE_OFFSET);
        double y = HeadlightPatchMath.world(quad.position0().y(), position.getY(), normal.y, SURFACE_OFFSET);
        double z = HeadlightPatchMath.world(quad.position0().z(), position.getZ(), normal.z, SURFACE_OFFSET);
        for (HeadlightBeam beam : beams) {
            if (HeadlightPatchMath.facing(x, y, z, beam.origin(), normal) < 0) return normal;
        }
        return null;
    }

    private static SurfacePatch patch(HeadlightVisibilityCache visibility, List<HeadlightBeam> beams,
                                      BlockPos position, BakedQuad quad, Vec3 normal, int tint,
                                      HeadlightPatchGeometry geometry, MainWork work) {
        if (PREPARED_ENABLED && work.visibilityRays >= work.quota.visibilityRays()) {
            exhaustedPatches++;
            return null; // Original path cannot accept another lamp either.
        }
        double x0 = HeadlightPatchMath.world(geometry.x(0), position.getX(), normal.x, SURFACE_OFFSET);
        double y0 = HeadlightPatchMath.world(geometry.y(0), position.getY(), normal.y, SURFACE_OFFSET);
        double z0 = HeadlightPatchMath.world(geometry.z(0), position.getZ(), normal.z, SURFACE_OFFSET);
        double x1 = HeadlightPatchMath.world(geometry.x(1), position.getX(), normal.x, SURFACE_OFFSET);
        double y1 = HeadlightPatchMath.world(geometry.y(1), position.getY(), normal.y, SURFACE_OFFSET);
        double z1 = HeadlightPatchMath.world(geometry.z(1), position.getZ(), normal.z, SURFACE_OFFSET);
        double x2 = HeadlightPatchMath.world(geometry.x(2), position.getX(), normal.x, SURFACE_OFFSET);
        double y2 = HeadlightPatchMath.world(geometry.y(2), position.getY(), normal.y, SURFACE_OFFSET);
        double z2 = HeadlightPatchMath.world(geometry.z(2), position.getZ(), normal.z, SURFACE_OFFSET);
        double x3 = HeadlightPatchMath.world(geometry.x(3), position.getX(), normal.x, SURFACE_OFFSET);
        double y3 = HeadlightPatchMath.world(geometry.y(3), position.getY(), normal.y, SURFACE_OFFSET);
        double z3 = HeadlightPatchMath.world(geometry.z(3), position.getZ(), normal.z, SURFACE_OFFSET);
        double centerX = HeadlightPatchMath.center(x0, x1, x2, x3);
        double centerY = HeadlightPatchMath.center(y0, y1, y2, y3);
        double centerZ = HeadlightPatchMath.center(z0, z1, z2, z3);
        Vec3 center = null;
        int visibleLamps = 0;
        for (int i = 0; i < beams.size(); i++) {
            if (PREPARED_ENABLED && work.visibilityRays >= work.quota.visibilityRays()) break;
            HeadlightBeam beam = beams.get(i);
            if (patchBelowThreshold(beam, centerX, centerY, centerZ,
                    x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3)
                    || HeadlightPatchMath.facing(centerX, centerY, centerZ, beam.origin(), normal) >= 0) continue;
            if (work.visibilityRays >= work.quota.visibilityRays()) continue;
            work.visibilityRays++;
            if (center == null) center = new Vec3(centerX, centerY, centerZ);
            var hit = visibility.clipOrNullOnTraversalMiss(beam.origin(), center);
            if (hit == null || hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(center) < 0.0001) {
                visibleLamps |= 1 << i;
            }
        }
        if (visibleLamps == 0) return null;
        // Texture arrays and the final vertex array are created only for an accepted patch.
        var uv = geometry.uv();
        if (uv == null) uv = geometry.uv(UVPair.unpackU(quad.packedUV(0)), UVPair.unpackV(quad.packedUV(0)),
                UVPair.unpackU(quad.packedUV(1)), UVPair.unpackV(quad.packedUV(1)),
                UVPair.unpackU(quad.packedUV(2)), UVPair.unpackV(quad.packedUV(2)),
                UVPair.unpackU(quad.packedUV(3)), UVPair.unpackV(quad.packedUV(3)));
        return new SurfacePatch(quad.materialInfo().sprite().atlasLocation(), new Vec3[] {
                new Vec3(x0, y0, z0), new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), new Vec3(x3, y3, z3)},
                uv.u(), uv.v(), normal, center, tint, visibleLamps);
    }

    private static float rawIntensity(SurfacePatch patch, List<HeadlightBeam> beams, int vertex, int allowed) {
        float result = 0;
        for (int lamp = 0; lamp < beams.size(); lamp++) {
            if ((patch.visibleLamps & allowed & (1 << lamp)) != 0)
                result = Math.max(result, beams.get(lamp).intensity(patch.vertices[vertex]));
        }
        return result;
    }

    /** Same max-of-five threshold decision, without evaluating samples after it is decided. */
    static boolean patchBelowThreshold(HeadlightBeam beam, double centerX, double centerY, double centerZ,
                                       double x0, double y0, double z0, double x1, double y1, double z1,
                                       double x2, double y2, double z2, double x3, double y3, double z3) {
        // Keep < rather than >=: a NaN sample also made the original maximum fail <.
        return beam.intensity(centerX, centerY, centerZ) < MIN_DRAW_INTENSITY
                && beam.intensity(x0, y0, z0) < MIN_DRAW_INTENSITY
                && beam.intensity(x1, y1, z1) < MIN_DRAW_INTENSITY
                && beam.intensity(x2, y2, z2) < MIN_DRAW_INTENSITY
                && beam.intensity(x3, y3, z3) < MIN_DRAW_INTENSITY;
    }

    private static float intensityAt(SurfacePatch patch, List<HeadlightBeam> beams, int vertex, float refinement,
                                     Map<SurfacePatch, float[]> parentFields) {
        float fine = rawIntensity(patch, beams, vertex, patch.visibleLamps);
        if (patch.childColumn < 0 || refinement >= 1) return fine;
        var parent = patch.parent;
        // A child's own fresh mask also gates its parent field. A blocked lamp
        // cannot leak in through interpolation from a different visible source.
        float coarse = 0;
        if (parent != null) {
            float[] field = parentFields.computeIfAbsent(parent, unused -> {
                float[] values = new float[beams.size() * 4];
                for (int lamp = 0; lamp < beams.size(); lamp++) for (int corner = 0; corner < 4; corner++)
                    values[lamp * 4 + corner] = rawIntensity(parent, beams, corner, 1 << lamp);
                return values;
            });
            float a = 0, b = 0, c = 0, d = 0;
            for (int lamp = 0; lamp < beams.size(); lamp++) if ((patch.visibleLamps & (1 << lamp)) != 0) {
                a = Math.max(a, field[lamp * 4]); b = Math.max(b, field[lamp * 4 + 1]);
                c = Math.max(c, field[lamp * 4 + 2]); d = Math.max(d, field[lamp * 4 + 3]);
            }
            coarse = HeadlightRefinement.child(a, b, c, d, patch.childColumn, patch.childRow, vertex);
        }
        return HeadlightRefinement.blend(coarse, fine, refinement);
    }

    private static void emit(LitBatch patches, Vec3 entityOrigin, PoseStack.Pose pose, VertexConsumer buffer) {
        Matrix4f viewPose = ComplementaryTemporalProjection.apply(HeadlightViewLayering.apply(pose.pose()));
        for (int patchIndex = 0; patchIndex < patches.size; patchIndex++) {
            SurfacePatch patch = patches.patches[patchIndex];
            // Original UVs and biome tint preserve road detail; a slight warm tint reads as a lamp.
            int red = (patch.tint >> 16) & 255;
            int green = (int)(((patch.tint >> 8) & 255) * 0.97);
            int blue = (int)((patch.tint & 255) * 0.88);
            for (int i = 0; i < 4; i++) {
                float intensity = patches.intensities[patchIndex * 4 + i];
                int alpha = Math.min(200, Math.max(0, (int)(intensity * 255)));
                Vec3 p = patch.vertices[i];
                buffer.addVertex(viewPose, (float)(p.x - entityOrigin.x),
                                (float)(p.y - entityOrigin.y), (float)(p.z - entityOrigin.z))
                        .setColor(red, green, blue, alpha).setUv(patch.u[i], patch.v[i])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                        .setNormal(pose, (float)patch.normal.x, (float)patch.normal.y, (float)patch.normal.z);
            }
        }
    }

    private record SurfaceCache(long tick, int topology, List<SurfacePatch> patches) {}
    private record SurfacePatch(Identifier atlas, Vec3[] vertices, float[] u, float[] v, Vec3 normal,
                                Vec3 center, int tint, int visibleLamps, SurfacePatch parent, int childColumn, int childRow) {
        private SurfacePatch(Identifier atlas, Vec3[] vertices, float[] u, float[] v, Vec3 normal,
                             Vec3 center, int tint, int visibleLamps) {
            this(atlas, vertices, u, v, normal, center, tint, visibleLamps, null, -1, -1);
        }
        private SurfacePatch refinedFrom(SurfacePatch parent, int column, int row) {
            return new SurfacePatch(atlas, vertices, u, v, normal, center, tint, visibleLamps, parent, column, row);
        }
    }
    private record AtlasCapacity(SurfaceCache source, Map<Identifier, Integer> counts) {}
    private record BroadCandidate(AutomobileEntity car, double score) {}
    /** Opt-in world adapter. Never resets original WORK or changes collection/emit algorithms. */
    private static final class ParkedRefresh {
        private record Stamp(SurfaceCache cache,List<HeadlightBeam> beams,long epoch,double time,long frame,boolean certified,
                             HeadlightParkedCacheKey persistentKey,HeadlightDelegateBindings modelBindings,Object frameSource,
                             HeadlightTerrainCertificate.Snapshot region){}
        private static final Map<AutomobileEntity,Stamp> stamps=new WeakHashMap<>();
        private static WeakReference<ClientLevel> world=new WeakReference<>(null);
        private static Object models,terrain;
        private static VehicleLightQuality quality;
        private static long terrainGeneration,terrainRevision,epoch,serial,firstTick=Long.MIN_VALUE,lastEpochTick,policyTick;
        private static long selectionTick=Long.MIN_VALUE,plannedFrame=Long.MIN_VALUE;
        private static Object selectionModels;
        private static List<WeakReference<AutomobileEntity>> sources=List.of();
        private static HeadlightRefreshPolicy policy;
        private static HeadlightRefreshPolicy.Frame plan;
        private static Map<java.util.UUID,List<HeadlightBeam>> plannedBeams=Map.of();
        private static double frameTime;
        private static long frameEpoch;
        private static boolean warmed;
        private static int frameCollects,maxWarmCollects;
        private static long collects,reuses,normalDue,warmups,forced,legacy,certifiedBuilds,rejectedBuilds,warmFrames,warmMultiFrames;
        private static final Map<String,Long> rejections=new java.util.LinkedHashMap<>();
        private static final Map<java.util.UUID,String> firstIneligible=new java.util.LinkedHashMap<>();
        private static long persistentReuse,persistentFallback,delegateRevocations;
        private static long regionalCertified,regionalComparisons,regionalRejected;
        private static HeadlightMovingRefreshPolicy moving;
        private static final Set<AutomobileEntity> movingCollecting=java.util.Collections.newSetFromMap(new WeakHashMap<>());
        static void beginFrame(){
            if(warmed){warmFrames++;maxWarmCollects=Math.max(maxWarmCollects,frameCollects);if(frameCollects>1)warmMultiFrames++;}
            frameCollects=0;warmed=false;serial++;
        }
        static String snapshot(){
            return "serial="+serial+" collects="+collects+" reuse="+reuses+" normalDue="+normalDue+" warmup="+warmups
                    +" forced="+forced+" legacy="+legacy+" certified="+certifiedBuilds+" rejected="+rejectedBuilds
                    +" warmFrames="+warmFrames+" maxWarmCollects="+maxWarmCollects+" warmMultiFrames="+warmMultiFrames
                    +" rejections="+rejections+" warmupWork="+(policy==null?"none":policy.ledger().warmupSpent())
                    +" firstRevocations="+(policy==null?"none":policy.revocationSnapshot())+" firstIneligible="+firstIneligible
                    +" persistentReuse="+persistentReuse+" persistentFallback="+persistentFallback+" delegateRevocations="+delegateRevocations
                    +" regionalEnabled="+REGIONAL_TERRAIN+" regionalCertified="+regionalCertified+" regionalComparisons="+regionalComparisons+" regionalRejected="+regionalRejected
                    +" "+(moving==null?"movingPhase=inactive":moving.snapshot());
        }
        private static long epoch(Minecraft client,ClientLevel level){
            if(world.get()!=level){
                world=new WeakReference<>(level);stamps.clear();policy=null;plan=null;sources=List.of();
                firstTick=level.getGameTime();selectionTick=plannedFrame=Long.MIN_VALUE;
                models=terrain=null;quality=null;moving=null;epoch=Math.incrementExact(epoch);
            }
            Object currentModels=client.getModelManager().getBlockStateModelSet();
            AutomobileTerrainRevisions currentTerrain=(Object)level instanceof AutomobileTerrainRevisions.Source source?source.automobility$terrainRevisions():null;
            long generation=currentTerrain==null?-1:currentTerrain.generation(),revision=currentTerrain==null?-1:currentTerrain.revision();
            if(models!=currentModels || terrain!=currentTerrain || terrainGeneration!=generation || terrainRevision!=revision || quality!=selectedQuality){
                models=currentModels;terrain=currentTerrain;terrainGeneration=generation;terrainRevision=revision;quality=selectedQuality;
                lastEpochTick=level.getGameTime();
                epoch=Math.incrementExact(epoch);
            }
            return epoch;
        }
        private static boolean parked(AutomobileEntity car){return HeadlightProjectionMotion.reuseCandidate(
                car.getClass()==AutomobileEntity.class,car.engineRunning(),car.isPassenger(),STABLE_ENGINE_PROJECTION && PERSISTENT_PARKED);}
        private static List<HeadlightBeam> currentBeams(Minecraft client,AutomobileEntity car){
            if(!parked(car))return List.of();
            float partial=client.getDeltaTracker().getGameTimeDeltaPartialTick(!car.level().tickRateManager().isEntityFrozen(car));
            var state=AutomobileEntityRenderer.headlightCullingState(car,partial);
            return state==null?List.of():beams(state);
        }
        private static boolean stable(AutomobileEntity car,SurfaceCache cached,Stamp stamp,List<HeadlightBeam> actual){
            return terrain!=null && parked(car) && !actual.isEmpty() && stamp!=null && stamp.cache==cached
                    && stamp.certified && stamp.modelBindings.stillValid() && stamp.beams.equals(actual);
        }
        private static SurfaceCache invalidateChangedDelegates(AutomobileEntity car,SurfaceCache cached){
            var stamp=stamps.get(car);
            if(stamp!=null && stamp.certified && !stamp.modelBindings.stillValid()){
                // Legacy same-tick freshness must not return the revoked geometry either.
                stamps.remove(car);CACHES.remove(car);delegateRevocations++;
                if(policy!=null)policy.revoke(car.getUUID(),"MODEL_DELEGATE_CHANGED");
                return null;
            }
            return cached;
        }
        static SurfaceCache persistentSurface(Minecraft client,ClientLevel level,AutomobileEntity car,List<HeadlightBeam> actual,
                                              long tick,int topology,SurfaceCache cached){
            cached=invalidateChangedDelegates(car,cached);
            // Arbitrary native model/shape callbacks may reenter. Never spend this owner's
            // still-partially-unused WORK again or take a legacy bypass during its collect.
            if(MOVING_PHASE && movingCollecting.contains(car))return null;
            long currentEpoch=epoch(client,level);
            if(selectionTick!=selectedTick || selectionModels!=selectedModels){
                stamps.keySet().removeIf(item->!SELECTED.contains(item.getId()));
                selectionTick=selectedTick;selectionModels=selectedModels;
                if(MOVING_PHASE)ensureMovingCohort(level);
            }
            var stamp=stamps.get(car);var work=WORK.get(car.getId());
            var quota=work==null?null:work.coverage;
            long reuseComparisonEpoch=currentEpoch;
            if(REGIONAL_TERRAIN && stamp!=null && stamp.region!=null){
                var source=(Object)level instanceof HeadlightTerrainCertificate.Source value?value:null;
                if(!stamp.region.valid(source,actual,models,terrain,selectedQuality)){
                    // A current-pose envelope extension or real local mutation cannot take a same-tick legacy hit.
                    regionalRejected++;stamps.remove(car);CACHES.remove(car);cached=null;stamp=null;
                    if(moving!=null)moving.revoke(car.getUUID());
                    if(policy!=null)policy.revoke(car.getUUID(),"LOCAL_TERRAIN_CERTIFICATE_CHANGED");
                }else{
                    // Only reuse comparisons may use the original collection epoch. Never rewrite actual
                    // global epoch, stamp/key/time/frame, fresh-work accounting or moving refresh age.
                    reuseComparisonEpoch=stamp.epoch;if(currentEpoch!=reuseComparisonEpoch)regionalComparisons++;
                }
            }
            boolean qualified=client.level==level && car.level()==level && level.getClass()==ClientLevel.class
                    && stable(car,cached,stamp,actual);
            // No same-frame bypass: terrain, full base/detail quota and actual pose are always checked.
            if(stamp!=null && stamp.persistentKey!=null && stamp.persistentKey.matches(cached,actual,quota,reuseComparisonEpoch,topology,qualified)){
                if(MOVING_PHASE && moving!=null){
                    if(stamp.frameSource==car.getFrame())moving.exact(car.getUUID(),cached,System.nanoTime(),tick,work!=null && work.unused());
                    else moving.revoke(car.getUUID());
                }
                persistentReuse++;return cached;
            }
            if(MOVING_PHASE && moving!=null){
                var decision=movingDecision(client,level,car,actual,tick,topology,cached,stamp,quota,reuseComparisonEpoch,work);
                if(decision==HeadlightMovingRefreshPolicy.Decision.REUSE)return cached;
                if(decision==HeadlightMovingRefreshPolicy.Decision.REFRESH){
                    // Actual normal WORK only: no warmup/future/extra ledger and no synthetic quota.
                    movingCollecting.add(car);
                    try{
                        var fresh=collect(client,level,car,actual,tick,topology,work);CACHES.put(car,fresh);
                        rememberMovingFresh(client,level,car,actual,tick,topology,cached,fresh,quota);
                        return fresh;
                    }finally{movingCollecting.remove(car);}
                }
            }
            // Invalidations never mint credit or extend stale-cache lifetime. Exact006 freshness/budget.
            persistentFallback++;
            if(MOVING_PHASE && moving!=null){
                movingCollecting.add(car);
                try{
                    var fresh=legacySurfaces(client,level,car,actual,tick,topology,cached);
                    rememberMovingFresh(client,level,car,actual,tick,topology,cached,fresh,quota);
                    return fresh;
                }finally{movingCollecting.remove(car);}
            }
            return legacySurfaces(client,level,car,actual,tick,topology,cached);
        }
        /** New certified paid native work only; a legacy same-token hit never renews age. */
        private static void rememberMovingFresh(Minecraft client,ClientLevel level,AutomobileEntity car,List<HeadlightBeam> actual,
                long tick,int topology,SurfaceCache previous,SurfaceCache fresh,VehicleLightingPolicy.CoverageQuota quota){
            var target=moving;if(target==null)return;
            if(!HeadlightMovingRefreshPolicy.newCache(previous,fresh) || client.level!=level || car.level()!=level){target.revoke(car.getUUID());return;}
            var stamp=stamps.get(car);long currentEpoch=epoch(client,level);
            if(moving!=target)return; // Reentrant world/cohort replacement cannot inherit this completion.
            if(stamp!=null && stamp.frame==serial && fresh.tick==tick && stamp.frameSource==car.getFrame()
                    && stamp.persistentKey!=null
                    && stamp.persistentKey.matches(fresh,actual,quota,currentEpoch,topology,stable(car,fresh,stamp,actual)))
                target.collected(car.getUUID(),fresh,System.nanoTime(),tick);
            else target.revoke(car.getUUID());
        }
        private static void ensureMovingCohort(ClientLevel level){
            var owners=new ArrayList<java.util.UUID>();
            for(var entity:level.entitiesForRendering())if(entity instanceof AutomobileEntity car && SELECTED.contains(car.getId()))owners.add(car.getUUID());
            owners.sort(Comparator.naturalOrder());
            if(owners.isEmpty() || owners.size()>16){moving=null;return;}
            if(moving==null || !moving.owners().equals(owners))moving=new HeadlightMovingRefreshPolicy(owners);
        }
        private static HeadlightMovingRefreshPolicy.Decision movingDecision(Minecraft client,ClientLevel level,AutomobileEntity car,
                List<HeadlightBeam> actual,long tick,int topology,SurfaceCache cached,Stamp stamp,
                VehicleLightingPolicy.CoverageQuota quota,long reuseComparisonEpoch,MainWork work){
            boolean qualified=client.level==level && car.level()==level && level.getClass()==ClientLevel.class
                    && terrain!=null && parked(car) && stamp!=null && stamp.cache==cached && stamp.certified
                    && stamp.frameSource==car.getFrame() && stamp.modelBindings.stillValid() && stamp.persistentKey!=null
                    && stamp.persistentKey.matches(cached,stamp.beams,quota,reuseComparisonEpoch,topology,true)
                    && HeadlightMovingRefreshPolicy.boundedPose(stamp.beams,actual);
            return moving.dirty(car.getUUID(),cached,System.nanoTime(),tick,qualified,work!=null && work.unused() && work.canRebuild());
        }
        private static void prepare(Minecraft client,ClientLevel level){
            long nowEpoch=epoch(client,level);
            if(plannedFrame==serial)return;
            plannedFrame=serial;frameEpoch=nowEpoch;frameTime=VehicleLightingView.presentationTime(client);plan=null;
            warmed=policy!=null && level.getGameTime()>=policyTick+40 && level.getGameTime()>=firstTick+80;
            if(selectionTick!=selectedTick || selectionModels!=selectedModels){
                var selected=new ArrayList<WeakReference<AutomobileEntity>>();
                for(var entity:level.entitiesForRendering())if(entity instanceof AutomobileEntity car && SELECTED.contains(car.getId()))selected.add(new WeakReference<>(car));
                sources=List.copyOf(selected);selectionTick=selectedTick;selectionModels=selectedModels;
            }
            stamps.keySet().removeIf(car->!SELECTED.contains(car.getId()));
            var inputs=new ArrayList<HeadlightRefreshPolicy.Input>();var poses=new HashMap<java.util.UUID,List<HeadlightBeam>>();
            for(var ref:sources){
                var car=ref.get();if(car==null || !SELECTED.contains(car.getId()))continue;
                var work=WORK.get(car.getId());if(work==null)continue;
                var actual=currentBeams(client,car);poses.put(car.getUUID(),actual);
                var cached=CACHES.get(car);var stamp=stamps.get(car);
                boolean eligible=cached==null?terrain!=null && parked(car) && !actual.isEmpty():stable(car,cached,stamp,actual);
                if(!eligible && policy!=null && !policy.isLegacy(car.getUUID()) && firstIneligible.size()<32)
                    firstIneligible.putIfAbsent(car.getUUID(),"frame="+serial+" terrain="+(terrain!=null)+" parked="+parked(car)
                            +" empty="+actual.isEmpty()+" cache="+(cached!=null)+" stamp="+(stamp!=null)
                            +" sameCache="+(stamp!=null && stamp.cache==cached)+" cert="+(stamp!=null && stamp.certified)
                            +" sameBeams="+(stamp!=null && stamp.beams.equals(actual)));
                var quota=new VehicleLightingPolicy.CoverageQuota(work.quota,work.detail.quota);
                inputs.add(new HeadlightRefreshPolicy.Input(car.getUUID(),eligible,cached!=null,cached,
                        stamp==null?-1:stamp.epoch,nowEpoch,stamp==null?Double.NaN:stamp.time,work.canRebuild(),quota));
            }
            plannedBeams=Map.copyOf(poses);
            // Preserve immediate baseline cold coverage and let popup/terrain initialization settle.
            // This is startup eligibility, not a recurring cache-age extension.
            if(policy==null && (level.getGameTime()<firstTick+40 || level.getGameTime()<lastEpochTick+40))return;
            if(policy==null && !inputs.isEmpty()){
                var envelope=HeadlightRefreshWork.of(VehicleLightingPolicy.mainBudget(VehicleLightQuality.HIGH));
                policy=new HeadlightRefreshPolicy(inputs.stream().map(HeadlightRefreshPolicy.Input::owner).toList(),envelope,envelope);
                policyTick=level.getGameTime();
            }
            if(policy!=null)plan=policy.plan(serial,level.getGameTime(),frameTime,inputs);
        }
        static SurfaceCache surface(Minecraft client,ClientLevel level,AutomobileEntity car,List<HeadlightBeam> actual,
                                    long tick,int topology,SurfaceCache cached){
            cached=invalidateChangedDelegates(car,cached);
            prepare(client,level);
            long currentEpoch=epoch(client,level);var stamp=stamps.get(car);
            // Repeated native submissions in one frame cannot execute the same grant twice.
            if(stamp!=null && stamp.cache==cached && stamp.frame==serial && stamp.epoch==currentEpoch
                    && stamp.modelBindings.stillValid() && stamp.beams.equals(actual) && cached.topology==topology)return cached;
            if(plan==null){legacy++;return legacySurfaces(client,level,car,actual,tick,topology,cached);}
            var d=plan.decisions().get(car.getUUID());
            boolean eligible=d!=null && !policy.isLegacy(car.getUUID()) && currentEpoch==frameEpoch
                    && actual.equals(plannedBeams.get(car.getUUID())) && cached!=null && cached.topology==topology
                    && stable(car,cached,stamp,actual);
            double actualTime=VehicleLightingView.presentationTime(client);
            if(d!=null && d.allowsReuse(serial,actualTime,cached,currentEpoch,eligible && client.level==level)){reuses++;return cached;}
            if(d==null || d.reason()==HeadlightRefreshPolicy.Reason.LEGACY || d.reason()==HeadlightRefreshPolicy.Reason.LEGACY_STICKY){
                legacy++;return legacySurfaces(client,level,car,actual,tick,topology,cached);
            }
            boolean cold=d.reason()==HeadlightRefreshPolicy.Reason.NORMAL_COLD;
            boolean valid=client.level==level && Double.isFinite(actualTime) && actualTime>=plan.time() && level.getGameTime()==plan.tick()
                    && currentEpoch==d.requiredEpoch() && d.cacheToken()==cached && actual.equals(plannedBeams.get(car.getUUID()))
                    && (cold?cached==null && terrain!=null && parked(car):eligible);
            if(!valid || d.reason()==HeadlightRefreshPolicy.Reason.REUSE_PHASE){
                policy.revoke(car.getUUID(),"ADAPTER_GUARD frame="+serial+" reason="+d.reason()+" world="+(client.level==level)
                        +" time="+actualTime+" planTime="+plan.time()+" tick="+level.getGameTime()+" planTick="+plan.tick()
                        +" epoch="+currentEpoch+" required="+d.requiredEpoch()+" cache="+(d.cacheToken()==cached)
                        +" plannedBeams="+actual.equals(plannedBeams.get(car.getUUID()))+" eligible="+eligible);
                legacy++;return legacySurfaces(client,level,car,actual,tick,topology,cached);
            }
            MainWork work;
            if(d.grant()!=null){
                if(!policy.ledger().claim(d.grant(),serial,currentEpoch)){
                    policy.revoke(car.getUUID(),"CLAIM_FAILED");legacy++;return legacySurfaces(client,level,car,actual,tick,topology,cached);
                }
                work=new MainWork(d.grant().quota());
                if(d.reason()==HeadlightRefreshPolicy.Reason.WARMUP)warmups++;else forced++;
            }else{work=WORK.get(car.getId());normalDue++;}
            if(work==null || !work.canRebuild()){
                policy.revoke(car.getUUID(),"WORK_UNAVAILABLE");legacy++;return legacySurfaces(client,level,car,actual,tick,topology,cached);
            }
            cached=collect(client,level,car,actual,tick,topology,work);CACHES.put(car,cached);return cached;
        }
        static SurfaceCache collect(Minecraft client,ClientLevel level,AutomobileEntity car,List<HeadlightBeam> actual,
                                    long tick,int topology,MainWork work){
            long before=epoch(client,level);double time=VehicleLightingView.presentationTime(client);
            var source=REGIONAL_TERRAIN && (Object)level instanceof HeadlightTerrainCertificate.Source value?value:null;
            var region=REGIONAL_TERRAIN?HeadlightTerrainCertificate.capture(source,actual,models,terrain,selectedQuality):null;
            var scope=new HeadlightStaticSurfaceScope(level::isLoaded,region==null?null:region::containsChunk);
            var actualQuota=work.coverage;boolean unusedWork=work.unused();
            if(!parked(car) || level.getClass()!=ClientLevel.class || terrain==null){
                frameCollects++;collects++;
                var result=collectSurfaces(client,level,car,actual,tick,topology,work);
                stamps.put(car,new Stamp(result,List.copyOf(actual),before,time,serial,false,null,HeadlightDelegateBindings.EMPTY,car.getFrame(),null));return result;
            }
            scope.enter(level);frameCollects++;collects++;
            SurfaceCache result;
            try{result=collectSurfaces(client,level,car,actual,tick,topology,work);}finally{scope.exit();}
            scope.certifyModels(state->client.getModelManager().getBlockStateModelSet().get(state));
            var modelBindings=scope.modelBindings();
            if(modelBindings==null){
                // A mutation during collection cannot become a same-tick legacy result.
                stamps.remove(car);CACHES.remove(car);delegateRevocations++;rejectedBuilds++;
                if(policy!=null)policy.revoke(car.getUUID(),"MODEL_CHANGED_DURING_COLLECT");
                return null;
            }
            double finishedTime=VehicleLightingView.presentationTime(client);
            boolean certified=scope.valid() && client.level==level && Double.isFinite(time) && finishedTime>=time && level.getGameTime()==tick
                    && before==epoch(client,level) && parked(car) && actual.equals(currentBeams(client,car));
            if(certified)certifiedBuilds++;
            else{
                rejectedBuilds++;if(rejections.size()<16 || rejections.containsKey(scope.reason()))rejections.merge(scope.reason(),1L,Long::sum);
                if(policy!=null)policy.revoke(car.getUUID(),"CERT_REJECT "+scope.reason());
            }
            var persistentKey=PERSISTENT_PARKED && certified && unusedWork && actualQuota!=null
                    ?new HeadlightParkedCacheKey(result,actual,actualQuota,before,topology):null;
            if(!certified || !scope.localEnvelopeValid() || region!=null && !region.valid(source,actual,models,terrain,selectedQuality))region=null;
            if(region!=null)regionalCertified++;
            stamps.put(car,new Stamp(result,List.copyOf(actual),before,time,serial,certified,persistentKey,
                    certified?modelBindings:HeadlightDelegateBindings.EMPTY,car.getFrame(),region));
            return result;
        }
    }
    private static final class MainWork {
        private final VehicleLightingPolicy.MainQuota quota;
        private final MainWork detail;
        private final VehicleLightingPolicy.CoverageQuota coverage;
        private int discoveryRays, candidates, blocks, modelQuads, testedPatches, patches, visibilityRays;
        private MainWork(VehicleLightingPolicy.MainQuota quota) { this.quota = quota; this.detail = null; this.coverage=null; }
        private MainWork(VehicleLightingPolicy.CoverageQuota quota) {
            this.quota = quota.base(); this.detail = new MainWork(quota.detail()); this.coverage=quota;
        }
        private boolean unused(){return discoveryRays==0 && candidates==0 && blocks==0 && modelQuads==0
                && testedPatches==0 && patches==0 && visibilityRays==0 && (detail==null || detail.unused());}
        private boolean canRefine(int lamps) {
            return HeadlightRefinement.canReplace(quota.testedPatches() - testedPatches,
                    quota.patches() - patches, quota.visibilityRays() - visibilityRays, lamps);
        }
        private boolean canRebuild() {
            return quota.discoveryRays() - discoveryRays > FAN_RAYS && candidates < quota.candidates()
                    && blocks < quota.blocks() && modelQuads < quota.modelQuads()
                    && testedPatches < quota.testedPatches() && patches < quota.patches()
                    && visibilityRays < quota.visibilityRays();
        }
    }
    /** Independent storage for each submission; never pooled across callbacks or render passes. */
    private static final class LitBatch {
        private final SurfacePatch[] patches;
        private final float[] intensities;
        private int size;

        private LitBatch(int capacity) {
            patches = new SurfacePatch[capacity];
            intensities = new float[capacity * 4];
        }

        private void add(SurfacePatch patch, float i0, float i1, float i2, float i3) {
            patches[size] = patch;
            int index = size++ * 4;
            intensities[index] = i0; intensities[index + 1] = i1;
            intensities[index + 2] = i2; intensities[index + 3] = i3;
        }
    }

    private record QuadGeometry(Vec3 normal, HeadlightPatchGeometry[] patches) {}
    private record GeometryKey(BlockState state, long seed, int divisions, BakedQuad quad) {
        @Override public boolean equals(Object other) {
            return other instanceof GeometryKey key && state == key.state && seed == key.seed
                    && divisions == key.divisions && quad == key.quad;
        }
        @Override public int hashCode() {
            int hash = 31 * System.identityHashCode(state) + Long.hashCode(seed);
            return 31 * (31 * hash + divisions) + System.identityHashCode(quad);
        }
    }

    private record BlockFact(boolean loaded, BlockState state) {}
    /** A finite, rebuild-local snapshot; overflow bypasses storage rather than skipping a block. */
    private static final class BlockFacts {
        private static final int MAX_ENTRIES = HeadlightSurfaceDiscovery.MAX_CANDIDATES * 4;
        private final ClientLevel level;
        private final Long2ObjectOpenHashMap<BlockFact> entries = new Long2ObjectOpenHashMap<>();

        private BlockFacts(ClientLevel level) { this.level = level; }

        private BlockFact at(BlockPos position) {
            long key = position.asLong();
            BlockFact fact = entries.get(key);
            if (fact == null) {
                boolean loaded = level.isLoaded(position);
                fact = new BlockFact(loaded, loaded ? level.getBlockState(position) : null);
                HeadlightStaticSurfaceScope.observe(level,position,fact.state);
                if (entries.size() < MAX_ENTRIES) entries.put(key, fact);
            }
            return fact;
        }

        private int faceMask(BlockPos position, BlockState state) {
            int mask = 0;
            for (int face = 0; face < DIRECTIONS.length; face++) {
                Direction direction = DIRECTIONS[face];
                BlockFact neighbor = at(position.relative(direction));
                if (!neighbor.loaded || Block.shouldRenderFace(state, neighbor.state, direction)) mask |= 1 << face;
            }
            return mask;
        }
    }
}
