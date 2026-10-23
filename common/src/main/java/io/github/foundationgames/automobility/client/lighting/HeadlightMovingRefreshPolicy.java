package io.github.foundationgames.automobility.client.lighting;

import java.util.*;

/** Best-effort temporal experiment. No world access, work credits or native collection here. */
final class HeadlightMovingRefreshPolicy {
    static final long PERIOD=50_000_000L;
    enum Decision { LEGACY, REUSE, REFRESH }
    private final List<UUID> owners;
    private final Map<UUID,Entry> entries=new HashMap<>();
    private long reuses,refreshes,fallbacks,maxReuseAge;
    private static final class Entry {
        final Object token;
        long validAt,lastSeen,lastTick,due=-1;
        Entry(Object token,long now,long tick){this.token=token;validAt=lastSeen=now;lastTick=tick;}
    }
    HeadlightMovingRefreshPolicy(Collection<UUID> owners){
        var sorted=new ArrayList<>(owners);sorted.sort(Comparator.naturalOrder());
        if(sorted.isEmpty()||sorted.size()>16||new HashSet<>(sorted).size()!=sorted.size())throw new IllegalArgumentException("Bounded unique cohort");
        this.owners=List.copyOf(sorted);
    }
    List<UUID> owners(){return owners;}
    static boolean newCache(Object previous,Object result){return result!=null&&result!=previous;}
    void revoke(UUID owner){entries.remove(owner);}
    private boolean clock(Entry e,long now,long tick){return now>=0&&now>=e.lastSeen
            && (tick==e.lastTick || e.lastTick!=Long.MAX_VALUE && tick==e.lastTick+1);}
    void exact(UUID owner,Object token,long now,long tick,boolean unusedNormalWork){
        if(!owners.contains(owner)||token==null||now<0)return;
        var e=entries.get(owner);
        if(e!=null&&(e.token!=token||!clock(e,now,tick))){entries.remove(owner);e=null;}
        if(e==null){if(unusedNormalWork)entries.put(owner,new Entry(token,now,tick));return;}
        e.validAt=e.lastSeen=now;e.lastTick=tick;e.due=-1;
    }
    /** Only after an actual certified fresh normal-work collection, never after a mere attempt. */
    void collected(UUID owner,Object token,long now,long tick){
        if(owners.contains(owner)&&token!=null&&now>=0)entries.put(owner,new Entry(token,now,tick));
    }
    Decision dirty(UUID owner,Object token,long now,long tick,boolean qualified,boolean unusedNormalWork){
        var e=entries.get(owner);
        if(!qualified||e==null||e.token!=token||!clock(e,now,tick)){revoke(owner);fallbacks++;return Decision.LEGACY;}
        e.lastSeen=now;e.lastTick=tick;
        if(e.due<0){
            long phase=owners.indexOf(owner)*PERIOD/owners.size();
            long residue=Math.floorMod(now,PERIOD),delay=Math.floorMod(phase-residue,PERIOD);
            // Saturation/overflow is never an excuse to prolong reuse.
            if(now>Long.MAX_VALUE-PERIOD){revoke(owner);fallbacks++;return Decision.LEGACY;}
            e.due=Math.min(now+delay,e.validAt+PERIOD);
        }
        if(now>=e.due){
            if(unusedNormalWork){refreshes++;return Decision.REFRESH;}
            revoke(owner);fallbacks++;return Decision.LEGACY;
        }
        long age=now-e.validAt;
        if(age<0||age>=PERIOD){revoke(owner);fallbacks++;return Decision.LEGACY;}
        maxReuseAge=Math.max(maxReuseAge,age);reuses++;return Decision.REUSE;
    }
    static boolean boundedPose(List<HeadlightBeam> cached,List<HeadlightBeam> actual){
        if(cached.isEmpty()||cached.size()!=actual.size())return false;
        for(int i=0;i<cached.size();i++){
            var a=cached.get(i);var b=actual.get(i);
            if(Double.doubleToLongBits(a.range())!=Double.doubleToLongBits(b.range())
                    ||Double.doubleToLongBits(a.horizontalTangent())!=Double.doubleToLongBits(b.horizontalTangent())
                    ||Double.doubleToLongBits(a.verticalTangent())!=Double.doubleToLongBits(b.verticalTangent())
                    ||a.origin().distanceToSqr(b.origin())>.5*.5
                    ||a.forward().distanceToSqr(b.forward())>.0012
                    ||a.right().distanceToSqr(b.right())>.0012
                    ||a.up().distanceToSqr(b.up())>.0012)return false;
        }
        return true;
    }
    String snapshot(){return "movingReuse="+reuses+" movingRefresh="+refreshes+" movingFallback="+fallbacks+" movingMaxReuseAgeNanos="+maxReuseAge;}
}
