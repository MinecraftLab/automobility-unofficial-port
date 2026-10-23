package io.github.foundationgames.automobility.api.dashboard;
import java.util.*;

/** Read-only accepted physical-slot/emitter proof projected onto the already accepted finite media clock. */
public record DashboardCassetteMotion027(VehicleDashboardKey key,UUID slotIncarnation,long slotRevision,
        String slotFingerprint,long emitterRevision,String trackSha256,long positionMillis,long durationMillis,boolean paused){
    public DashboardCassetteMotion027{
        Objects.requireNonNull(key);Objects.requireNonNull(slotIncarnation);Objects.requireNonNull(slotFingerprint);Objects.requireNonNull(trackSha256);
        if(slotIncarnation.equals(new UUID(0,0))||slotRevision<0||emitterRevision<0||!slotFingerprint.matches("[0-9a-f]{64}")
                ||!trackSha256.matches("[0-9a-f]{64}")||positionMillis<0||durationMillis<=0||positionMillis>durationMillis)
            throw new IllegalArgumentException("physical cassette motion");
    }
    public boolean matches(DashboardCassetteSnapshot slot){return slot.key().equals(key)&&slot.state()==DashboardCassetteSnapshot.State.OCCUPIED
            &&slot.slotIncarnation().equals(slotIncarnation)&&slot.revision()==slotRevision&&slot.itemFingerprint().equals(slotFingerprint);}
    /** Phase derives only from the finite source position. Pausing therefore freezes it; EOF never wraps. */
    public float leftAngle(){return (float)((positionMillis%1000)*Math.PI*2/1000);}
    public float rightAngle(){return (float)((positionMillis%2000)*Math.PI*2/2000);}
}
