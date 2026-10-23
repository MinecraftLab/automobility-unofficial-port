package io.github.foundationgames.automobility.client.lighting;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One client-world session. Epoch/reload/view changes MUST NOT construct a new ledger. */
public final class HeadlightRefreshLedger {
    public enum Kind { WARMUP, FORCED }
    public static final class Grant {
        private final UUID owner;
        private final long frame, epoch;
        private final Kind kind;
        private final VehicleLightingPolicy.CoverageQuota quota;
        private boolean attempted;
        private Grant(UUID owner,long frame,long epoch,Kind kind,VehicleLightingPolicy.CoverageQuota quota) {
            this.owner=owner;this.frame=frame;this.epoch=epoch;this.kind=kind;this.quota=quota;
        }
        public Kind kind(){return kind;}
        public VehicleLightingPolicy.CoverageQuota quota(){return quota;}
    }
    private final Set<UUID> cohort;
    private final HeadlightRefreshWork warmupEnvelope, forcedEnvelope;
    private final Map<UUID,Grant> warmups=new HashMap<>();
    private HeadlightRefreshWork warmupSpent=HeadlightRefreshWork.ZERO, forcedSpent=HeadlightRefreshWork.ZERO;
    private long tickHighWater=Long.MIN_VALUE;
    private Grant forced;

    public HeadlightRefreshLedger(Collection<UUID> owners,HeadlightRefreshWork warmupEnvelope,HeadlightRefreshWork forcedEnvelope) {
        cohort=Set.copyOf(owners);
        if(cohort.size()!=owners.size() || cohort.size()>32)throw new IllegalArgumentException("Unique bounded initial cohort required");
        this.warmupEnvelope=java.util.Objects.requireNonNull(warmupEnvelope);
        this.forcedEnvelope=java.util.Objects.requireNonNull(forcedEnvelope);
    }
    public Grant warmup(UUID owner,long frame,long epoch,VehicleLightingPolicy.CoverageQuota quota) {
        if(!cohort.contains(owner) || warmups.containsKey(owner))return null;
        var next=warmupSpent.plus(HeadlightRefreshWork.of(quota));
        if(!next.fits(warmupEnvelope))return null;
        var grant=new Grant(owner,frame,epoch,Kind.WARMUP,quota);
        warmups.put(owner,grant);warmupSpent=next;return grant;
    }
    public Grant forced(UUID owner,long frame,long tick,long epoch,VehicleLightingPolicy.CoverageQuota quota) {
        if(!cohort.contains(owner) || tick<tickHighWater)return null;
        if(tick>tickHighWater){tickHighWater=tick;forced=null;forcedSpent=HeadlightRefreshWork.ZERO;}
        // Exactly one extra owner per strictly advancing world tick, not per event/frame.
        if(forced!=null)return null;
        var cost=HeadlightRefreshWork.of(quota);if(!cost.fits(forcedEnvelope))return null;
        forced=new Grant(owner,frame,epoch,Kind.FORCED,quota);forcedSpent=cost;return forced;
    }
    /** Budget was charged at reservation. Failed/empty/aborted/late claims never refund it. */
    public boolean claim(Grant grant,long actualFrame,long actualEpoch) {
        if(grant==null || grant.attempted || (grant.kind==Kind.WARMUP?warmups.get(grant.owner)!=grant:forced!=grant))return false;
        grant.attempted=true;
        return grant.frame==actualFrame && grant.epoch==actualEpoch;
    }
    public HeadlightRefreshWork warmupSpent(){return warmupSpent;}
    public HeadlightRefreshWork forcedSpent(){return forcedSpent;}
    public int warmupTombstones(){return warmups.size();}
}
