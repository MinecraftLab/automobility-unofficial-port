package io.github.foundationgames.automobility.client.lighting;

import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;
import io.github.foundationgames.automobility.automobile.light.VehicleSignalMode;
import io.github.foundationgames.automobility.automobile.light.Mx5VisualPose;
import io.github.foundationgames.automobility.automobile.light.Mx5PopupGeometry;
import io.github.foundationgames.automobility.client.vehicle.VehicleLightQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Bounded, short-range coloured diffuse surface lighting; never modifies world block light. */
public final class VehicleSmallLampLighting {
    public static final int WHITE = 0xFFF5DC;
    public static final int RED = 0xFF2830;
    public static final int AMBER = 0xFFAE24;
    public static final int REVERSE_WHITE = 0xFFF8EE;
    public static final float MAX_INTENSITY = 0.30f;
    public static final int MAX_SOURCES = 10;
    private static final double MAX_LOCAL_SOURCE_DISTANCE = 8;
    private static final Budget ECONOMY = new Budget(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    private static final Budget LIMITED = new Budget(2.5, 2, 2048, 512, 64, 512, 512, 160, 1024, 2, 48);
    private static final Budget BALANCED = new Budget(3.0, 2, 3072, 704, 88, 704, 704, 208, 1280, 2, 56);
    private static final Budget HIGH = new Budget(3.5, 3, 4096, 896, 112, 896, 896, 256, 1536, 3, 64);

    private VehicleSmallLampLighting() {}

    public static Budget budget(VehicleLightQuality quality) {
        return switch (Objects.requireNonNull(quality)) {
            case POTATO -> ECONOMY;
            case ECONOMY -> LIMITED;
            case BALANCED -> BALANCED;
            case HIGH -> HIGH;
        };
    }

    /** Spend HIGH's extra detail near lamps, leaving enough work for the dim outer footprint. */
    public static int divisions(Budget budget, List<Light> lights, BlockPos cell) {
        if (budget.divisions() <= 2) return budget.divisions();
        Vec3 center = Vec3.atCenterOf(cell);
        double distanceSquared = Double.POSITIVE_INFINITY;
        for (Light light : lights) {
            if (light.enabled()) distanceSquared = Math.min(distanceSquared, center.distanceToSqr(light.origin()));
        }
        return distanceSquared <= 1.6 * 1.6 ? 3 : distanceSquared <= 2.6 * 2.6 ? 2 : 1;
    }

    /** Per-car rebuild limits. Multiplying by maxCars bounds the entire selected fleet per tick. */
    public record Budget(double radius, int maxCars, int maxCandidateVisits, int maxCandidates,
                         int maxBlocks, int maxModelQuads, int maxTestedPatches, int maxPatches,
                         int maxRays, int divisions, double maxCameraDistance) {
        public int maxGlobalPatches() { return maxCars * maxPatches; }
        public int maxGlobalRays() { return maxCars * maxRays; }
        public int maxGlobalCandidateVisits() { return maxCars * maxCandidateVisits; }
    }

    public enum Kind { FRONT_WHITE, REAR_RED, SIGNAL_AMBER, BRAKE_RED, REVERSE_WHITE }

    public record Light(Vec3 origin, Vec3 normal, double radius, Kind kind, boolean enabled, boolean active) {
        public Light {
            Objects.requireNonNull(origin);
            Objects.requireNonNull(normal);
            Objects.requireNonNull(kind);
            if (!origin.isFinite() || !normal.isFinite() || Math.abs(normal.lengthSqr() - 1) > 0.00001
                    || !Double.isFinite(radius) || radius <= 0 || radius > 3.5) {
                throw new IllegalArgumentException("Invalid bounded small lamp");
            }
            if (active && !enabled) {
                throw new IllegalArgumentException("An inactive source footprint cannot emit light");
            }
        }

        public int color() {
            return switch (kind) {
                case FRONT_WHITE -> WHITE;
                case REAR_RED, BRAKE_RED -> RED;
                case SIGNAL_AMBER -> AMBER;
                case REVERSE_WHITE -> VehicleSmallLampLighting.REVERSE_WHITE;
            };
        }

        /** Independent of active phase so immutable geometry/visibility can be shared through blinking. */
        public float potentialIntensity(Vec3 point) {
            Vec3 delta = point.subtract(origin);
            double squared = delta.lengthSqr();
            if (!point.isFinite() || squared >= radius * radius || delta.dot(normal) < -0.04) return 0;
            double distance = Math.sqrt(squared), t = distance / radius;
            double falloff = 1 - t * t * (3 - 2 * t);
            // A broad outward lobe also gently illuminates the ground directly under an end lamp.
            double cosine = distance < 0.00001 ? 1 : Math.max(0, delta.dot(normal) / distance);
            double angular = 0.35 + 0.65 * Math.min(1, cosine / 0.6);
            float strength = switch (kind) {
                case FRONT_WHITE -> .13f;
                case REAR_RED -> .18f;
                case SIGNAL_AMBER -> .24f;
                case BRAKE_RED -> .26f;
                case REVERSE_WHITE -> .18f;
            };
            return (float)(falloff * angular * strength);
        }
    }

    /**
     * Source order never depends on mode/phase. The enabled footprint remains identical through a blink's two phases.
     * Explicit physical layouts supply all positions; unverified inferred headlights supply no faces.
     */
    public static List<Light> sources(List<VehicleHeadlightLayout.LampFace> front,
                                      List<VehicleHeadlightLayout.LampFace> rear,
                                      VehicleTurnSignalLayout signals, Matrix4f chassis, Vec3 worldOrigin,
                                      VehicleLampSnapshot state, double radius) {
        return sources(front, rear, signals, List.of(), chassis, worldOrigin, state, radius);
    }

    public static List<Light> sources(List<VehicleHeadlightLayout.LampFace> front,
                                      List<VehicleHeadlightLayout.LampFace> rear,
                                      VehicleTurnSignalLayout signals, List<VehicleHeadlightLayout.LampFace> reverse,
                                      Matrix4f chassis, Vec3 worldOrigin, VehicleLampSnapshot state, double radius) {
        return sources(front, rear, signals, reverse, chassis, worldOrigin, state, radius, null);
    }

    public static List<Light> sources(List<VehicleHeadlightLayout.LampFace> front,
                                      List<VehicleHeadlightLayout.LampFace> rear, VehicleTurnSignalLayout signals,
                                      List<VehicleHeadlightLayout.LampFace> reverse, Matrix4f chassis, Vec3 worldOrigin,
                                      VehicleLampSnapshot state, double radius, Mx5VisualPose popupPose) {
        if (radius == 0) return List.of();
        if (front.size() > 2 || rear.size() > 2 || reverse.size() > 2) throw new IllegalArgumentException("Too many lamp faces");
        List<Light> result = new ArrayList<>(MAX_SOURCES);
        // Existing LOW/HIGH projections supply front white light; never add a second broad white spotlight.
        if (popupPose == null) add(result, front, chassis, worldOrigin, radius, Kind.FRONT_WHITE,
                state.lightMode() == VehicleLightMode.POSITION, state.lightMode() == VehicleLightMode.POSITION);
        else for (var face : front) {
            boolean left = face.center().x < 0;
            boolean position = popupPose.lensMode(left) == VehicleLightMode.POSITION;
            addFace(result, face, Mx5PopupGeometry.chassis(chassis, popupPose.progress(left)), worldOrigin,
                    radius, Kind.FRONT_WHITE, position, position);
        }
        boolean rearEnabled = state.brakeLit() || state.lightMode().hasPositionLights();
        add(result, rear, chassis, worldOrigin, radius, state.brakeLit() ? Kind.BRAKE_RED : Kind.REAR_RED, rearEnabled, rearEnabled);
        addSignals(result, signals.frontFaces(), chassis, worldOrigin, radius, state);
        addSignals(result, signals.rearFaces(), chassis, worldOrigin, radius, state);
        add(result, reverse, chassis, worldOrigin, radius, Kind.REVERSE_WHITE, state.reverseLit(), state.reverseLit());
        return List.copyOf(result);
    }

    private static void addSignals(List<Light> result, List<VehicleHeadlightLayout.LampFace> faces,
                                   Matrix4f chassis, Vec3 worldOrigin, double radius, VehicleLampSnapshot state) {
        for (var face : faces) {
            boolean side = state.signalMode() == VehicleSignalMode.HAZARD
                    || (VehicleTurnSignalLayout.isLeft(face)
                    ? state.signalMode() == VehicleSignalMode.LEFT : state.signalMode() == VehicleSignalMode.RIGHT);
            addFace(result, face, chassis, worldOrigin, radius, Kind.SIGNAL_AMBER, side, side && state.signalLit());
        }
    }

    /** Cheap physical eligibility, including the dark half of an enabled signal. No source vectors allocated. */
    public static boolean hasEnabledFaces(List<VehicleHeadlightLayout.LampFace> front,
                                          List<VehicleHeadlightLayout.LampFace> rear,
                                          VehicleTurnSignalLayout signals,
                                          List<VehicleHeadlightLayout.LampFace> reverse,
                                          VehicleLampSnapshot state, Mx5VisualPose popup) {
        for (var face : front) {
            boolean enabled = popup == null ? state.lightMode() == VehicleLightMode.POSITION
                    : popup.lensMode(face.center().x < 0) == VehicleLightMode.POSITION;
            if (enabled && supported(face)) return true;
        }
        if (state.brakeLit() || state.lightMode().hasPositionLights())
            for (var face : rear) if (supported(face)) return true;
        if (state.reverseLit()) for (var face : reverse) if (supported(face)) return true;
        for (var faces : List.of(signals.frontFaces(), signals.rearFaces())) {
            for (var face : faces) {
                boolean enabled = state.signalMode() == VehicleSignalMode.HAZARD
                        || (VehicleTurnSignalLayout.isLeft(face)
                        ? state.signalMode() == VehicleSignalMode.LEFT : state.signalMode() == VehicleSignalMode.RIGHT);
                if (enabled && supported(face)) return true;
            }
        }
        return false;
    }

    private static boolean supported(VehicleHeadlightLayout.LampFace face) {
        return face.center().isFinite() && face.normal().isFinite()
                && face.center().lengthSqr() <= MAX_LOCAL_SOURCE_DISTANCE * MAX_LOCAL_SOURCE_DISTANCE;
    }

    private static void add(List<Light> result, List<VehicleHeadlightLayout.LampFace> faces,
                            Matrix4f chassis, Vec3 worldOrigin, double radius, Kind kind, boolean enabled, boolean active) {
        for (var face : faces) {
            addFace(result, face, chassis, worldOrigin, radius, kind, enabled, active);
        }
    }

    private static void addFace(List<Light> result, VehicleHeadlightLayout.LampFace face,
                                Matrix4f chassis, Vec3 worldOrigin, double radius,
                                Kind kind, boolean enabled, boolean active) {
        // Outlying addon faces fail closed instead of creating a large discovery region.
        if (face.center().lengthSqr() > MAX_LOCAL_SOURCE_DISTANCE * MAX_LOCAL_SOURCE_DISTANCE) return;
        Vec3 origin = new Vec3(chassis.transformPosition(face.center().toVector3f())).add(worldOrigin);
        Vec3 normal = new Vec3(chassis.transformDirection(face.normal().toVector3f())).normalize();
        if (origin.isFinite() && normal.isFinite() && result.size() < MAX_SOURCES) {
            result.add(new Light(origin, normal, radius, kind, enabled, active));
        }
    }

    public record CandidateSet(int visitedCells, boolean truncated, List<BlockPos> cells) {
        public CandidateSet { cells = List.copyOf(cells); }
    }

    /** Deterministic small local cell scan, with near-to-source sorting before any expensive model/clip call. */
    public static CandidateSet candidates(List<Light> lights, Budget budget) {
        if (lights.isEmpty() || lights.stream().noneMatch(Light::enabled) || budget.maxCars() == 0) return new CandidateSet(0, false, List.of());
        if (lights.size() > MAX_SOURCES) throw new IllegalArgumentException("Too many small lights");
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (Light light : lights) {
            if (!light.enabled()) continue;
            minX = Math.min(minX, light.origin().x - light.radius());
            minY = Math.min(minY, light.origin().y - light.radius());
            minZ = Math.min(minZ, light.origin().z - light.radius());
            maxX = Math.max(maxX, light.origin().x + light.radius());
            maxY = Math.max(maxY, light.origin().y + light.radius());
            maxZ = Math.max(maxZ, light.origin().z + light.radius());
        }
        List<Candidate> candidates = new ArrayList<>();
        int visited = 0;
        boolean truncated = false;
        outer: for (int x = floor(minX); x <= floor(maxX); x++) {
            for (int y = floor(minY); y <= floor(maxY); y++) {
                for (int z = floor(minZ); z <= floor(maxZ); z++) {
                    if (visited >= budget.maxCandidateVisits()) { truncated = true; break outer; }
                    visited++;
                    BlockPos cell = new BlockPos(x, y, z);
                    double score = Double.POSITIVE_INFINITY;
                    for (Light light : lights) {
                        if (!light.enabled()) continue;
                        double dx = axisDistance(light.origin().x, x), dy = axisDistance(light.origin().y, y);
                        double dz = axisDistance(light.origin().z, z);
                        if (dx * dx + dy * dy + dz * dz <= light.radius() * light.radius()) {
                            Vec3 centerDelta = Vec3.atCenterOf(cell).subtract(light.origin());
                            double normalRadius = (Math.abs(light.normal().x) + Math.abs(light.normal().y) + Math.abs(light.normal().z)) * 0.5;
                            if (centerDelta.dot(light.normal()) + normalRadius >= -0.04) {
                                score = Math.min(score, centerDelta.lengthSqr());
                            }
                        }
                    }
                    if (Double.isFinite(score)) candidates.add(new Candidate(cell, score));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score)
                .thenComparingInt(c -> c.cell().getX()).thenComparingInt(c -> c.cell().getY()).thenComparingInt(c -> c.cell().getZ()));
        if (candidates.size() > budget.maxCandidates()) truncated = true;
        return new CandidateSet(visited, truncated,
                candidates.stream().limit(budget.maxCandidates()).map(Candidate::cell).toList());
    }

    private static int floor(double value) {
        if (!Double.isFinite(value) || value <= Integer.MIN_VALUE || value >= Integer.MAX_VALUE - 1) {
            throw new IllegalArgumentException("Out-of-world small-light coordinate");
        }
        return (int)Math.floor(value);
    }

    private static double axisDistance(double point, int cell) {
        return point < cell ? cell - point : point > cell + 1.0 ? point - (cell + 1.0) : 0;
    }

    private record Candidate(BlockPos cell, double score) {}

    /** Weighted normalized RGB plus one capped alpha; overlapping lamps never create multiple additive copies. */
    public record Color(int rgb, float intensity) {
        public static final Color DARK = new Color(0, 0);
    }

    /** Fresh rebuild-local clip cache; the endpoint tolerance only accepts an actual surface hit. */
    static boolean unobstructed(HeadlightVisibilityCache visibility, Vec3 origin, Vec3 point) {
        var hit = visibility.clip(origin, point);
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(point) < 0.0001;
    }

    public static Color color(List<Light> lights, Vec3 point, Vec3 surfaceNormal, int visibleSources) {
        double red = 0, green = 0, blue = 0;
        for (int i = 0; i < lights.size(); i++) {
            Light light = lights.get(i);
            if (!light.active() || (visibleSources & (1 << i)) == 0) continue;
            float intensity = light.potentialIntensity(point);
            Vec3 towardLight = light.origin().subtract(point);
            if (intensity <= 0 || towardLight.dot(surfaceNormal) <= 0) continue;
            double incidence = Math.min(1, towardLight.normalize().dot(surfaceNormal));
            intensity *= (float)(0.35 + 0.65 * incidence);
            int rgb = light.color();
            red += ((rgb >> 16) & 255) / 255.0 * intensity;
            green += ((rgb >> 8) & 255) / 255.0 * intensity;
            blue += (rgb & 255) / 255.0 * intensity;
        }
        double peak = Math.max(red, Math.max(green, blue));
        if (peak < 1.0 / 255) return Color.DARK;
        int rgb = channel(red / peak) << 16 | channel(green / peak) << 8 | channel(blue / peak);
        return new Color(rgb, (float)Math.min(MAX_INTENSITY, peak));
    }

    private static int channel(double fraction) { return Math.max(0, Math.min(255, (int)Math.round(fraction * 255))); }

    public static AABB enclose(AABB body, List<Light> lights) {
        double minX = body.minX, minY = body.minY, minZ = body.minZ;
        double maxX = body.maxX, maxY = body.maxY, maxZ = body.maxZ;
        for (Light light : lights) {
            if (!light.enabled()) continue;
            minX = Math.min(minX, light.origin().x - light.radius());
            minY = Math.min(minY, light.origin().y - light.radius());
            minZ = Math.min(minZ, light.origin().z - light.radius());
            maxX = Math.max(maxX, light.origin().x + light.radius());
            maxY = Math.max(maxY, light.origin().y + light.radius());
            maxZ = Math.max(maxZ, light.origin().z + light.radius());
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
