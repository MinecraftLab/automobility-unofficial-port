package io.github.foundationgames.automobility.block;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.entity.HitboxEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;

public class AutomobilePressurePlateBlock extends PressurePlateBlock {
    public AutomobilePressurePlateBlock(Properties properties) {
        super(BlockSetType.IRON, properties);
    }

    @Override
    protected int getSignalStrength(Level level, BlockPos pos) {
        var box = TOUCH_AABB.move(pos);
        return getEntityCount(level, box, AutomobileEntity.class) +
                getEntityCount(level, box, HitboxEntity.class)
                > 0 ? 15 : 0;
    }
}
