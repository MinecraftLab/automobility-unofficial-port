package io.github.foundationgames.automobility.entity;

import io.github.foundationgames.automobility.automobile.garage.GarageTransferParticipant.Decision;
import io.github.foundationgames.automobility.entity.GarageTransferJournal.Entry;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import java.io.IOException;
import java.util.*;

/** Server-thread, actual-instance admission. Journal tombstones survive unloading an admitted instance. */
public final class GarageCarrierAdmission {
    public record Carrier(UUID vehicle,String dimension,boolean active,GarageCarrierMarker marker) {
        public Carrier {
            Objects.requireNonNull(vehicle);Objects.requireNonNull(marker);
            var id=dimension==null?null:Identifier.tryParse(dimension);
            if(id==null||!id.toString().equals(dimension)||active!=(marker.kind()==GarageCarrierMarker.Kind.ACTIVE))
                throw new IllegalArgumentException("Invalid carrier descriptor");
        }
        public CompoundTag image() {
            var out=new CompoundTag();out.putInt("carrierSchema",1);out.putString("vehicle",vehicle.toString());out.putString("dimension",dimension);
            out.putBoolean("active",active);marker.write(out);return out;
        }
        static Carrier read(CompoundTag raw) throws IOException {
            try {
                if(!(raw.get("carrierSchema") instanceof IntTag)||raw.getIntOr("carrierSchema",0)!=1
                        ||!(raw.get("vehicle") instanceof StringTag)||!(raw.get("dimension") instanceof StringTag)
                        ||(!ByteTag.valueOf(true).equals(raw.get("active"))&&!ByteTag.valueOf(false).equals(raw.get("active"))))
                    throw new IOException("Malformed Garage carrier descriptor");
                var text=raw.getStringOr("vehicle","");var id=UUID.fromString(text);
                if(!id.toString().equals(text)) throw new IOException("Noncanonical carrier UUID");
                return new Carrier(id,raw.getStringOr("dimension",""),raw.getBooleanOr("active",false),(raw.getBooleanOr("active",false)?GarageCarrierMarker.read(raw):GarageCarrierMarker.readStored(raw)).orElseThrow());
            } catch(RuntimeException error){throw new IOException("Invalid Garage carrier descriptor",error);}
        }
    }
    private record State(Entry journal,Carrier resolved) {}
    private final Map<UUID,State> states=new HashMap<>();
    private final Map<UUID,Object> admitted=new HashMap<>();
    private final Map<UUID,UUID> volatileFences=new HashMap<>();
    public void observe(Entry entry) throws IOException {
        var id=entry.request().vehicle();var old=states.get(id);
        if(old!=null&&(!old.journal().request().transaction().equals(entry.request().transaction())
                ||!old.journal().equals(entry))) GarageTransferJournal.validateTransition(old.journal(),entry);
        var before=Carrier.read(entry.before());var after=Carrier.read(entry.after());
        if(!id.equals(before.vehicle())||!id.equals(after.vehicle())||!entry.request().dimension().equals(before.dimension())
                ||!entry.request().dimension().equals(after.dimension())||before.marker().generation()>=entry.request().generation()
                ||after.marker().generation()!=entry.request().generation()||!entry.request().transaction().equals(after.marker().transaction()))
            throw new IOException("Carrier identity or generation differs from Garage intent");
        if(old!=null&&!old.journal().request().transaction().equals(entry.request().transaction())
                &&(old.resolved()==null||!old.resolved().equals(before)))throw new IOException("New Garage intent does not continue the resolved carrier");
        switch(entry.request().operation()) {
            case STORE -> {if(!before.active()||after.active())throw new IOException("STORE must change ACTIVE to STORED");}
            case DEPLOY -> {if(before.active()||!after.active())throw new IOException("DEPLOY must change STORED to ACTIVE");}
            case STORED_TUNE -> {
                if(before.active()||after.active()||!entry.request().beforeEngine().equals(entry.request().afterEngine()))
                    throw new IOException("Stored wheel tuning must preserve the inactive carrier and engine");
                GarageInventoryExchange.validate(entry.request(),entry.before(),entry.after());
            }
            case ENGINE_SWAP -> {
                if(before.active()!=after.active())throw new IOException("Engine swap must preserve carrier kind");
                GarageInventoryExchange.validate(entry.request(),entry.before(),entry.after());
                if(!before.marker().equals((before.active()?GarageCarrierMarker.read(entry.before().getCompound("entity").orElseThrow()):GarageCarrierMarker.readStored(entry.before().getCompound("entity").orElseThrow())).orElse(null))
                        ||!after.marker().equals((after.active()?GarageCarrierMarker.read(entry.after().getCompound("entity").orElseThrow()):GarageCarrierMarker.readStored(entry.after().getCompound("entity").orElseThrow())).orElse(null)))throw new IOException("Engine raw/descriptor markers differ");
            }
            default -> throw new IOException("Carrier admission operation not qualified");
        }
        Carrier resolved=null;
        if(entry.finalized()) {
            resolved=Carrier.read(entry.decision()==Decision.COMMIT?entry.after():entry.before());
            if(!id.equals(resolved.vehicle()))throw new IOException("Resolved carrier UUID differs from journal");
            if(!entry.request().dimension().equals(resolved.dimension())||resolved.marker().generation()>entry.request().generation()
                    ||(entry.decision()==Decision.COMMIT&&(resolved.marker().generation()!=entry.request().generation()
                    ||!entry.request().transaction().equals(resolved.marker().transaction()))))throw new IOException("Resolved carrier generation/dimension differs from decision");
        }
        states.put(id,new State(entry,resolved));
        if(resolved==null||!resolved.active())admitted.remove(id);
    }
    public boolean resolvedMatches(UUID vehicle,String dimension,GarageCarrierMarker marker,boolean active){
        if(marker==null||pending(vehicle))return false;
        var state=states.get(vehicle);
        if(state==null)return marker.equals(active?GarageCarrierMarker.LEGACY:GarageCarrierMarker.LEGACY_STORED);
        var resolved=state.resolved();return resolved!=null&&resolved.active()==active&&resolved.dimension().equals(dimension)&&resolved.marker().equals(marker);
    }
    /** UUID-only consumers stay fenced until an exact ACTIVE instance has been admitted. */
    public boolean blocked(UUID vehicle){
        if(pending(vehicle))return true;var state=states.get(vehicle);
        return state!=null&&(state.resolved()==null||!state.resolved().active()||!admitted.containsKey(vehicle));
    }
    public boolean tracked(UUID vehicle){return states.containsKey(vehicle)||volatileFences.containsKey(vehicle);}
    public boolean pending(UUID vehicle){var state=states.get(vehicle);return volatileFences.containsKey(vehicle)||(state!=null&&!state.journal().finalized());}
    public boolean hold(UUID vehicle,UUID token){var state=states.get(vehicle);if(state!=null&&!state.journal().finalized()&&!state.journal().request().transaction().equals(token))return false;var old=volatileFences.putIfAbsent(vehicle,token);return old==null||old.equals(token);}
    public void release(UUID vehicle,UUID token){volatileFences.remove(vehicle,token);}
    /** A pre-load check. Raw rejection must be durably retained before materialization is skipped. */
    public boolean allowsRaw(UUID vehicle,String dimension,GarageCarrierMarker marker) {
        if(marker==null||pending(vehicle))return false;
        var state=states.get(vehicle);
        if(state==null)return marker.equals(GarageCarrierMarker.LEGACY);
        var expected=state.resolved();
        return expected!=null&&expected.active()&&expected.dimension().equals(dimension)&&expected.marker().equals(marker);
    }
    public boolean admit(Object actual,UUID vehicle,String dimension,GarageCarrierMarker marker) {
        if(actual==null||!allowsRaw(vehicle,dimension,marker))return false;
        if(!states.containsKey(vehicle))return true;
        var existing=admitted.putIfAbsent(vehicle,actual);return existing==null||existing==actual;
    }
    public boolean blocked(Object actual,UUID vehicle,String dimension,GarageCarrierMarker marker) {
        if(actual==null||!allowsRaw(vehicle,dimension,marker))return true;
        return states.containsKey(vehicle)&&admitted.get(vehicle)!=actual;
    }
    /** Unloading releases only the exact in-memory binding, never its finalized generation tombstone. */
    public void unload(Object actual,UUID vehicle){admitted.remove(vehicle,actual);}
}
