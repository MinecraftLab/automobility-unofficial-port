package io.github.foundationgames.automobility.client.lighting;

/** Full reservation vector, not a scalar stand-in for native collection work. */
public record HeadlightRefreshWork(long discoveryRays, long candidates, long blocks, long modelQuads,
                                   long testedPatches, long patches, long visibilityRays, long nearBlocks) {
    public static final HeadlightRefreshWork ZERO = new HeadlightRefreshWork(0,0,0,0,0,0,0,0);
    public HeadlightRefreshWork {
        if (discoveryRays<0 || candidates<0 || blocks<0 || modelQuads<0 || testedPatches<0
                || patches<0 || visibilityRays<0 || nearBlocks<0) throw new IllegalArgumentException("Negative quota");
    }
    public static HeadlightRefreshWork of(VehicleLightingPolicy.MainQuota q) {
        return new HeadlightRefreshWork(q.discoveryRays(),q.candidates(),q.blocks(),q.modelQuads(),
                q.testedPatches(),q.patches(),q.visibilityRays(),q.nearBlocks());
    }
    public static HeadlightRefreshWork of(VehicleLightingPolicy.CoverageQuota q) {
        return of(q.base()).plus(of(q.detail()));
    }
    public static HeadlightRefreshWork of(VehicleLightingPolicy.MainBudget q) {
        return new HeadlightRefreshWork(q.discoveryRays(),q.candidates(),q.blocks(),q.modelQuads(),
                q.testedPatches(),q.patches(),q.visibilityRays(),q.nearBlocks());
    }
    public HeadlightRefreshWork plus(HeadlightRefreshWork b) {
        return new HeadlightRefreshWork(Math.addExact(discoveryRays,b.discoveryRays),Math.addExact(candidates,b.candidates),
                Math.addExact(blocks,b.blocks),Math.addExact(modelQuads,b.modelQuads),Math.addExact(testedPatches,b.testedPatches),
                Math.addExact(patches,b.patches),Math.addExact(visibilityRays,b.visibilityRays),Math.addExact(nearBlocks,b.nearBlocks));
    }
    public boolean fits(HeadlightRefreshWork b) {
        return discoveryRays<=b.discoveryRays && candidates<=b.candidates && blocks<=b.blocks && modelQuads<=b.modelQuads
                && testedPatches<=b.testedPatches && patches<=b.patches && visibilityRays<=b.visibilityRays && nearBlocks<=b.nearBlocks;
    }
}
