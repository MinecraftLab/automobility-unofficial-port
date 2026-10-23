package io.github.foundationgames.automobility.entity;

import io.github.foundationgames.automobility.automobile.garage.GarageTransferParticipant.Request;
import net.minecraft.nbt.*;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/** Exact inventory settlement identity. A matching payment receipt is never charged twice. */
public final class GarageInventoryExchange {
    public static final String PLAYER_KEY="automobility:garageInventoryReceipt";
    private static final String INVENTORY="inventoryExchange", RECEIPT="playerExchangeReceipt";
    private GarageInventoryExchange(){}
    public enum State { UNPAID, PAID, UNKNOWN }
    public static boolean present(CompoundTag image){return image.contains(INVENTORY)||image.contains(RECEIPT);}
    private static void snapshot(CompoundTag value)throws IOException {
        if(!value.keySet().equals(Set.of("slots"))||!(value.get("slots") instanceof ListTag slots)
                ||slots.size()!=GarageBatchPlan.INVENTORY_SIZE||value.sizeInBytes()>1_048_576)
            throw new IOException("Invalid bounded Garage inventory image");
        for(var slot:slots)if(!(slot instanceof CompoundTag))throw new IOException("Invalid Garage inventory slot");
    }
    private static CompoundTag part(CompoundTag image,String key)throws IOException {
        return image.getCompound(key).orElseThrow(()->new IOException("Missing Garage inventory settlement field: "+key));
    }
    public static CompoundTag marker(Request request,CompoundTag paid)throws IOException {
        snapshot(paid);var out=new CompoundTag();out.putInt("schema",1);
        out.putString("transaction",request.transaction().toString());out.putLong("generation",request.generation());
        out.putString("vehicle",request.vehicle().toString());out.putString("actor",request.actor().toString());
        out.put("paidInventory",paid.copy());return out;
    }
    public static boolean validMarker(CompoundTag value){
        if(value.isEmpty())return true;
        try{
            if(!value.keySet().equals(Set.of("schema","transaction","generation","vehicle","actor","paidInventory"))
                    ||!(value.get("schema") instanceof IntTag)||value.getIntOr("schema",0)!=1
                    ||!(value.get("generation") instanceof LongTag)||value.getLongOr("generation",0)<1)return false;
            for(var key:Set.of("transaction","vehicle","actor")){
                if(!(value.get(key) instanceof StringTag))return false;var text=value.getStringOr(key,"");
                if(!UUID.fromString(text).toString().equals(text))return false;
            }
            snapshot(part(value,"paidInventory"));return true;
        }catch(IOException|RuntimeException error){return false;}
    }
    public static void attach(Request request,CompoundTag before,CompoundTag after,CompoundTag inventoryBefore,
                              CompoundTag inventoryAfter,CompoundTag previousReceipt)throws IOException {
        snapshot(inventoryBefore);snapshot(inventoryAfter);
        if(!validMarker(previousReceipt)||(!previousReceipt.isEmpty()&&!request.actor().toString().equals(previousReceipt.getStringOr("actor",""))))throw new IOException("Unknown or foreign previous Garage payment receipt");
        before.put(INVENTORY,inventoryBefore.copy());before.put(RECEIPT,previousReceipt.copy());
        after.put(INVENTORY,inventoryAfter.copy());after.put(RECEIPT,marker(request,inventoryAfter));
    }
    public static void validate(Request request,CompoundTag before,CompoundTag after)throws IOException {
        snapshot(part(before,INVENTORY));var paid=part(after,INVENTORY);snapshot(paid);
        if(!validMarker(part(before,RECEIPT))||(!part(before,RECEIPT).isEmpty()&&!request.actor().toString().equals(part(before,RECEIPT).getStringOr("actor","")))||!part(after,RECEIPT).equals(marker(request,paid)))
            throw new IOException("Garage payment receipt does not bind the exact actor/transaction/after image");
    }
    public static State state(Request request,CompoundTag before,CompoundTag after,CompoundTag inventory,CompoundTag receipt)throws IOException {
        validate(request,before,after);snapshot(inventory);
        if(receipt.equals(part(after,RECEIPT)))return State.PAID;
        if(receipt.equals(part(before,RECEIPT))&&inventory.equals(part(before,INVENTORY)))return State.UNPAID;
        return State.UNKNOWN;
    }
    public static CompoundTag afterInventory(CompoundTag image)throws IOException {return part(image,INVENTORY).copy();}
    public static CompoundTag afterReceipt(CompoundTag image)throws IOException {return part(image,RECEIPT).copy();}
}
