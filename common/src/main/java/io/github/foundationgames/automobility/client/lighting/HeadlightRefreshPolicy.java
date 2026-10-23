package io.github.foundationgames.automobility.client.lighting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure scheduling prototype, NOT wired to the renderer. One instance per world session.
 * Adapter must certify static terrain/models and unchanged actual beams; unknowns use legacy.
 * Nominal 20Hz is not a promise of identical tick-aligned invalidation freshness.
 */
public final class HeadlightRefreshPolicy {
    public enum Reason { LEGACY, LEGACY_STICKY, NORMAL_COLD, NORMAL_DUE, REUSE_PHASE, WARMUP, FORCED }
    public record Input(UUID owner,boolean eligible,boolean cached,Object cacheToken,long cachedEpoch,long requiredEpoch,
                        double cachedAt,boolean normalAvailable,VehicleLightingPolicy.CoverageQuota quota) {
        public Input {
            Objects.requireNonNull(owner);Objects.requireNonNull(quota);HeadlightRefreshWork.of(quota);
            if(cached!=(cacheToken!=null))throw new IllegalArgumentException("Exact present immutable cache identity required");
        }
    }
    public record Decision(Reason reason,long frameSerial,double plannedTime,Object cacheToken,long requiredEpoch,double deadline,HeadlightRefreshLedger.Grant grant) {
        /** Recheck at actual submission; a frozen plan never authorizes late invalidation. */
        public boolean allowsReuse(long actualFrame,double actualTime,Object actualCache,long actualEpoch,boolean stillEligible) {
            return reason==Reason.REUSE_PHASE && stillEligible && actualEpoch==requiredEpoch && actualFrame==frameSerial
                    && actualCache!=null && actualCache==cacheToken
                    && Double.isFinite(actualTime) && actualTime>=plannedTime && actualTime<deadline;
        }
    }
    public record Frame(long serial,long tick,double time,Map<UUID,Decision> decisions) {
        public Frame { decisions=Map.copyOf(decisions); }
    }
    private static final class Owner {
        final double phase;
        boolean legacy;
        Owner(double phase){this.phase=phase;}
    }
    private final Map<UUID,Owner> owners=new HashMap<>();
    private final Map<UUID,String> firstRevocations=new LinkedHashMap<>();
    private final HeadlightRefreshLedger ledger;
    private long tickHighWater=Long.MIN_VALUE;
    private double timeHighWater=Double.NEGATIVE_INFINITY;
    private Frame last;

    public HeadlightRefreshPolicy(Collection<UUID> initialOwners,HeadlightRefreshWork warmupEnvelope,HeadlightRefreshWork forcedEnvelope) {
        var sorted=initialOwners.stream().sorted().distinct().toList();
        if(sorted.size()!=initialOwners.size() || sorted.size()>32)throw new IllegalArgumentException("Unique initial cohort <=32");
        for(int i=0;i<sorted.size();i++)owners.put(sorted.get(i),new Owner((double)i/sorted.size()));
        ledger=new HeadlightRefreshLedger(sorted,warmupEnvelope,forcedEnvelope);
    }
    public HeadlightRefreshLedger ledger(){return ledger;}
    /** Late adapter invalidation revokes reuse without resetting phases or any credit. */
    public void revoke(UUID id){revoke(id,"EXPLICIT");}
    public void revoke(UUID id,String reason){var owner=owners.get(id);if(owner!=null){if(!owner.legacy)firstRevocations.put(id,reason);owner.legacy=true;}}
    public String revocationSnapshot(){return firstRevocations.toString();}
    private void reject(Input input,String reason,long serial,long tick,double time,double due){
        revoke(input.owner,reason+" frame="+serial+" tick="+tick+" time="+time+" cachedAt="+input.cachedAt
                +" due="+due+" normal="+input.normalAvailable+" epochs="+input.cachedEpoch+"/"+input.requiredEpoch);
    }
    public boolean isLegacy(UUID id){var owner=owners.get(id);return owner==null || owner.legacy;}
    public Frame plan(long serial,long tick,double time,List<Input> candidates) {
        if(serial<0)throw new IllegalArgumentException("Negative frame serial");
        if(last!=null && serial==last.serial)return last; // Same immutable fleet plan, no new grants.
        if(last!=null && serial<last.serial)throw new IllegalArgumentException("Frame serial rollback");
        if(candidates.size()>32)throw new IllegalArgumentException("Bounded selected fleet required");
        var inputs=new ArrayList<>(candidates);inputs.sort(java.util.Comparator.comparing(Input::owner));
        for(int i=1;i<inputs.size();i++)if(inputs.get(i-1).owner.equals(inputs.get(i).owner))throw new IllegalArgumentException("Duplicate source");
        boolean clockValid=tick>=0 && Double.isFinite(time) && time>=tick && time<=tick+1.0
                && time>=timeHighWater && tick>=tickHighWater && Math.abs((double)tick)<0x1.0p52;
        if(!clockValid){String reason="CLOCK_INVALID frame="+serial+" tick="+tick+" time="+time+" tickHwm="+tickHighWater+" timeHwm="+timeHighWater;owners.keySet().forEach(id->revoke(id,reason));}
        else{timeHighWater=time;tickHighWater=tick;}
        Map<UUID,Decision> decisions=new LinkedHashMap<>();
        for(var input:inputs){
            Owner owner=owners.get(input.owner);
            Decision decision;
            if(owner==null)decision=decision(Reason.LEGACY,input,Double.NaN,null);
            else if(owner.legacy || !input.eligible || !clockValid){reject(input,"INELIGIBLE_OR_CLOCK",serial,tick,time,Double.NaN);decision=decision(Reason.LEGACY_STICKY,input,Double.NaN,null);}
            else if(!input.cached){
                // Cold coverage is the exact normal path, never postponed to its phase.
                if(!input.normalAvailable)reject(input,"COLD_NO_NORMAL",serial,tick,time,Double.NaN);
                decision=decision(owner.legacy?Reason.LEGACY_STICKY:Reason.NORMAL_COLD,input,Double.NaN,null);
            }else if(!Double.isFinite(input.cachedAt) || input.cachedAt>time){
                reject(input,"CACHE_TIME_INVALID",serial,tick,time,Double.NaN);decision=decision(Reason.LEGACY_STICKY,input,Double.NaN,null);
            }else if(input.cachedEpoch!=input.requiredEpoch){
                var grant=ledger.forced(input.owner,serial,tick,input.requiredEpoch,input.quota);
                if(grant==null)reject(input,"FORCED_DENIED",serial,tick,time,Double.NaN);
                decision=decision(owner.legacy?Reason.LEGACY_STICKY:Reason.FORCED,input,Double.NaN,grant);
            }else{
                // Fixed calendar, no catch-up queue and no shifting phase after a forced collect.
                double due=Math.floor(input.cachedAt)+owner.phase;
                if(due<=input.cachedAt)due+=1;
                if(time<due && time-input.cachedAt<=1)decision=decision(Reason.REUSE_PHASE,input,due,null);
                else if(input.normalAvailable)decision=decision(Reason.NORMAL_DUE,input,due,null);
                else{
                    // Only bootstrap within the already charged cold tick can need WARMUP.
                    var grant=Math.floor(input.cachedAt)==tick?ledger.warmup(input.owner,serial,input.requiredEpoch,input.quota):null;
                    if(grant==null)reject(input,"DUE_NO_WORK_NO_WARMUP",serial,tick,time,due);
                    decision=decision(owner.legacy?Reason.LEGACY_STICKY:Reason.WARMUP,input,due,grant);
                }
            }
            decisions.put(input.owner,decision);
        }
        Map<UUID,Decision> stamped=new LinkedHashMap<>();
        decisions.forEach((id,d)->stamped.put(id,new Decision(d.reason,serial,time,d.cacheToken,d.requiredEpoch,d.deadline,d.grant)));
        return last=new Frame(serial,tick,time,stamped);
    }
    private static Decision decision(Reason reason,Input input,double due,HeadlightRefreshLedger.Grant grant) {
        return new Decision(reason,Long.MIN_VALUE,Double.NaN,input.cacheToken,input.requiredEpoch,due,grant);
    }
}
