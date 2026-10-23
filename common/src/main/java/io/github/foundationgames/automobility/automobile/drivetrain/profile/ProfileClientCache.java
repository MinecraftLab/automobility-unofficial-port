package io.github.foundationgames.automobility.automobile.drivetrain.profile;

import java.io.ByteArrayOutputStream;
import java.util.*;

/** One accepted table plus one bounded in-flight table; tied to actual connection object and server world session. */
public final class ProfileClientCache{
    public static final int CHUNK_BYTES=4096, MAX_CHUNKS=PhysicalDefinitions.MAX_DEFINITION_BYTES/CHUNK_BYTES, TIMEOUT_TICKS=200;
    public record Offer(UUID worldSession,long epoch,String sha256,int byteLength){
        public Offer{Objects.requireNonNull(worldSession);ProfileDefinitionCodec.checkHash(sha256);if(epoch<1||byteLength<12||byteLength>PhysicalDefinitions.MAX_DEFINITION_BYTES)throw new IllegalArgumentException("profile offer");}
        public int chunks(){return(byteLength+CHUNK_BYTES-1)/CHUNK_BYTES;}
    }
    public record Accepted(Offer offer,PhysicalDefinitions definitions){public Accepted{Objects.requireNonNull(offer);Objects.requireNonNull(definitions);}}
    private Object connection;private UUID worldSession;private Accepted accepted;private Pending pending;
    private static final class Pending{final Offer offer;final long deadline;final ByteArrayOutputStream bytes=new ByteArrayOutputStream();int next;Pending(Offer offer,long now){this.offer=offer;deadline=Math.addExact(now,TIMEOUT_TICKS);}}
    /** Called on trusted connection/world-session transition, never based on an arbitrary chunk. */
    public void bind(Object actualConnection,UUID worldSession){Objects.requireNonNull(actualConnection);Objects.requireNonNull(worldSession);if(connection!=actualConnection||!worldSession.equals(this.worldSession)){connection=actualConnection;this.worldSession=worldSession;accepted=null;pending=null;}}
    public void disconnect(){connection=null;worldSession=null;accepted=null;pending=null;}
    public boolean offer(Object actualConnection,Offer offer,long clientMonotonicTick){
        Objects.requireNonNull(offer);if(clientMonotonicTick<0||clientMonotonicTick>Long.MAX_VALUE-TIMEOUT_TICKS||connection==null||actualConnection!=connection||!offer.worldSession().equals(worldSession))return false;
        if(accepted!=null&&offer.epoch()<accepted.offer().epoch())return false;
        if(accepted!=null&&offer.epoch()==accepted.offer().epoch())return offer.equals(accepted.offer());
        if(pending!=null){if(offer.epoch()<pending.offer.epoch())return false;if(offer.epoch()==pending.offer.epoch())return offer.equals(pending.offer);}
        pending=new Pending(offer,clientMonotonicTick);return true;
    }
    public boolean chunk(Object actualConnection,Offer offer,int index,byte[] bytes,long clientMonotonicTick){
        Objects.requireNonNull(bytes);var p=pending;
        if(actualConnection!=connection||p==null||!p.offer.equals(offer))return false;
        if(clientMonotonicTick<0||clientMonotonicTick>=p.deadline||index!=p.next||index<0||index>=p.offer.chunks())return reject();
        int expected=Math.min(CHUNK_BYTES,p.offer.byteLength()-index*CHUNK_BYTES);if(bytes.length!=expected)return reject();
        p.bytes.writeBytes(bytes);p.next++;
        if(p.next==p.offer.chunks()){
            byte[] complete=p.bytes.toByteArray();try{var definitions=ProfileDefinitionCodec.decode(complete);if(!ProfileDefinitionCodec.hash(complete).equals(p.offer.sha256()))return reject();accepted=new Accepted(p.offer,definitions);pending=null;}catch(IllegalArgumentException invalid){return reject();}
        }return true;
    }
    private boolean reject(){pending=null;return false;}
    public void tick(long clientMonotonicTick){if(pending!=null&&(clientMonotonicTick<0||clientMonotonicTick>=pending.deadline))pending=null;}
    /** Garage and powered prediction MUST resolve through this exact server identity/hash, never local JSON fallback. */
    public Optional<PhysicalDefinitions> lookup(Object actualConnection,UUID session,long epoch,String hash){
        return connection==actualConnection&&accepted!=null&&worldSession.equals(session)&&accepted.offer().epoch()==epoch&&accepted.offer().sha256().equals(hash)?Optional.of(accepted.definitions()):Optional.empty();
    }
    public Optional<Accepted>accepted(){return Optional.ofNullable(accepted);}
    public Optional<Offer>pendingOffer(){return pending==null?Optional.empty():Optional.of(pending.offer);}
    public int nextChunkIndex(){return pending==null?-1:pending.next;}
    public int bufferedBytes(){return pending==null?0:pending.bytes.size();}
}
