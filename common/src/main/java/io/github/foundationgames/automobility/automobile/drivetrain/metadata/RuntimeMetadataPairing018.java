package io.github.foundationgames.automobility.automobile.drivetrain.metadata;

import io.github.foundationgames.automobility.automobile.drivetrain.DrivetrainProtocol;
import io.github.foundationgames.automobility.automobile.drivetrain.profile.*;
import java.util.*;

/** One bounded observation per actual car/connection/world/driver grant, accepting either S2C arrival order.
 * This is not a control lease. Its caller must already authenticate the actual connection and accepted Core identity.
 * Duplicate packets never renew freshness. A missing peer/table/payload/identity yields no powered prediction. */
public final class RuntimeMetadataPairing018 {
    public static final int MAX_AGE_TICKS=5;
    private Object connection,world;
    private DrivetrainProtocol.Identity identity;
    private DrivetrainProtocol.Snapshot snapshot;
    private RuntimeMetadata018 metadata;
    private long snapshotAt=-1,metadataAt=-1;
    public record Pair(DrivetrainProtocol.Snapshot snapshot,RuntimeMetadata018 metadata){
        public Pair{Objects.requireNonNull(snapshot);Objects.requireNonNull(metadata);if(!metadata.pairs(snapshot))throw new IllegalArgumentException("unpaired observation");}
    }
    /** Trusted identity rotation (including driver nonce), native connection replacement or dimension replacement clears both packets. */
    public void bind(Object actualConnection,Object actualWorld,DrivetrainProtocol.Identity acceptedIdentity){
        Objects.requireNonNull(actualConnection);Objects.requireNonNull(actualWorld);Objects.requireNonNull(acceptedIdentity);
        if(connection!=actualConnection||world!=actualWorld||!acceptedIdentity.equals(identity)){
            clear();connection=actualConnection;world=actualWorld;identity=acceptedIdentity;
        }
    }
    public void clear(){connection=world=null;identity=null;snapshot=null;metadata=null;snapshotAt=metadataAt=-1;}
    private boolean context(Object actualConnection,Object actualWorld,DrivetrainProtocol.Identity packetIdentity,long now){return now>=0&&connection!=null&&connection==actualConnection&&world==actualWorld&&identity.equals(packetIdentity);}
    public boolean snapshot(Object actualConnection,Object actualWorld,DrivetrainProtocol.Snapshot value,long now){
        Objects.requireNonNull(value);if(!context(actualConnection,actualWorld,value.identity(),now))return false;
        if(snapshot!=null&&(value.sequence()<=snapshot.sequence()||value.serverTick()<snapshot.serverTick()))return false;
        snapshot=value;snapshotAt=now;
        if(metadata!=null&&metadata.approval().snapshotSequence()<value.sequence()){metadata=null;metadataAt=-1;}
        return true;
    }
    public boolean metadata(Object actualConnection,Object actualWorld,RuntimeMetadata018 value,long now){
        Objects.requireNonNull(value);if(!context(actualConnection,actualWorld,value.approval().identity(),now))return false;
        long seq=value.approval().snapshotSequence();
        if(snapshot!=null&&(seq<snapshot.sequence()||value.serverTick()<snapshot.serverTick()))return false;
        if(metadata!=null&&seq<=metadata.approval().snapshotSequence())return false;
        metadata=value;metadataAt=now;return true;
    }
    public Optional<Pair> pair(Object actualConnection,Object actualWorld,long now){
        if(snapshot==null||metadata==null||!context(actualConnection,actualWorld,snapshot.identity(),now)||!fresh(snapshotAt,now)||!fresh(metadataAt,now)||!metadata.pairs(snapshot))return Optional.empty();
        return Optional.of(new Pair(snapshot,metadata));
    }
    public Optional<PhysicalDefinitions.Selection> prediction(Object actualConnection,Object actualWorld,long now,ProfileClientCache cache,
            String actualFrame,String actualWheel,String actualEngine,Optional<UUID>actualMotor){
        return pair(actualConnection,actualWorld,now).filter(p->p.snapshot().predictionPowerPermitted()&&p.metadata().measured().available())
                .flatMap(p->p.metadata().approval().prediction(cache,actualConnection,p.snapshot().identity(),p.snapshot().sequence(),actualFrame,actualWheel,actualEngine,actualMotor));
    }
    private static boolean fresh(long received,long now){return received>=0&&now>=received&&now-received<=MAX_AGE_TICKS;}
}
