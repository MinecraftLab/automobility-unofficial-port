package io.github.foundationgames.automobility.screen;

import io.github.foundationgames.automobility.automobile.attachment.rear.BlockRearAttachment;
import io.github.foundationgames.automobility.automobile.attachment.rear.AttachmentMenuValidity;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.BiFunction;

public class AutomobileContainerLevelAccess implements ContainerLevelAccess {
    private final Level world;
    private final AutomobileEntity automobile;
    private final @Nullable BlockRearAttachment attachment;

    public AutomobileContainerLevelAccess(AutomobileEntity automobile) {
        this.world = automobile.level();
        this.automobile = automobile;
        this.attachment = automobile.getRearAttachment() instanceof BlockRearAttachment att ? att : null;
    }

    @Override
    public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> getter) {
        if (!this.hasCurrentAttachment()) {
            return Optional.empty();
        }
        return Optional.of(getter.apply(world, automobile.blockPosition()));
    }

    public BlockState getAttachmentBlockState() {
        return this.hasCurrentAttachment() ? this.attachment.block : Blocks.AIR.defaultBlockState();
    }

    public boolean isValidFor(Player player) {
        return this.attachment != null && AttachmentMenuValidity.isValid(
                this.attachment.wasRemoved(),
                this.automobile.isAlive(),
                player.level() == this.world && this.automobile.level() == this.world,
                this.automobile.getRearAttachment() == this.attachment,
                player.distanceToSqr(this.automobile));
    }

    private boolean hasCurrentAttachment() {
        return this.attachment != null
                && !this.attachment.wasRemoved()
                && this.automobile.isAlive()
                && this.automobile.level() == this.world
                && this.automobile.getRearAttachment() == this.attachment;
    }
}
