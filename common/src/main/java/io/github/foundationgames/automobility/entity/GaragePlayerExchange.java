package io.github.foundationgames.automobility.entity;
import io.github.foundationgames.automobility.entity.GarageTransferJournal.Entry;
import io.github.foundationgames.automobility.mixin.*;
import io.github.foundationgames.automobility.util.duck.GaragePlayerReceipt;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/** Core owns the item exchange; producer participants never debit or refund an inventory. */
final class GaragePlayerExchange {
    private GaragePlayerExchange(){}
    static CompoundTag snapshot(ServerPlayer actor){return snapshot(actor,GarageBatchPlan.snapshot(actor.getInventory()));}
    static CompoundTag snapshot(ServerPlayer actor,List<ItemStack> slots){
        var out=new CompoundTag();var list=new ListTag();var ops=actor.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for(var stack:slots)list.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops,stack).getOrThrow());out.put("slots",list);return out;
    }
    static CompoundTag marker(ServerPlayer actor){return ((GaragePlayerReceipt)actor).automobility$garageReceipt();}
    static boolean cursorEmpty(ServerPlayer actor){return actor.containerMenu.getCarried().isEmpty();}
    static GarageInventoryExchange.State state(ServerPlayer actor,Entry entry)throws IOException {
        if(!cursorEmpty(actor))return GarageInventoryExchange.State.UNKNOWN;
        return GarageInventoryExchange.state(entry.request(),entry.before(),entry.after(),snapshot(actor),marker(actor));
    }
    static boolean apply(ServerPlayer actor,Entry entry)throws IOException {
        var state=state(actor,entry);if(state==GarageInventoryExchange.State.PAID)return true;
        if(state!=GarageInventoryExchange.State.UNPAID)return false;
        var slots=GarageInventoryExchange.afterInventory(entry.after()).getListOrEmpty("slots");
        var ops=actor.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var decoded=new java.util.ArrayList<ItemStack>();
        for(var slot:slots)decoded.add(ItemStack.OPTIONAL_CODEC.parse(ops,slot).getOrThrow());
        for(int i=0;i<GarageBatchPlan.INVENTORY_SIZE;i++)actor.getInventory().setItem(i,decoded.get(i).copy());
        actor.getInventory().setChanged();
        if(!cursorEmpty(actor)||!snapshot(actor).equals(GarageInventoryExchange.afterInventory(entry.after())))return false;
        ((GaragePlayerReceipt)actor).automobility$garageReceipt(GarageInventoryExchange.afterReceipt(entry.after()));
        return state(actor,entry)==GarageInventoryExchange.State.PAID;
    }
    static boolean checkpoint(ServerPlayer actor,Entry entry,boolean committed)throws IOException {
        if(!actor.level().dimension().identifier().toString().equals(entry.request().dimension()))return false;
        var expected=GarageInventoryExchange.afterReceipt(committed?entry.after():entry.before());
        if(!marker(actor).equals(expected)||!cursorEmpty(actor))return false;
        var list=actor.level().getServer().getPlayerList();
        var storage=((GaragePlayerListStorageAccess)(Object)list).automobility$playerStorage();
        var captured=snapshot(actor);
        // A fresh write ACK also detects a swallowed save failure when the old inventory,
        // payment marker and dimension coincidentally match the current preimage.
        var nonce=java.util.UUID.randomUUID();((GaragePlayerReceipt)actor).automobility$garageSaveNonce(nonce);
        storage.save(actor);
        var root=((GaragePlayerDataDirectoryAccess)(Object)storage).automobility$playerDirectory().toPath().toAbsolutePath().normalize();
        var path=root.resolve(actor.getUUID()+".dat");
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>16_777_216)return false;
        // Never accept load()'s .dat_old fallback as proof that this save succeeded.
        var saved=NbtIo.readCompressed(path,NbtAccounter.create(16_777_216));
        if(saved.contains(GarageInventoryExchange.PLAYER_KEY)&&!(saved.get(GarageInventoryExchange.PLAYER_KEY) instanceof CompoundTag))return false;
        var disk=saved.getCompound(GarageInventoryExchange.PLAYER_KEY).orElseGet(CompoundTag::new);
        if(!nonce.toString().equals(saved.getStringOr("automobility:garageSaveNonce",""))
                ||!nonce.equals(((GaragePlayerReceipt)actor).automobility$garageSaveNonce())
                ||!saved.getStringOr("Dimension","").equals(entry.request().dimension())
                ||!actor.level().dimension().identifier().toString().equals(entry.request().dimension())
                ||!disk.equals(expected)||!marker(actor).equals(expected)||!snapshot(actor).equals(captured)
                ||!(saved.get("Inventory") instanceof ListTag inventory))return false;
        var slots=new java.util.ArrayList<ItemStack>(java.util.Collections.nCopies(GarageBatchPlan.INVENTORY_SIZE,ItemStack.EMPTY));
        var seen=new java.util.HashSet<Integer>();var ops=actor.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for(var raw:inventory){
            if(!(raw instanceof CompoundTag))return false;
            var result=net.minecraft.world.ItemStackWithSlot.CODEC.parse(ops,raw).result();if(result.isEmpty())return false;
            var row=result.get();if(!row.isValidInContainer(GarageBatchPlan.INVENTORY_SIZE)||row.stack().isEmpty()||!seen.add(row.slot()))return false;
            slots.set(row.slot(),row.stack().copy());
        }
        return java.util.Objects.equals(GarageWorldAdmission.uuid(saved),actor.getUUID())&&snapshot(actor,slots).equals(captured);
    }
}
