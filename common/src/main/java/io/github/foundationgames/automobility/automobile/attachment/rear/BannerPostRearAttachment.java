package io.github.foundationgames.automobility.automobile.attachment.rear;

import io.github.foundationgames.automobility.automobile.attachment.RearAttachmentType;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.screen.SingleSlotScreenHandler;
import io.github.foundationgames.automobility.util.network.CommonPackets;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;

public class BannerPostRearAttachment extends RearAttachment {
    private static final Component UI_TITLE = Component.translatable("container.automobility.banner_post");

    private @Nullable DyeColor baseColor = null;
    private BannerPatternLayers patterns = null;

    public final SimpleContainer inventory = new SimpleContainer(1) {
        @Override
        public void setItem(int slot, ItemStack stack) {
            super.setItem(slot, stack);

            BannerPostRearAttachment.this.setFromItem(stack);
        }

        @Override
        public boolean stillValid(Player player) {
            return AttachmentMenuValidity.isValid(
                    BannerPostRearAttachment.this.wasRemoved(),
                    BannerPostRearAttachment.this.automobile().isAlive(),
                    player.level() == BannerPostRearAttachment.this.automobile().level(),
                    BannerPostRearAttachment.this.automobile().getRearAttachment() == BannerPostRearAttachment.this,
                    player.distanceToSqr(BannerPostRearAttachment.this.automobile())
            );
        }
    };

    public BannerPostRearAttachment(RearAttachmentType<?> type, AutomobileEntity entity) {
        super(type, entity);
    }

    public void sendPacket() {
        if (!this.world().isClientSide()) {
            this.automobile().forPlayersTrackingMe(false, p ->
                    CommonPackets.sendBannerPostAttachmentUpdatePacket(this.automobile(), this.baseColor, this.patterns, p));
        }
    }

    @Override
    public void updatePacketRequested(ServerPlayer player) {
        super.updatePacketRequested(player);

        CommonPackets.sendBannerPostAttachmentUpdatePacket(this.automobile(), this.baseColor, this.patterns, player);
    }

    public void setBanner(DyeColor color, BannerPatternLayers layers) {
        this.baseColor = color;
        this.patterns = layers;
    }

    public void setFromItem(ItemStack stack) {
        if (stack.getItem() instanceof BannerItem banner) {
            this.baseColor = banner.getColor();
        } else {
            this.erase();
            return;
        }

        this.patterns = stack.get(DataComponents.BANNER_PATTERNS);

        if (!this.world().isClientSide()) {
            this.sendPacket();
        }
    }

    public void erase() {
        this.baseColor = null;

        if (!this.world().isClientSide()) {
            this.sendPacket();
        }
    }

    public @Nullable DyeColor getBaseColor() {
        return this.baseColor;
    }

    public BannerPatternLayers getPatterns() {
        return this.patterns;
    }

    @Override
    public void onRemoved() {
        super.onRemoved();

        if (this.world() instanceof ServerLevel level) {
            level.players().forEach(player -> {
                if (player.containerMenu instanceof SingleSlotScreenHandler menu && menu.uses(this.inventory)) {
                    player.closeContainer();
                }
            });

            var pos = this.pos();
            Containers.dropItemStack(this.world(), pos.x, pos.y, pos.z, this.inventory.removeItemNoUpdate(0));
        }
    }

    @Override
    public void writeNbt(CompoundTag nbt, HolderLookup.Provider registry) {
        super.writeNbt(nbt, registry);

        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registry);
        this.inventory.storeAsItemList(output.list("Items", ItemStack.CODEC));
        nbt.put("Banner", output.buildResult());
    }

    @Override
    public void readNbt(CompoundTag nbt, HolderLookup.Provider registry) {
        super.readNbt(nbt, registry);

        var input = TagValueInput.create(ProblemReporter.DISCARDING, registry, nbt.getCompoundOrEmpty("Banner"));
        this.inventory.fromItemList(input.listOrEmpty("Items", ItemStack.CODEC));
    }

    @Override
    public boolean hasMenu(@Nullable Player player) {
        return (player != null && player.isCreative()) || !this.automobile().isDecorative();
    }

    @Override
    public @Nullable MenuProvider createMenu(ContainerLevelAccess ctx) {
        return new SimpleMenuProvider((syncId, playerInv, player) ->
                new SingleSlotScreenHandler(syncId, playerInv, this.inventory), UI_TITLE);
    }
}
