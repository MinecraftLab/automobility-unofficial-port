package io.github.foundationgames.automobility.screen;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
/** Virtual presentation only. GarageScreenHandler blocks Creative clone on this target. */
public final class GarageReadonlySlot extends Slot {
    public GarageReadonlySlot(Container container,int index){super(container,index,-10000,-10000);}
    @Override public boolean mayPlace(ItemStack stack){return false;}
    @Override public boolean mayPickup(Player player){return false;}
}
