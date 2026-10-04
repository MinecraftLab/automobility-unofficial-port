package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.Automobility;
import io.github.foundationgames.automobility.automobile.AutomobileStats;
import io.github.foundationgames.automobility.block.AutomobilityBlocks;
import io.github.foundationgames.automobility.block.GarageBlock;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.entity.GarageService;
import io.github.foundationgames.automobility.item.AutomobilityItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Menu inventory contains real input/output only; display slots are never withdrawable. */
public class GarageScreenHandler extends AbstractContainerMenu {
    private final Level level;
    private final Player owner;
    private final OpeningData opening;
    private final SimpleContainer inventory = new SimpleContainer(2);
    private final SimpleContainer display = new SimpleContainer(3);
    private final DataSlot status = DataSlot.standalone();
    private final DataSlot requiredCount = DataSlot.standalone();
    private GarageService.Components expected;
    private boolean closed;

    public GarageScreenHandler(int id, Inventory playerInventory, OpeningData data) {
        super(Automobility.GARAGE_SCREEN.require(), id);
        this.owner = playerInventory.player;
        this.level = owner.level();
        this.opening = data;
        this.addSlot(new Slot(inventory, 0, 26, 104) {
            @Override public boolean mayPlace(ItemStack stack) { return GarageService.accepts(stack); }
        });
        this.addSlot(new Slot(inventory, 1, 206, 104) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int index = 0; index < 3; index++) {
            this.addSlot(new Slot(display, index, 26 + index * 36, 28) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 44 + col * 18, 162 + row * 18));
        }
        for (int col = 0; col < 9; col++) this.addSlot(new Slot(playerInventory, col, 44 + col * 18, 220));
        this.addDataSlot(status);
        this.addDataSlot(requiredCount);
        status.set(GarageService.Status.INSERT_PART.ordinal());
        var car = vehicle();
        if (!level.isClientSide() && car != null) {
            expected = car.garageComponents();
            refreshDisplay();
            refreshStatus();
        }
    }

    private AutomobileEntity vehicle() {
        var entity = level.getEntity(opening.entityId());
        return entity instanceof AutomobileEntity car && car.getUUID().equals(opening.uuid()) ? car : null;
    }

    @Override
    public boolean stillValid(Player player) {
        if (closed || player != owner) return false;
        if (level.isClientSide()) return true;
        var car = vehicle();
        return car != null && car.isAlive() && player.level() == level
                && car.distanceToSqr(Vec3.atCenterOf(opening.pos())) <= GarageBlock.SERVICE_RADIUS * GarageBlock.SERVICE_RADIUS
                && car.distanceToSqr(player) <= 64
                && stillValid(ContainerLevelAccess.create(level, opening.pos()), player, AutomobilityBlocks.GARAGE.require());
    }

    private boolean permitted(AutomobileEntity car) {
        return owner.mayBuild() && !owner.isSpectator() && !car.isDecorative()
                && (!car.isInvulnerable() || owner.isCreative())
                && level.mayInteract(owner, opening.pos()) && level.mayInteract(owner, car.blockPosition());
    }

    private GarageService.Plan refreshStatus() {
        var car = vehicle();
        GarageService.Plan plan;
        if (!stillValid(owner) || car == null || !permitted(car)) {
            plan = new GarageService.Plan(GarageService.Status.PROTECTED, null, ItemStack.EMPTY, 0);
        } else {
            plan = GarageService.plan(car, expected, inventory.getItem(0), inventory.getItem(1).isEmpty());
        }
        status.set(plan.status().ordinal());
        requiredCount.set(plan.requiredCount());
        return plan;
    }

    private void refreshDisplay() {
        if (expected == null) return;
        display.setItem(0, expected.frameStack());
        display.setItem(1, expected.wheelStack());
        display.setItem(2, expected.engineStack());
    }

    @Override
    public void broadcastChanges() {
        if (!level.isClientSide() && !closed) refreshStatus();
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (level.isClientSide() || button != 0 || player != owner || player.containerMenu != this || !stillValid(player)) return false;
        // Recompute on the server immediately before committing; never trust a client preview or cached READY status.
        var plan = refreshStatus();
        if (plan.status() != GarageService.Status.READY) {
            broadcastChanges();
            return false;
        }
        var car = vehicle();
        var parts = plan.replacement();
        car.setComponents(parts.frame(), parts.wheel(), parts.engine());
        inventory.getItem(0).shrink(plan.requiredCount());
        inventory.setItem(1, plan.returnedPart());
        expected = parts;
        refreshDisplay();
        broadcastChanges();
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!closed && !level.isClientSide()) {
            // Not gated by the block still existing: destruction/disconnect must also return both real stacks.
            clearContainer(player, inventory);
        }
        closed = true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || (index >= 2 && index < 5)) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var original = stack.copy();
        if (index < 2) {
            if (!moveItemStackTo(stack, 5, 41, true)) return ItemStack.EMPTY;
        } else if (GarageService.accepts(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (index < 32) {
            if (!moveItemStackTo(stack, 32, 41, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 5, 32, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    public GarageService.Status getStatus() {
        var values = GarageService.Status.values();
        return status.get() >= 0 && status.get() < values.length ? values[status.get()] : GarageService.Status.INVALID_PART;
    }

    public int getRequiredCount() { return requiredCount.get(); }
    public Component vehicleName() { return opening.name(); }

    public AutomobileStats currentStats() { return stats(false); }
    public AutomobileStats previewStats() { return stats(true); }

    private AutomobileStats stats(boolean preview) {
        var frame = AutomobilityItems.AUTOMOBILE_FRAME.require().getComponent(display.getItem(0), level.registryAccess());
        var wheel = AutomobilityItems.AUTOMOBILE_WHEEL.require().getComponent(display.getItem(1), level.registryAccess());
        var engine = AutomobilityItems.AUTOMOBILE_ENGINE.require().getComponent(display.getItem(2), level.registryAccess());
        if (frame.isEmpty() || wheel.isEmpty() || engine.isEmpty()) return null;
        var input = inventory.getItem(0);
        if (preview) {
            if (input.is(AutomobilityItems.AUTOMOBILE_WHEEL.require())) {
                wheel = AutomobilityItems.AUTOMOBILE_WHEEL.require().getComponent(input, level.registryAccess());
            } else if (input.is(AutomobilityItems.AUTOMOBILE_ENGINE.require())) {
                engine = AutomobilityItems.AUTOMOBILE_ENGINE.require().getComponent(input, level.registryAccess());
            } else return null;
            if (wheel.isEmpty() || engine.isEmpty()) return null;
        }
        var result = new AutomobileStats();
        result.from(frame, wheel, engine);
        return result;
    }

    public record OpeningData(BlockPos pos, int entityId, UUID uuid, Component name) {
        public static final StreamCodec<RegistryFriendlyByteBuf, OpeningData> STREAM_CODEC =
                StreamCodec.ofMember(OpeningData::encode, OpeningData::decode);
        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(pos);
            buffer.writeVarInt(entityId);
            buffer.writeUUID(uuid);
            ComponentSerialization.STREAM_CODEC.encode(buffer, name);
        }
        private static OpeningData decode(RegistryFriendlyByteBuf buffer) {
            return new OpeningData(buffer.readBlockPos(), buffer.readVarInt(), buffer.readUUID(), ComponentSerialization.STREAM_CODEC.decode(buffer));
        }
    }
}
