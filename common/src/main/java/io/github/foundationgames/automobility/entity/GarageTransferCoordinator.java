package io.github.foundationgames.automobility.entity;

import io.github.foundationgames.automobility.automobile.garage.GarageTransferParticipant.*;
import io.github.foundationgames.automobility.entity.GarageTransferJournal.*;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/** Server-thread orchestration. Repository decisions, never exceptions or client ACKs, choose recovery. */
public final class GarageTransferCoordinator {
    private GarageTransferCoordinator() {}
    public enum Status { APPLIED, ROLLED_BACK, RECOVERY_REQUIRED }
    public record Outcome(Status status, Entry retained, String detail) {}
    public interface CoreWork {
        boolean validateBefore();
        /** Persist and verify a player preimage before publishing any recoverable item intent. */
        default boolean checkpointBeforeIntent() throws IOException { return true; }
        /** Reversible staging only. Never discard the origin or publish/spawn a destination before COMMIT. */
        boolean stageAfterQuiet();
        boolean validateStaged();
        /** After durable COMMIT only: idempotently activate the exact selected carrier/image.
         * Recognize exact before/after images; unknown or partial foreign state must refuse without guessing. */
        boolean ensureAfterQuiet();
        boolean validateAfter();
        boolean ensureBeforeQuiet();
        /** Persist player/entity/receipt images and tx/generation markers before the fence can close. */
        boolean checkpoint() throws IOException;
    }
    private static void need(boolean value,String detail) throws IOException {
        if(!value) throw new IOException(detail);
    }
    private static void need(Verdict value,String detail) throws IOException { need(value==Verdict.READY,detail+": "+value); }
    private static void sameCapsules(Entry entry,Map<String,Prepared> owners) throws IOException {
        need(entry.owners().keySet().equals(owners.keySet()),"Owner set changed");
        for(var id:entry.owners().keySet()) need(entry.owners().get(id).capsule().equals(owners.get(id).capsule()),"Owner capsule changed: "+id);
    }
    private static Entry write(Repository repository,Entry previous,Entry next,Consumer<Entry> observe) throws IOException {
        repository.write(previous,next);observe.accept(next);return next;
    }
    public static Outcome execute(Repository repository,Entry previous,Entry intent,CoreWork work,
                                  Map<String,Prepared> owners,Consumer<Entry> observe) {
        if(intent.decision()!=Decision.UNDECIDED||intent.finalized()) throw new IllegalArgumentException("Fresh intent required");
        var sorted=new TreeMap<>(owners);
        boolean intentPublished=false;
        try {
            sameCapsules(intent,sorted);
            need(work.validateBefore(),"Core preimage changed");
            need(work.checkpointBeforeIntent(),"Core player preimage could not be persisted");
            need(work.validateBefore(),"Core preimage changed during baseline checkpoint");
            var current=write(repository,previous,intent,observe);intentPublished=true;
            for(var owner:sorted.values()) need(owner.validateBeforeApply(),"Owner preimage changed");
            need(work.stageAfterQuiet(),"Core reversible staging refused");
            for(var owner:sorted.values()) need(owner.applyQuiet(),"Owner quiet application refused");
            need(work.validateStaged(),"Core staged image changed");
            for(var owner:sorted.values()) need(owner.validateApplied(),"Owner after-image changed");
            sameCapsules(current,sorted);
            need(work.validateStaged(),"Core preimage changed during final owner callbacks");
            current=write(repository,current,current.decide(Decision.COMMIT),observe);
            return complete(repository,current,work,sorted,observe);
        } catch(Exception error) {
            try {
                var actual=repository.read(intent.request().vehicle());
                if(!intentPublished&&Objects.equals(actual,previous)) {
                    close(sorted);return new Outcome(Status.ROLLED_BACK,previous,"Intent not published: "+error);
                }
                if(actual==null||!actual.request().equals(intent.request()))
                    return new Outcome(Status.RECOVERY_REQUIRED,actual,"Uncertain decision: "+error);
                observe.accept(actual);
                return complete(repository,actual,work,sorted,observe);
            } catch(Exception uncertain) {
                return new Outcome(Status.RECOVERY_REQUIRED,null,error+"; reconciliation: "+uncertain);
            }
        } finally { cleanup(sorted); }
    }
    /** Caller must have acquired fresh recovery leases under the already loaded persistent fence. */
    public static Outcome recover(Repository repository,Entry entry,CoreWork work,
                                  Map<String,Prepared> owners,Consumer<Entry> observe) {
        var sorted=new TreeMap<>(owners);
        try {
            need(entry.equals(repository.read(entry.request().vehicle())),"Recovery journal changed");
            observe.accept(entry);
            if(entry.finalized()) return terminal(entry);
            sameCapsules(entry,sorted);
            return complete(repository,entry,work,sorted,observe);
        } catch(Exception error) {
            return new Outcome(Status.RECOVERY_REQUIRED,entry,"Recovery refused: "+error);
        } finally { cleanup(sorted); }
    }
    private static Outcome complete(Repository repository,Entry current,CoreWork work,
                                     Map<String,Prepared> owners,Consumer<Entry> observe) throws IOException {
        if(current.finalized()) return terminal(current);
        sameCapsules(current,owners);
        if(current.decision()==Decision.UNDECIDED) current=write(repository,current,current.decide(Decision.ROLLBACK),observe);
        if(current.decision()==Decision.COMMIT) {
            need(work.ensureAfterQuiet(),"Unknown Core committed image");
            for(var owner:owners.values()) need(owner.applyQuiet(),"Owner committed image refused");
            need(work.validateAfter(),"Core commit validation failed");
            for(var owner:owners.values()) need(owner.validateApplied(),"Owner commit validation failed");
        } else {
            need(work.ensureBeforeQuiet(),"Unknown Core rollback image");
            for(var owner:owners.values()) need(owner.restoreQuiet(),"Owner quiet rollback refused");
            need(work.validateBefore(),"Core rollback validation failed");
            for(var owner:owners.values()) need(owner.validateBeforeApply(),"Owner rollback validation failed");
        }
        sameCapsules(current,owners);
        for(var item:owners.entrySet()) {
            // Always refresh runtime publication, including a fresh process with a durable earlier ACK.
            need(item.getValue().publish(current.decision()),"Owner publication failed: "+item.getKey());
            if(!current.published().contains(item.getKey())) current=write(repository,current,current.acknowledge(item.getKey()),observe);
        }
        // The global fence remains held after local close. A retiring origin stays alive until
        // owner validation/publication finish; checkpoint may now retire the exact carrier.
        close(owners);
        need(work.checkpoint(),"Core persistence checkpoint failed");
        if(!current.checkpointed()) current=write(repository,current,current.checkpoint(),observe);
        current=write(repository,current,current.finish(),observe);
        return new Outcome(current.decision()==Decision.COMMIT?Status.APPLIED:Status.ROLLED_BACK,current,"Durable decision completed");
    }
    private static Outcome terminal(Entry entry) {
        return new Outcome(entry.decision()==Decision.COMMIT?Status.APPLIED:Status.ROLLED_BACK,entry,"Already finalized; historical images were not replayed");
    }
    private static void close(Map<String,Prepared> owners) throws IOException {
        for(var item:owners.entrySet()) need(item.getValue().closeLocal(),"Owner local close failed: "+item.getKey());
    }
    private static void cleanup(Map<String,Prepared> owners) {
        // This is local cleanup only. Errors never grant a persistent fence release.
        for(var owner:owners.values()) try { owner.closeLocal(); } catch(Exception ignored) { }
    }
}
