package io.github.foundationgames.automobility.entity;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** A complete, detached plan for the 36 ordinary inventory slots. Preview never owns real items. */
public final class GarageBatchPlan {
    public static final int INVENTORY_SIZE = 36;
    private final GarageService.Status status;
    private final List<ItemStack> before, after, debits;
    private GarageBatchPlan(GarageService.Status status, List<ItemStack> before, List<ItemStack> after, List<ItemStack> debits) {
        this.status = status; this.before = copy(before); this.after = copy(after); this.debits = copy(debits);
    }
    public static List<ItemStack> snapshot(Inventory inventory) {
        var slots = new ArrayList<ItemStack>(INVENTORY_SIZE);
        for (int i = 0; i < INVENTORY_SIZE; i++) slots.add(inventory.getItem(i).copy());
        return List.copyOf(slots);
    }
    public static GarageBatchPlan plan(List<ItemStack> inventory, List<ItemStack> debits, List<ItemStack> credits) {
        if (inventory.size() != INVENTORY_SIZE) throw new IllegalArgumentException("Expected 36 inventory slots");
        var next = copy(inventory);
        for (var required : debits) {
            int remaining = required.getCount();
            if (required.isEmpty() || remaining <= 0) return denied(GarageService.Status.INVALID_PART, inventory);
            for (var stack : next) {
                if (!ItemStack.isSameItemSameComponents(stack, required)) continue;
                int count = Math.min(remaining, stack.getCount()); stack.shrink(count); remaining -= count;
                if (remaining == 0) break;
            }
            if (remaining != 0) return denied(GarageService.Status.MISSING_PARTS, inventory);
        }
        for (var returned : credits) {
            if (returned.isEmpty()) continue;
            int remaining = returned.getCount(), maximum = Math.min(64, returned.getMaxStackSize());
            for (int pass = 0; pass < 2; pass++) for (int i = 0; i < next.size() && remaining > 0; i++) {
                var target = next.get(i);
                if (pass == 0 && !target.isEmpty() && ItemStack.isSameItemSameComponents(target, returned)) {
                    int amount = Math.min(remaining, Math.max(0, Math.min(maximum, target.getMaxStackSize()) - target.getCount()));
                    target.grow(amount); remaining -= amount;
                } else if (pass == 1 && target.isEmpty()) {
                    int amount = Math.min(remaining, maximum); var stack = returned.copy(); stack.setCount(amount);
                    next.set(i, stack); remaining -= amount;
                }
            }
            if (remaining != 0) return denied(GarageService.Status.INVENTORY_FULL, inventory);
        }
        return new GarageBatchPlan(GarageService.Status.READY, inventory, next, debits);
    }
    /** Engine API2 binds a physical source slot, including the whole original count and payload. */
    public static GarageBatchPlan planEngineAtSlot(List<ItemStack> inventory,int sourceSlot,
                                                  ItemStack expectedWholeSource,ItemStack returned) {
        if(inventory.size()!=INVENTORY_SIZE)throw new IllegalArgumentException("Expected 36 inventory slots");
        if(sourceSlot<0||sourceSlot>=INVENTORY_SIZE||expectedWholeSource==null||expectedWholeSource.isEmpty()
                ||returned==null||returned.isEmpty()||returned.getCount()!=1)return denied(GarageService.Status.INVALID_PART,inventory);
        var actual=inventory.get(sourceSlot);
        if(actual.getCount()!=expectedWholeSource.getCount()||!ItemStack.isSameItemSameComponents(actual,expectedWholeSource))
            return denied(GarageService.Status.STALE,inventory);
        var next=copy(inventory);next.get(sourceSlot).shrink(1);
        var insertion=plan(next,List.of(),List.of(returned));
        if(!insertion.ready())return denied(insertion.status(),inventory);
        var debit=expectedWholeSource.copy();debit.setCount(1);
        return new GarageBatchPlan(GarageService.Status.READY,inventory,insertion.after(),List.of(debit));
    }
    private static GarageBatchPlan denied(GarageService.Status status, List<ItemStack> inventory) {
        return new GarageBatchPlan(status, inventory, inventory, List.of());
    }
    private static ArrayList<ItemStack> copy(List<ItemStack> slots) {
        var result = new ArrayList<ItemStack>(slots.size()); for (var slot : slots) result.add(slot.copy()); return result;
    }
    public GarageService.Status status() { return status; }
    public boolean ready() { return status == GarageService.Status.READY; }
    public List<ItemStack> before() { return List.copyOf(copy(before)); }
    public List<ItemStack> after() { return List.copyOf(copy(after)); }
    public List<ItemStack> debits() { return List.copyOf(copy(debits)); }
    private static boolean equal(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            var a = left.get(i); var b = right.get(i);
            if (a.isEmpty() && b.isEmpty()) continue;
            if (a.getCount() != b.getCount() || !ItemStack.isSameItemSameComponents(a, b)) return false;
        }
        return true;
    }
    public boolean matches(Inventory inventory) { return equal(before, snapshot(inventory)); }
    public boolean matches(List<ItemStack> inventory) { return equal(before, inventory); }
    public boolean commit(Inventory inventory) {
        if (!ready() || !matches(inventory)) return false;
        write(inventory, after); return true;
    }
    /** Assembly rolls back its reservation only if no spawn hook changed any inventory slot. */
    public boolean rollback(Inventory inventory) {
        if (!ready() || !equal(after, snapshot(inventory))) return false;
        write(inventory, before); return true;
    }
    private static void write(Inventory inventory, List<ItemStack> slots) {
        for (int i = 0; i < INVENTORY_SIZE; i++) inventory.setItem(i, slots.get(i).copy());
        inventory.setChanged();
    }
}
