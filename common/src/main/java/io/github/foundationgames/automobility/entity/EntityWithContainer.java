package io.github.foundationgames.automobility.entity;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface EntityWithContainer extends Container {
    private boolean garageBlocked(){return this instanceof AutomobileEntity car&&io.github.foundationgames.automobility.automobile.garage.GarageTransferAccess.blocked(car);}

    Container underlyingContainer();

    @Override
    default int getContainerSize() {
        if(garageBlocked())return 0;
        var c = underlyingContainer();
        if (c != null) {
            return c.getContainerSize();
        }

        return 0;
    }

    @Override
    default boolean isEmpty() {
        if(garageBlocked())return true;
        var c = underlyingContainer();
        if (c != null) {
            return c.isEmpty();
        }

        return true;
    }

    @Override
    default ItemStack getItem(int slot) {
        if(garageBlocked())return ItemStack.EMPTY;
        var c = underlyingContainer();
        if (c != null) {
            return c.getItem(slot);
        }

        return ItemStack.EMPTY;
    }

    @Override
    default ItemStack removeItem(int slot, int amount) {
        if(garageBlocked())return ItemStack.EMPTY;
        var c = underlyingContainer();
        if (c != null) {
            return c.removeItem(slot, amount);
        }

        return ItemStack.EMPTY;
    }

    @Override
    default ItemStack removeItemNoUpdate(int slot) {
        if(garageBlocked())return ItemStack.EMPTY;
        var c = underlyingContainer();
        if (c != null) {
            return c.removeItemNoUpdate(slot);
        }

        return ItemStack.EMPTY;
    }

    @Override
    default void setItem(int slot, ItemStack stack) {
        if(garageBlocked())return;
        var c = underlyingContainer();
        if (c != null) {
            c.setItem(slot, stack);
        }
    }

    @Override
    default void setChanged() {
        if(garageBlocked())return;
        var c = underlyingContainer();
        if (c != null) {
            c.setChanged();
        }
    }

    @Override
    default boolean stillValid(Player player) {
        if(garageBlocked())return false;
        var c = underlyingContainer();
        if (c != null) {
            return c.stillValid(player);
        }

        return false;
    }

    @Override
    default void clearContent() {
        if(garageBlocked())return;
        var c = underlyingContainer();
        if (c != null) {
            c.clearContent();
        }
    }

    @Override
    default boolean canPlaceItem(int slot, ItemStack stack) {
        if(garageBlocked())return false;
        var c = underlyingContainer();
        if (c != null) {
            return c.canPlaceItem(slot, stack);
        }

        return false;
    }

    @Override
    default boolean canTakeItem(Container target, int slot, ItemStack stack) {
        if(garageBlocked())return false;
        var c = underlyingContainer();
        if (c != null) {
            return c.canTakeItem(target, slot, stack);
        }

        return false;
    }
}
