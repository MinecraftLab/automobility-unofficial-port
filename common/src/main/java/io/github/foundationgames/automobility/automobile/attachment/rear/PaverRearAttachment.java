package io.github.foundationgames.automobility.automobile.attachment.rear;

import io.github.foundationgames.automobility.automobile.attachment.RearAttachmentType;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.component.BlockTransformers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class PaverRearAttachment extends BasePlowRearAttachment {
    public PaverRearAttachment(RearAttachmentType<?> type, AutomobileEntity entity) {
        super(type, entity);
    }

    @Override
    public SoundEvent plowSound() {
        return SoundEvents.SHOVEL_FLATTEN.value();
    }

    @Override
    public double searchHeight() {
        return -0.25;
    }

    @Override
    public BlockState plowResult(BlockPos pos, BlockState state) {
        if (!this.world().getBlockState(pos.above()).isAir()) {
            return state;
        }

        // Read the world's data-driven shovel transformations instead of the removed
        // ShovelItem table. BasePlowRearAttachment still owns permission checks and writes.
        var world = this.world();
        var transformer = world.registryAccess().lookupOrThrow(Registries.BLOCK_TRANSFORMER)
                .getOrThrow(BlockTransformers.SHOVEL).value();
        for (var transform : transformer.transforms()) {
            if (transform.disallowedFaces().contains(Direction.UP)) {
                continue;
            }
            var result = transform.blockStateProvider().value().getOptionalState(world, world.getRandom(), pos);
            if (result != null) {
                return transform.updateFromNeighbors() ? Block.updateFromNeighbourShapes(result, world, pos) : result;
            }
        }
        return state;
    }
}
