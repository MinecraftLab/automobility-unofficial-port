package io.github.foundationgames.automobility.client.lighting;

import io.github.foundationgames.automobility.client.vehicle.VehicleLightQuality;
import java.util.*;

/** Presentation selection only. This class never owns world results or rebuild deadlines. */
public final class VehicleProjectionPolicy {
    public static final int MAX_CANDIDATES = 32, MAX_RECENT = 64;
    public static final double VIEW_MARGIN = 3.0;
    private static final double INCUMBENT_MARGIN = 1.15;
    /** Apply active-driver priority before the bounded broad-phase queue is truncated. */
    public static int admissionPriority(boolean main, boolean driven, boolean incumbent) {
        return main ? 0 : driven ? (incumbent ? 1 : 2) : incumbent ? 3 : 4;
    }
    public enum Role { FULL, CONE, LAMP }
    public record Profile(int priorityFull, int viewFull, int cones) {
        public Profile {
            if (priorityFull < 1 || priorityFull > 7 || viewFull > 6 || priorityFull + viewFull > 7 || viewFull < 0 || cones < 0 || cones > 7)
                throw new IllegalArgumentException("Bounded projection profile");
        }
    }
    public static Profile profile(VehicleLightQuality quality) {
        return switch (Objects.requireNonNull(quality)) {
            case POTATO -> new Profile(1, 0, 2);
            case ECONOMY -> new Profile(1, 1, 4);
            case BALANCED -> new Profile(3, 3, 6);
            case HIGH -> new Profile(7, 0, 7);
        };
    }
    public record Key(int id, UUID identity, int lightToken) {
        public Key { Objects.requireNonNull(identity); }
    }
    public record Candidate(Key key, double score, boolean inExpandedView, boolean driven) {
        public Candidate(Key key, double score, boolean inExpandedView) { this(key,score,inExpandedView,false); }
        public Candidate {
            Objects.requireNonNull(key);
            if (!Double.isFinite(score) || score < 0) throw new IllegalArgumentException("Finite score");
        }
    }
    public record Plan(VehicleLightQuality quality, UUID mainIdentity, Map<Key, Role> roles,
                       Set<Key> priorityFull, Set<Key> viewFull) {
        public Plan {
            roles = Map.copyOf(roles); priorityFull = Set.copyOf(priorityFull); viewFull = Set.copyOf(viewFull);
        }
        public Role role(Key key) { return roles.getOrDefault(key, Role.LAMP); }
        public int count(Role role) { return (int)roles.values().stream().filter(value -> value == role).count(); }
    }
    public static final class Selector {
        private final LinkedHashMap<Key, Long> recent = new LinkedHashMap<>();
        private long lastTick = Long.MIN_VALUE;
        public void reset() { recent.clear(); lastTick = Long.MIN_VALUE; }
        public int recentCount() { return recent.size(); }

        /** A missing mounted primary still reserves its slot. Never substitute a visible car for it. */
        public Plan select(long tick, VehicleLightQuality quality, UUID mountedPrimary, List<Candidate> input) {
            return select(tick, quality, profile(quality), mountedPrimary, input);
        }

        public Plan select(long tick, VehicleLightQuality quality, Profile profile, UUID mountedPrimary, List<Candidate> input) {
            Objects.requireNonNull(profile);
            if (input.size() > MAX_CANDIDATES) throw new IllegalArgumentException("Bounded candidates");
            if (tick < lastTick) reset();
            lastTick = tick;
            recent.entrySet().removeIf(entry -> tick - entry.getValue() > 40);
            Set<Integer> ids = new HashSet<>(); Set<UUID> identities = new HashSet<>();
            for (Candidate item : input) if (!ids.add(item.key.id) || !identities.add(item.key.identity))
                throw new IllegalArgumentException("Duplicate physical source");
            List<Candidate> ranked = new ArrayList<>(input);
            ranked.sort(Comparator.comparingInt((Candidate item) -> item.driven ? 0 : 1)
                    .thenComparing(Comparator.comparingDouble((Candidate item) ->
                            item.score * (recent.containsKey(item.key) ? INCUMBENT_MARGIN : 1)).reversed())
                    .thenComparing(item -> item.key.identity).thenComparingInt(item -> item.key.id));
            Map<Key, Role> roles = new LinkedHashMap<>(); Set<Key> priority = new HashSet<>(), view = new HashSet<>();
            UUID primary = mountedPrimary;
            if (primary == null && !ranked.isEmpty()) primary = ranked.getFirst().key.identity;
            if (primary != null) for (Candidate item : ranked) if (item.key.identity.equals(primary)) {
                roles.put(item.key, Role.FULL); priority.add(item.key); break;
            }
            // One slot belongs to the primary even when its complete footprint is outside the view.
            int remainingPriority = profile.priorityFull - 1;
            for (Candidate item : ranked) if (remainingPriority > 0 && !roles.containsKey(item.key)) {
                roles.put(item.key, Role.FULL); priority.add(item.key); remainingPriority--;
            }
            int remainingView = profile.viewFull;
            for (Candidate item : ranked) if (remainingView > 0 && item.inExpandedView && !roles.containsKey(item.key)) {
                roles.put(item.key, Role.FULL); view.add(item.key); remainingView--;
            }
            int remainingCones = profile.cones;
            for (Candidate item : ranked) if (remainingCones > 0 && item.inExpandedView && !roles.containsKey(item.key)) {
                roles.put(item.key, Role.CONE); remainingCones--;
            }
            for (Key key : roles.keySet()) { recent.remove(key); recent.put(key, tick); }
            while (recent.size() > MAX_RECENT) recent.remove(recent.keySet().iterator().next());
            return new Plan(quality, primary, roles, priority, view);
        }
    }
    private VehicleProjectionPolicy() {}
}
