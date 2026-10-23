package io.github.foundationgames.automobility.automobile.drivetrain.profile;

import io.github.foundationgames.automobility.automobile.drivetrain.DrivetrainProtocol;
import java.util.*;

/** Aggregate approved payload metadata, to travel with the same authoritative mechanical snapshot. Never sends inventory contents. */
public record ProfilePayloadApproval(DrivetrainProtocol.Identity identity,long snapshotSequence,ProfileViews.Reference definition,
        String frameId,String wheelId,String engineId,Optional<UUID>physicalMotor,PayloadMassModel.Result payload){
    public ProfilePayloadApproval{
        Objects.requireNonNull(identity);Objects.requireNonNull(definition);Objects.requireNonNull(physicalMotor);Objects.requireNonNull(payload);
        if(snapshotSequence<1||!identity.worldInstance().equals(definition.worldSession()))throw new IllegalArgumentException("payload snapshot identity");
        PhysicalDefinitions.id(frameId);PhysicalDefinitions.id(wheelId);PhysicalDefinitions.id(engineId);
        if(physicalMotor.filter(id->id.equals(new UUID(0,0))).isPresent())throw new IllegalArgumentException("physical motor identity");
        PhysicalDefinitions.bounded(payload.knownPassengerKg(),0,100000,"approved passenger mass");PhysicalDefinitions.bounded(payload.knownCargoKg(),0,100000,"approved cargo mass");PhysicalDefinitions.bounded(payload.knownAttachmentKg(),0,100000,"approved attachment mass");
        PhysicalDefinitions.bounded(payload.knownSubtotalKg(),0,100000,"approved payload subtotal");
        if(payload.unmodelledPassengerCount()<0||payload.unmodelledPassengerCount()>64||payload.unmodelledItemCount()<0||payload.unmodelledItemCount()>127872||payload.opaqueContainerCount()<0||payload.opaqueContainerCount()>128)throw new IllegalArgumentException("approved unknown counts");
    }
    /** Freshness is the calling Runtime's accepted snapshot/PredictionGuard decision, not client world time. */
    public Optional<PhysicalDefinitions.Selection>prediction(ProfileClientCache cache,Object actualConnection,DrivetrainProtocol.Identity actualIdentity,
            long acceptedSnapshotSequence,String actualFrame,String actualWheel,String actualEngine,Optional<UUID>actualPhysicalMotor){
        if(!identity.equals(actualIdentity)||snapshotSequence!=acceptedSnapshotSequence||!frameId.equals(actualFrame)||!wheelId.equals(actualWheel)||!engineId.equals(actualEngine)||!physicalMotor.equals(actualPhysicalMotor)||!payload.poweredPredictionPermitted())return Optional.empty();
        var definitions=cache.lookup(actualConnection,definition.worldSession(),definition.epoch(),definition.sha256()).orElse(null);
        if(definitions==null||definitions.loadRules().strictUnmodelled()&&!payload.complete())return Optional.empty();
        return definitions.resolve(actualFrame,actualWheel,actualEngine).filter(selection->selection.engine().electric()==null||physicalMotor.isPresent());
    }
}
