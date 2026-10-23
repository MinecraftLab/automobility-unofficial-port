package io.github.foundationgames.automobility.api.dashboard;

import java.util.*;

/** Actual accepted physical car slot metadata only; never an inventory, media source, decoder or playback permission. */
public record DashboardCassetteSnapshot(VehicleDashboardKey key,UUID slotIncarnation,long revision,State state,
        String itemFingerprint,Optional<UUID> archiveId,Optional<UUID> recordingId,String label,int labelRgb){
    public enum State{EMPTY,OCCUPIED,PENDING_DROP,UNRECOGNIZED}
    public DashboardCassetteSnapshot{
        Objects.requireNonNull(key);Objects.requireNonNull(slotIncarnation);Objects.requireNonNull(state);Objects.requireNonNull(itemFingerprint);
        Objects.requireNonNull(archiveId);Objects.requireNonNull(recordingId);Objects.requireNonNull(label);
        if(slotIncarnation.equals(new UUID(0,0))||revision<0||!itemFingerprint.matches("[0-9a-f]{64}|empty|opaque")||label.length()>384
                ||label.codePoints().anyMatch(c->Character.isISOControl(c))||(labelRgb&0xff000000)!=0)throw new IllegalArgumentException("physical cassette metadata");
        if(state==State.OCCUPIED&&(archiveId.isEmpty()||recordingId.isEmpty()||itemFingerprint.length()!=64)
                ||state!=State.OCCUPIED&&(archiveId.isPresent()||recordingId.isPresent()||!label.isEmpty()))throw new IllegalArgumentException("physical cassette source mismatch");
    }
}
