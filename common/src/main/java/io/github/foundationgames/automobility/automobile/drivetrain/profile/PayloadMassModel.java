package io.github.foundationgames.automobility.automobile.drivetrain.profile;

import java.util.*;

/** Actual observed membership/counts enter here. No inventory read, mutation or invented mass for unknown units. */
public final class PayloadMassModel{
    public record Passenger(UUID entity,String typeId){public Passenger{Objects.requireNonNull(entity);PhysicalDefinitions.id(typeId);}}
    public record Cargo(String itemId,int count,boolean opaqueContainedInventory){public Cargo{PhysicalDefinitions.id(itemId);if(count<1||count>999)throw new IllegalArgumentException("cargo count");}}
    public record Observation(UUID vehicle,String frontAttachmentId,String rearAttachmentId,List<Passenger> passengers,List<Cargo> localCargo,boolean completeLocalObservation){
        public Observation{Objects.requireNonNull(vehicle);PhysicalDefinitions.id(frontAttachmentId);PhysicalDefinitions.id(rearAttachmentId);passengers=List.copyOf(passengers);localCargo=List.copyOf(localCargo);
            if(passengers.size()>64||localCargo.size()>128)throw new IllegalArgumentException("payload observation bound");
            if(passengers.stream().map(Passenger::entity).distinct().count()!=passengers.size())throw new IllegalArgumentException("duplicate actual rider");}
    }
    public record Result(double knownPassengerKg,double knownCargoKg,double knownAttachmentKg,int unmodelledPassengerCount,
            int unmodelledItemCount,int opaqueContainerCount,boolean unmodelledAttachment,boolean incompleteObservation,boolean poweredPredictionPermitted){
        public double knownSubtotalKg(){return knownPassengerKg+knownCargoKg+knownAttachmentKg;}
        public boolean complete(){return unmodelledPassengerCount==0&&unmodelledItemCount==0&&opaqueContainerCount==0&&!unmodelledAttachment&&!incompleteObservation;}
    }
    private PayloadMassModel(){}
    public static Result evaluate(PhysicalDefinitions definitions,Observation observed){
        Objects.requireNonNull(definitions);Objects.requireNonNull(observed);var rules=definitions.loadRules();double people=0,cargo=0,attachment=0;int unknownPeople=0,unknownItems=0,opaque=0;
        for(var p:observed.passengers()){var kg=rules.passengerKg().get(p.typeId());if(kg==null)unknownPeople++;else people+=kg;}
        for(var c:observed.localCargo()){var kg=rules.itemKg().get(c.itemId());if(kg==null)unknownItems=Math.addExact(unknownItems,c.count());else cargo+=kg*c.count();if(c.opaqueContainedInventory())opaque++;}
        var front=definitions.frontMassKg().get(observed.frontAttachmentId());var rear=definitions.rearMassKg().get(observed.rearAttachmentId());boolean unknownAttachment=front==null||rear==null;
        if(front!=null)attachment+=front;if(rear!=null)attachment+=rear;
        double subtotal=people+cargo+attachment;
        if(!Double.isFinite(subtotal)||subtotal>rules.maximumKnownPayloadKg())throw new IllegalArgumentException("known payload exceeds configured arithmetic bound; never truncate/clamp it");
        boolean complete=unknownPeople==0&&unknownItems==0&&opaque==0&&!unknownAttachment&&observed.completeLocalObservation();
        return new Result(people,cargo,attachment,unknownPeople,unknownItems,opaque,unknownAttachment,!observed.completeLocalObservation(),complete||!rules.strictUnmodelled());
    }
}
