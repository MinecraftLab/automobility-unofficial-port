package io.github.foundationgames.automobility.client.lighting;

import io.github.foundationgames.automobility.client.vehicle.VehicleLightQuality;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Client-only finite admission and presentation policy; no world or renderer callbacks. */
public final class VehicleLightingPolicy {
    public static final int MAX_CANDIDATES = 32;
    public static final int MAX_HEADLIGHT_CARS = 7;
    public static final int MAX_ACTIVE_CARS = MAX_HEADLIGHT_CARS + 1;
    public static final int MAX_BASELINE_CARS = MAX_CANDIDATES;
    public static final int MAX_RECENT_SOURCES = 64;
    public static final int RECENT_TICKS = 40;
    public static final int FADE_TICKS = 4;
    private static final double INCUMBENT_MARGIN = 1.15;

    private VehicleLightingPolicy() {}

    public record MainBudget(int maxCars, int maxActiveCars, int discoveryRays, int candidates, int blocks,
                             int modelQuads, int testedPatches, int patches, int visibilityRays,
                             int nearBlocks) {}

    public static MainBudget mainBudget(VehicleLightQuality quality) {
        int detail = switch (quality) { case ECONOMY, POTATO -> 2; case BALANCED -> 3; case HIGH -> 4; };
        // Discovery keeps optical coverage in every preset. Detail, not lamp range, changes.
        int discovery = 3 * (55 + HeadlightRoadFootprint.MAX_COLUMNS);
        int tested = detail * (HeadlightSurfaceDiscovery.MAX_BLOCKS * 24 + 128 * 72);
        return new MainBudget(MAX_HEADLIGHT_CARS, MAX_ACTIVE_CARS, discovery,
                3 * HeadlightSurfaceDiscovery.MAX_CANDIDATES,
                3 * HeadlightSurfaceDiscovery.MAX_BLOCKS,
                detail * HeadlightSurfaceDiscovery.MAX_BLOCKS * 24,
                tested, detail * (HeadlightSurfaceDiscovery.MAX_BLOCKS * 4 + 128 * 12),
                tested * 2, quality == VehicleLightQuality.ECONOMY || quality == VehicleLightQuality.POTATO ? 0 : detail * 128);
    }

    /** A reservation made before native entity submission; sums never exceed the fleet budget. */
    public record MainQuota(int discoveryRays, int candidates, int blocks, int modelQuads,
                            int testedPatches, int patches, int visibilityRays, int nearBlocks) {}

    public record CoverageQuota(MainQuota base, MainQuota detail) {}

    /**
     * Coverage is independent of refinement membership: all admitted visible cars
     * have a non-fading base. Fine work only replaces subdivisions on those SAME
     * discovered surfaces, never changes the optical range or adds another layer.
     */
    public static Map<Integer, CoverageQuota> coverageQuotas(MainBudget budget, List<Candidate> visible,
                                                            List<Contribution> refinement, double time) {
        if (visible.size() > MAX_BASELINE_CARS) throw new IllegalArgumentException("Unbounded baseline fleet");
        if (visible.isEmpty()) return Map.of();
        if (refinement.size() > budget.maxActiveCars()) throw new IllegalArgumentException("Unbounded refinement fleet");
        Set<Integer> ids = new HashSet<>();
        for (var item : visible) if (!ids.add(item.id())) throw new IllegalArgumentException("Duplicate baseline source");
        for (var item : refinement) if (!ids.contains(item.id())) throw new IllegalArgumentException("Refinement without coverage");
        int slots = (int) refinement.stream().mapToInt(Contribution::slot).distinct().count();
        if (slots > budget.maxCars()) throw new IllegalArgumentException("Unbounded detail slots");
        boolean fine = budget.nearBlocks() > 0 && slots > 0;
        // A higher preset must never trade away the ECONOMY coverage floor.
        // Refinement spends only the additional work, not half of the base.
        var floor = mainBudget(VehicleLightQuality.ECONOMY);
        int baseTests = Math.min(budget.testedPatches(), floor.testedPatches());
        int basePatches = Math.min(budget.patches(), floor.patches());
        int baseRays = Math.min(budget.visibilityRays(), floor.visibilityRays());
        int count = visible.size();
        MainQuota base = new MainQuota(Math.min(55 + HeadlightRoadFootprint.MAX_COLUMNS, budget.discoveryRays() / count),
                Math.min(HeadlightSurfaceDiscovery.MAX_CANDIDATES, budget.candidates() / count),
                Math.min(HeadlightSurfaceDiscovery.MAX_BLOCKS, budget.blocks() / count), budget.modelQuads() / count,
                baseTests / count, basePatches / count, baseRays / count, 0);
        MainQuota zero = new MainQuota(0, 0, 0, 0, 0, 0, 0, 0);
        Map<Integer, MainQuota> detail = new HashMap<>();
        if (fine) {
            var weights = shares(refinement, time);
            for (var item : refinement) {
                double share = weights.get(item.id());
                detail.put(item.id(), new MainQuota(0, 0, 0, 0,
                        portion(budget.testedPatches() - baseTests, Integer.MAX_VALUE, slots, share),
                        portion(budget.patches() - basePatches, Integer.MAX_VALUE, slots, share),
                        portion(budget.visibilityRays() - baseRays, Integer.MAX_VALUE, slots, share),
                        portion(budget.nearBlocks(), 128, slots, share)));
            }
        }
        Map<Integer, CoverageQuota> result = new LinkedHashMap<>();
        for (var item : visible) result.put(item.id(), new CoverageQuota(base, detail.getOrDefault(item.id(), zero)));
        return Map.copyOf(result);
    }

    public static Map<Integer, MainQuota> quotas(MainBudget budget, List<Contribution> contributions) {
        double time = contributions.stream().mapToDouble(Contribution::startTick).max().orElse(0) + 0.5;
        return quotas(budget, contributions, time);
    }

    public static Map<Integer, MainQuota> quotas(MainBudget budget, List<Contribution> contributions, double time) {
        if (contributions.isEmpty()) return Map.of();
        int count = contributions.size();
        if (count > budget.maxActiveCars())
            throw new IllegalArgumentException("Unbudgeted headlight contributions");
        int slots = (int) contributions.stream().mapToInt(Contribution::slot).distinct().count();
        if (slots > budget.maxCars()) throw new IllegalArgumentException("Unbudgeted steady slots");
        var shares = shares(contributions, time);
        Map<Integer, MainQuota> result = new LinkedHashMap<>();
        for (var item : contributions) {
            double share = shares.get(item.id());
            result.put(item.id(), new MainQuota(portion(budget.discoveryRays(), 55 + HeadlightRoadFootprint.MAX_COLUMNS, slots, share),
                    portion(budget.candidates(), HeadlightSurfaceDiscovery.MAX_CANDIDATES, slots, share),
                    portion(budget.blocks(), HeadlightSurfaceDiscovery.MAX_BLOCKS, slots, share),
                    portion(budget.modelQuads(), Integer.MAX_VALUE, slots, share),
                    portion(budget.testedPatches(), Integer.MAX_VALUE, slots, share),
                    portion(budget.patches(), Integer.MAX_VALUE, slots, share),
                    portion(budget.visibilityRays(), Integer.MAX_VALUE, slots, share),
                    portion(budget.nearBlocks(), 128, slots, share)));
        }
        return Map.copyOf(result);
    }

    /** Crossover contributors share the existing small-spill fleet envelope, not an extra full budget. */
    public static VehicleSmallLampLighting.Budget smallQuota(VehicleSmallLampLighting.Budget base, int count) {
        if (count < 1 || count > base.maxCars() + 1) throw new IllegalArgumentException("Invalid small-light plan");
        int cars = base.maxCars();
        return new VehicleSmallLampLighting.Budget(base.radius(), cars,
                Math.min(base.maxCandidateVisits(), cars * base.maxCandidateVisits() / count),
                base.maxCandidates(), Math.min(base.maxBlocks(), cars * base.maxBlocks() / count),
                Math.min(base.maxModelQuads(), cars * base.maxModelQuads() / count),
                Math.min(base.maxTestedPatches(), cars * base.maxTestedPatches() / count),
                Math.min(base.maxPatches(), cars * base.maxPatches() / count),
                Math.min(base.maxRays(), cars * base.maxRays() / count), base.divisions(), base.maxCameraDistance());
    }

    public static int smallActiveLimit(VehicleLightQuality quality) {
        var budget = VehicleSmallLampLighting.budget(quality);
        return budget.maxCars() == 0 ? 0 : budget.maxCars() + 1;
    }

    public static Map<Integer, VehicleSmallLampLighting.Budget> smallQuotas(VehicleSmallLampLighting.Budget base,
                                                                          List<Contribution> contributions, double time) {
        if (contributions.isEmpty()) return Map.of();
        int slots = (int) contributions.stream().mapToInt(Contribution::slot).distinct().count();
        if (slots > base.maxCars() || contributions.size() > base.maxCars() + 1)
            throw new IllegalArgumentException("Unbudgeted small-light contributions");
        var shares = shares(contributions, time);
        Map<Integer, VehicleSmallLampLighting.Budget> result = new LinkedHashMap<>();
        int cars = base.maxCars();
        for (var item : contributions) {
            double share = shares.get(item.id());
            result.put(item.id(), new VehicleSmallLampLighting.Budget(base.radius(), cars,
                    portion(cars * base.maxCandidateVisits(), base.maxCandidateVisits(), slots, share),
                    base.maxCandidates(), portion(cars * base.maxBlocks(), base.maxBlocks(), slots, share),
                    portion(cars * base.maxModelQuads(), base.maxModelQuads(), slots, share),
                    portion(cars * base.maxTestedPatches(), base.maxTestedPatches(), slots, share),
                    portion(cars * base.maxPatches(), base.maxPatches(), slots, share),
                    portion(cars * base.maxRays(), base.maxRays(), slots, share), base.divisions(), base.maxCameraDistance()));
        }
        return Map.copyOf(result);
    }

    private static int portion(int total, int perSlotCap, int slots, double share) {
        return (int)Math.floor(Math.min(perSlotCap, total / slots) * share);
    }

    /** Only the replaced pair divides a slot; other cars keep their existing detail reservation. */
    private static Map<Integer, Double> shares(List<Contribution> plan, double time) {
        Map<Integer, Double> totals = new HashMap<>();
        for (var item : plan) totals.merge(item.slot(), (double)item.weight(time), Double::sum);
        Map<Integer, Double> result = new HashMap<>();
        for (var item : plan) {
            double total = totals.get(item.slot());
            long members = plan.stream().filter(other -> other.slot() == item.slot()).count();
            result.put(item.id(), total > 0 ? item.weight(time) / total : 1.0 / members);
        }
        return result;
    }

    /** Identity and actual lamp/model state prevent entity-ID reuse from restoring old priority. */
    public record SourceKey(UUID identity, int lightToken) {
        public SourceKey { if (identity == null) throw new IllegalArgumentException("Missing source identity"); }
    }

    public record Candidate(int id, double score, int cost, SourceKey source) {
        public Candidate(int id, double score, int cost) { this(id, score, cost, new SourceKey(new UUID(0, id), 0)); }
        public Candidate {
            if (!Double.isFinite(score) || score < 0 || cost < 1 || source == null) throw new IllegalArgumentException("Invalid light candidate");
        }
    }

    // Runtime IDs depend on client load order. Equal-score cosmetic reservations
    // must follow persistent physical identity across reconnects and shader reloads.
    private static final Comparator<Candidate> STABLE_SOURCE_ORDER =
            Comparator.comparing((Candidate item) -> item.source().identity()).thenComparingInt(Candidate::id);

    public static Comparator<Candidate> stableSourceOrder() { return STABLE_SOURCE_ORDER; }

    /** Immutable frame weight. Never read mutable scheduler state from a deferred draw. */
    public record Contribution(int id, int cost, double startTick, float startWeight, boolean target, int slot) {
        public Contribution(int id, int cost, double startTick, float startWeight, boolean target) {
            this(id, cost, startTick, startWeight, target, id);
        }
        public Contribution {
            if (cost < 1 || !Double.isFinite(startTick) || !Float.isFinite(startWeight) || startWeight < 0 || startWeight > 1)
                throw new IllegalArgumentException("Invalid light transition");
        }
        public float weight(double time) {
            if (!Double.isFinite(time)) return startWeight;
            double fraction = Math.max(0, Math.min(1, (time - startTick) / FADE_TICKS));
            float end = target ? 1 : 0;
            return (float)(startWeight + (end - startWeight) * fraction);
        }
    }

    /**
     * Retiring and incoming emitters share a logical slot and its finite work reservation.
     * If the bounded crossover slot is occupied, finish that fade before another admission.
     */
    public static final class Scheduler {
        private final Map<Integer, Contribution> active = new LinkedHashMap<>();
        private final Map<Integer, RecentSource> recent = new LinkedHashMap<>();
        private final Map<Integer, SourceKey> observed = new HashMap<>();
        private long observedTick = Long.MIN_VALUE;
        private long tick = Long.MIN_VALUE;
        private List<Contribution> plan = List.of();

        public void reset() { active.clear(); recent.clear(); observed.clear(); observedTick = Long.MIN_VALUE; tick = Long.MIN_VALUE; plan = List.of(); }

        /** Only known metadata is touched; scanning offscreen entities allocates no reservations. */
        public void observeLive(long tick, int id, SourceKey source) {
            if (observedTick != tick) { observed.clear(); observedTick = tick; }
            if (recent.containsKey(id)) observed.put(id, source);
        }

        public int recentCount() { return recent.size(); }

        private record RecentSource(SourceKey source, long lastVisibleTick) {}

        public List<Contribution> update(long tick, int steadyLimit, int activeLimit, int costLimit,
                                         List<Candidate> input) {
            if (steadyLimit < 0 || steadyLimit > activeLimit || activeLimit > MAX_ACTIVE_CARS || costLimit < 0
                    || input.size() > MAX_CANDIDATES)
                throw new IllegalArgumentException("Invalid lighting limits");
            if (this.tick == tick) return plan;
            this.tick = tick;
            Map<Integer, Candidate> candidates = new HashMap<>();
            // Native callers preserve eligible residents in their bounded shortlist.
            List<Candidate> bounded = input.stream().sorted(Comparator.comparingDouble(Candidate::score).reversed()
                    .thenComparing(STABLE_SOURCE_ORDER)).toList();
            for (var item : bounded) {
                if (candidates.put(item.id(), item) != null) throw new IllegalArgumentException("Duplicate light ID");
            }
            if (observedTick != tick) { observed.clear(); observedTick = tick; }
            for (var item : bounded) if (recent.containsKey(item.id())) observed.put(item.id(), item.source());
            // Physical OFF/removal/unload/state changes invalidate metadata immediately.
            // Offscreen but still valid sources retain only a short identity/priority memory.
            var forgotten = new HashSet<Integer>();
            recent.entrySet().removeIf(entry -> {
                var current = observed.get(entry.getKey());
                boolean stale = current == null || !current.equals(entry.getValue().source())
                        || tick < entry.getValue().lastVisibleTick() || tick - entry.getValue().lastVisibleTick() > RECENT_TICKS;
                // Changing lamps invalidates the history hint, not an ongoing
                // visible transition. Only disappearance/identity/clock resets
                // cancel a contribution; fresh masks still gate actual output.
                if (current == null || !current.identity().equals(entry.getValue().source().identity())
                        || tick < entry.getValue().lastVisibleTick()) forgotten.add(entry.getKey());
                return stale;
            });
            active.keySet().removeAll(forgotten);
            // Leaving the view frees the active slot immediately, not the metadata history.
            active.entrySet().removeIf(entry -> !candidates.containsKey(entry.getKey())
                    || !entry.getValue().target() && entry.getValue().weight(tick) <= 0);
            List<Candidate> ranked = new ArrayList<>(bounded);
            ranked.sort(Comparator.comparingDouble((Candidate candidate) -> {
                var resident = active.get(candidate.id());
                return candidate.score() * (resident != null && resident.target() || recent.containsKey(candidate.id()) ? INCUMBENT_MARGIN : 1);
            }).reversed().thenComparing(STABLE_SOURCE_ORDER));
            Set<Integer> desired = new HashSet<>();
            int desiredCost = 0;
            for (var item : ranked) {
                var resident = active.get(item.id());
                if (resident != null && resident.target() && tick - resident.startTick() < FADE_TICKS
                        && desired.size() < steadyLimit && item.cost() <= costLimit - desiredCost) {
                    desired.add(item.id()); desiredCost += item.cost();
                }
            }
            for (var item : ranked) {
                if (desired.size() >= steadyLimit) break;
                if (desired.contains(item.id())) continue;
                if (item.cost() > costLimit - desiredCost) continue;
                desired.add(item.id()); desiredCost += item.cost();
            }
            for (var entry : active.entrySet()) {
                var old = entry.getValue();
                boolean target = desired.contains(entry.getKey());
                int cost = candidates.get(entry.getKey()).cost();
                entry.setValue(old.target() == target
                        ? new Contribution(old.id(), cost, old.startTick(), old.startWeight(), target, old.slot())
                        : new Contribution(old.id(), cost, tick, old.weight(tick), target, old.slot()));
            }
            // A drastically changed footprint/quality can exceed capacity. Remove weakest
            // reservations conservatively; never spend extra work to preserve a cosmetic fade.
            Comparator<Contribution> order = Comparator.comparingDouble((Contribution item) -> candidates.get(item.id()).score())
                    .thenComparing((a, b) -> STABLE_SOURCE_ORDER.compare(candidates.get(b.id()), candidates.get(a.id())));
            while (active.size() > activeLimit || totalCost() > costLimit) {
                var weakest = active.values().stream().min(order).orElseThrow();
                active.remove(weakest.id());
            }
            // A lower preset plus different desired cars can leave every old slot
            // retiring. Keep retirement inside the NEW steady capacity and reserve
            // an admission opportunity, rather than throwing during slot compaction.
            while (active.values().stream().filter(item -> !item.target()).count() > steadyLimit) {
                var weakest = active.values().stream().filter(item -> !item.target()).min(order).orElseThrow();
                active.remove(weakest.id());
            }
            for (var item : ranked) {
                if (!desired.contains(item.id()) || active.containsKey(item.id())) continue;
                if (active.size() >= activeLimit || item.cost() > costLimit - totalCost()) continue;
                int slot = availableSlot(steadyLimit);
                if (slot < 0) continue;
                // Looking at an already-on lamp is not an ignition event. A truly free
                // slot starts fully present; only a visible replacement pair crossfades.
                final int chosenSlot = slot;
                boolean replacement = active.values().stream().anyMatch(old -> old.slot() == chosenSlot);
                active.put(item.id(), new Contribution(item.id(), item.cost(), tick, replacement ? 0 : 1, true, slot));
            }
            compactSlots(steadyLimit);
            for (var item : active.values()) {
                if (!item.target()) continue;
                var source = candidates.get(item.id()).source();
                recent.remove(item.id());
                recent.put(item.id(), new RecentSource(source, tick));
            }
            while (recent.size() > MAX_RECENT_SOURCES) recent.remove(recent.keySet().iterator().next());
            plan = active.values().stream().sorted(Comparator.comparingInt(Contribution::id)).toList();
            return plan;
        }

        private int totalCost() { return active.values().stream().mapToInt(Contribution::cost).sum(); }
        private void compactSlots(int steadyLimit) {
            Set<Integer> targets = new HashSet<>();
            var items = active.values().stream().sorted(Comparator.comparingInt(Contribution::slot)
                    .thenComparingInt(Contribution::id)).toList();
            for (var item : items) {
                if (!item.target()) continue;
                int slot = item.slot();
                if (slot >= steadyLimit || targets.contains(slot)) {
                    slot = -1;
                    for (int index = 0; index < steadyLimit; index++) if (!targets.contains(index)) { slot = index; break; }
                }
                if (slot < 0) throw new IllegalStateException("Too many target lights");
                targets.add(slot); replaceSlot(item, slot);
            }
            Set<Integer> retiring = new HashSet<>();
            for (var item : items) {
                if (item.target()) continue;
                int slot = item.slot();
                if (slot >= steadyLimit || retiring.contains(slot)) {
                    slot = -1;
                    for (int index = 0; index < steadyLimit; index++) if (!retiring.contains(index)) { slot = index; break; }
                }
                if (slot < 0) throw new IllegalStateException("Too many retiring lights");
                retiring.add(slot); replaceSlot(item, slot);
            }
        }
        private void replaceSlot(Contribution item, int slot) {
            if (item.slot() != slot) active.put(item.id(), new Contribution(item.id(), item.cost(), item.startTick(),
                    item.startWeight(), item.target(), slot));
        }
        private int availableSlot(int steadyLimit) {
            for (int slot = 0; slot < steadyLimit; slot++) {
                final int index = slot;
                if (active.values().stream().noneMatch(item -> item.slot() == index)) return slot;
            }
            return active.values().stream().filter(item -> !item.target())
                    .filter(item -> active.values().stream().filter(other -> other.slot() == item.slot()).count() == 1)
                    .mapToInt(Contribution::slot).min().orElse(-1);
        }
        public Contribution contribution(int id) { return active.get(id); }
        public int retainedCount() { return active.size(); }
    }
}
