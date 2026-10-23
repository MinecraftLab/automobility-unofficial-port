package io.github.foundationgames.automobility.entity;

import io.github.foundationgames.automobility.automobile.garage.GarageTransferParticipant.Decision;
import io.github.foundationgames.automobility.entity.GarageTransferJournal.Entry;

/** Inactive protected receipts are immutable; normal active driving state is deliberately excluded. */
public final class GarageReceiptLineage {
    private GarageReceiptLineage(){}
    public static boolean accepts(Entry previous,GarageStorage.Entry receipt){
        if(receipt==null||receipt.entity()==null||receipt.state()!=GarageStorage.State.PARKED)return false;
        if(previous==null)return !receipt.entity().contains(GarageCarrierMarker.KEY);
        if(!previous.finalized()||previous.decision()==Decision.UNDECIDED)return false;
        try{
            var resolved=previous.decision()==Decision.COMMIT?previous.after():previous.before();
            var carrier=GarageCarrierAdmission.Carrier.read(resolved);
            var original=resolved.getCompound("receipt").orElseThrow();
            return !carrier.active()&&carrier.vehicle().equals(receipt.id())&&carrier.dimension().equals(receipt.dimension())
                    &&original.equals(GarageStorage.encodeEntry(receipt));
        }catch(java.io.IOException|RuntimeException invalid){return false;}
    }
}
