package io.github.foundationgames.automobility.api.drivetrain.profile;

import io.github.foundationgames.automobility.automobile.attachment.rear.BannerPostRearAttachment;
import io.github.foundationgames.automobility.automobile.drivetrain.profile.PayloadMassModel;
import io.github.foundationgames.automobility.automobile.garage.GarageTransferAccess;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** Actual server membership and physical local cargo only. Never opens a menu, remote ender inventory or nested storage. */
public final class VehiclePayloadReader{
    public record Read(Optional<PayloadMassModel.Observation> observation,String diagnostic){public Read{Objects.requireNonNull(observation);Objects.requireNonNull(diagnostic);}}
    private VehiclePayloadReader(){}
    public static Read capture(AutomobileEntity car){
        Objects.requireNonNull(car);
        if(!(car.level() instanceof ServerLevel level)||!level.getServer().isSameThread()||car.isRemoved()||level.getEntity(car.getUUID())!=car||GarageTransferAccess.blocked(car))return unavailable("payload_context_unavailable");
        var front=car.getFrontAttachment();var rear=car.getRearAttachment();if(front==null||rear==null)return unavailable("payload_attachment_unavailable");
        var members=List.copyOf(car.getPassengers());if(members.size()>64)return unavailable("payload_passenger_bound");
        var people=new ArrayList<PayloadMassModel.Passenger>();var cargo=new ArrayList<PayloadMassModel.Cargo>();boolean complete=true;
        try{
            for(Entity passenger:members){if(passenger.isRemoved()||passenger.level()!=level||passenger.getVehicle()!=car)return unavailable("payload_membership_changed");var id=BuiltInRegistries.ENTITY_TYPE.getKey(passenger.getType());if(id==null)return unavailable("payload_passenger_type_unavailable");people.add(new PayloadMassModel.Passenger(passenger.getUUID(),id.toString()));}
            Container local=car.underlyingContainer();
            // A banner is a separate real one-slot inventory; remote ender-chest menus are deliberately absent.
            if(rear instanceof BannerPostRearAttachment banner){if(local!=null)return unavailable("payload_container_ambiguity");local=banner.inventory;}
            if(local!=null){
                int size=local.getContainerSize();if(size<0||size>128)return unavailable("payload_container_bound");var before=new ArrayList<ItemStack>();
                for(int i=0;i<size;i++){
                    ItemStack stack=local.getItem(i);before.add(stack.copy());if(stack.isEmpty())continue;
                    if(stack.getCount()<1||stack.getCount()>999){complete=false;continue;}
                    var item=BuiltInRegistries.ITEM.getKey(stack.getItem());if(item==null){complete=false;continue;}
                    var stored=stack.get(DataComponents.CONTAINER);var bundle=stack.get(DataComponents.BUNDLE_CONTENTS);
                    boolean unknownContained=stored!=null&&stored.nonEmptyItems().iterator().hasNext()||bundle!=null&&!bundle.isEmpty();
                    cargo.add(new PayloadMassModel.Cargo(item.toString(),stack.getCount(),unknownContained));
                }
                if(local.getContainerSize()!=size)return unavailable("payload_inventory_changed");
                for(int i=0;i<size;i++)if(!ItemStack.matches(before.get(i),local.getItem(i)))return unavailable("payload_inventory_changed");
                Container current=rear instanceof BannerPostRearAttachment banner?banner.inventory:car.underlyingContainer();if(current!=local)return unavailable("payload_inventory_changed");
            }
            if(car.level()!=level||car.isRemoved()||level.getEntity(car.getUUID())!=car||car.getFrontAttachment()!=front||car.getRearAttachment()!=rear||!members.equals(car.getPassengers())||GarageTransferAccess.blocked(car))return unavailable("payload_context_changed");
            return new Read(Optional.of(new PayloadMassModel.Observation(car.getUUID(),front.type.getId().toString(),rear.type.getId().toString(),people,cargo,complete)),complete?"":"payload_observation_incomplete");
        }catch(RuntimeException foreignContainer){return unavailable("payload_observation_unavailable");}
    }
    private static Read unavailable(String diagnostic){return new Read(Optional.empty(),diagnostic);}
}
