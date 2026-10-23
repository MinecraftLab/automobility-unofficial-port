package io.github.foundationgames.automobility.client.lighting;

import java.util.List;
import java.util.Objects;

/** Exact validity of certified static geometry AND visibility, never an age heuristic. */
public record HeadlightParkedCacheKey(Object cache,List<HeadlightBeam> beams,
                                     VehicleLightingPolicy.CoverageQuota quota,long epoch,int topology) {
    public HeadlightParkedCacheKey {
        Objects.requireNonNull(cache);beams=List.copyOf(beams);Objects.requireNonNull(quota);
        Objects.requireNonNull(quota.base());Objects.requireNonNull(quota.detail());
        if(beams.isEmpty())throw new IllegalArgumentException("A static light source needs actual beams");
    }
    public boolean matches(Object actualCache,List<HeadlightBeam> actualBeams,
                           VehicleLightingPolicy.CoverageQuota actualQuota,long actualEpoch,
                           int actualTopology,boolean qualified) {
        return qualified && cache==actualCache && epoch==actualEpoch && topology==actualTopology
                && quota.equals(actualQuota) && beams.equals(actualBeams);
    }
}
