package io.github.foundationgames.automobility.automobile.drivetrain.profile;

import java.util.*;

/** Per-world authoritative table staging. All live affected mechanics must reach a trusted stationary boundary before apply. */
public final class ProfileReloadGate{
    public record Snapshot(UUID worldSession,long epoch,String sha256,PhysicalDefinitions definitions){
        public Snapshot{Objects.requireNonNull(worldSession);ProfileDefinitionCodec.checkHash(sha256);Objects.requireNonNull(definitions);if(epoch<1||!sha256.equals(ProfileDefinitionCodec.hash(ProfileDefinitionCodec.encode(definitions))))throw new IllegalArgumentException("epoch/definition hash");}
        public ProfileClientCache.Offer offer(){return new ProfileClientCache.Offer(worldSession,epoch,sha256,ProfileDefinitionCodec.encode(definitions).length);}
        public List<byte[]>chunks(){byte[] bytes=ProfileDefinitionCodec.encode(definitions);var chunks=new ArrayList<byte[]>();for(int offset=0;offset<bytes.length;offset+=ProfileClientCache.CHUNK_BYTES)chunks.add(Arrays.copyOfRange(bytes,offset,Math.min(bytes.length,offset+ProfileClientCache.CHUNK_BYTES)));return List.copyOf(chunks);}
    }
    public record Boundary(UUID vehicle,UUID physicalMotor,boolean stationary,boolean mechanicallyQuiescent,boolean garageBlocked,boolean energyTransactionOutstanding){
        public Boundary{Objects.requireNonNull(vehicle);/* physicalMotor is null for ICE; neither identity is replaced here. */}
        public boolean ready(){return stationary&&mechanicallyQuiescent&&!garageBlocked&&!energyTransactionOutstanding;}
    }
    private Snapshot active;private PhysicalDefinitions pending;
    public ProfileReloadGate(UUID worldSession,PhysicalDefinitions initial){active=snapshot(worldSession,1,initial);}
    private static Snapshot snapshot(UUID session,long epoch,PhysicalDefinitions definition){return new Snapshot(session,epoch,ProfileDefinitionCodec.hash(ProfileDefinitionCodec.encode(definition)),definition);}
    public Snapshot active(){return active;}
    /** Invalid/future raw remains with the caller's config file and cannot replace a valid active/pending table. */
    public boolean stage(ProfileJson.Read read){Objects.requireNonNull(read);if(!read.accepted())return false;ProfileDefinitionCodec.encode(read.definitions());pending=read.definitions();return true;}
    public boolean pending(){return pending!=null;}
    /** Trusted adapter supplies the complete affected-live-vehicle set on one server tick; no speed, RPM or owner callback is written. */
    public boolean apply(Collection<Boundary> completeAffectedLiveVehicles){
        Objects.requireNonNull(completeAffectedLiveVehicles);if(pending==null)return false;
        var seen=new HashSet<UUID>();for(var b:completeAffectedLiveVehicles)if(!seen.add(b.vehicle())||!b.ready())return false;
        if(active.epoch()==Long.MAX_VALUE)throw new IllegalStateException("profile epoch exhausted; explicit new world session required");
        active=snapshot(active.worldSession(),active.epoch()+1,pending);pending=null;return true;
    }
}
