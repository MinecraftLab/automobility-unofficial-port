package io.github.foundationgames.automobility.automobile.attachment.front;

import io.github.foundationgames.automobility.automobile.attachment.FrontAttachmentType;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class GrassCutterFrontAttachment extends BaseHarvesterFrontAttachment {
    public GrassCutterFrontAttachment(FrontAttachmentType<?> type, AutomobileEntity automobile) {
        super(type, automobile);
    }

    @Override
    public boolean canHarvest(BlockState state) {
        if (state.getBlock() instanceof CropBlock) {
            return false;
        }

        return state.is(BlockTags.FLOWERS)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH)
                || state.getBlock() instanceof BushBlock;
    }

    @Override
    public void onBlockHarvested(BlockState state, BlockPos pos, List<ItemStack> drops) {
        var dropPos = this.pos();
        for (var drop : drops) {
            this.dropOrTransfer(drop, dropPos);
        }
    }
}
