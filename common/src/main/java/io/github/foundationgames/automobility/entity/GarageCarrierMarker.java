package io.github.foundationgames.automobility.entity;

import net.minecraft.nbt.*;
import java.util.*;

/** Core carrier identity is independent of transaction sequence numbers, especially after rollback. */
public record GarageCarrierMarker(long generation,UUID transaction,Kind kind) {
    public static final String KEY="automobility:garage_carrier";
    public enum Kind { ACTIVE, STORED }
    public static final GarageCarrierMarker LEGACY=new GarageCarrierMarker(0,null,Kind.ACTIVE);
    public static final GarageCarrierMarker LEGACY_STORED=new GarageCarrierMarker(0,null,Kind.STORED);
    public GarageCarrierMarker {
        if(generation<0||kind==null||(generation==0)!=(transaction==null))
            throw new IllegalArgumentException("Invalid Garage carrier marker");
    }
    /** Empty means malformed/future; absent is the exact legacy ACTIVE carrier. */
    public static Optional<GarageCarrierMarker> read(CompoundTag root) {
        if(!root.contains(KEY)) return Optional.of(LEGACY);
        try {
            var raw=root.getCompound(KEY).orElseThrow();
            if(!raw.keySet().equals(Set.of("version","generation","transaction","kind"))
                    ||!(raw.get("version") instanceof IntTag)||raw.getIntOr("version",0)!=1
                    ||!(raw.get("generation") instanceof LongTag)||!(raw.get("transaction") instanceof StringTag)
                    ||!(raw.get("kind") instanceof StringTag)) return Optional.empty();
            var generation=raw.getLongOr("generation",0);var text=raw.getStringOr("transaction","");var transaction=UUID.fromString(text);
            if(generation<1||!transaction.toString().equals(text)) return Optional.empty();
            return Optional.of(new GarageCarrierMarker(generation,transaction,Kind.valueOf(raw.getStringOr("kind",""))));
        } catch(RuntimeException malformed) {return Optional.empty();}
    }
    public static Optional<GarageCarrierMarker> readStored(CompoundTag root) {
        if(!root.contains(KEY))return Optional.of(LEGACY_STORED);
        return read(root).filter(marker->marker.kind()==Kind.STORED);
    }
    public void write(CompoundTag root) {
        if(generation==0){root.remove(KEY);return;}
        var raw=new CompoundTag();raw.putInt("version",1);raw.putLong("generation",generation);
        raw.putString("transaction",transaction.toString());raw.putString("kind",kind.name());root.put(KEY,raw);
    }
}
