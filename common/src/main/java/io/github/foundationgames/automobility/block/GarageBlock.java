package io.github.foundationgames.automobility.block;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.platform.Platform;
import io.github.foundationgames.automobility.screen.GarageScreenHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class GarageBlock extends Block {
    public static final double SERVICE_RADIUS = 6;

    public GarageBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return InteractionResult.PASS;
        var cars = level.getEntitiesOfClass(AutomobileEntity.class, new AABB(pos).inflate(SERVICE_RADIUS),
                car -> car.isAlive() && car.distanceToSqr(Vec3.atCenterOf(pos)) <= SERVICE_RADIUS * SERVICE_RADIUS);
        // Never silently service the wrong car in a crowded garage.
        if (cars.size() != 1) {
            player.sendOverlayMessage(Component.translatable(cars.isEmpty()
                    ? "garage.automobility.no_vehicle" : "garage.automobility.multiple_vehicles"));
            return InteractionResult.CONSUME;
        }
        var car = cars.getFirst();
        var data = new GarageScreenHandler.OpeningData(pos, car.getId(), car.getUUID(), car.getDisplayName());
        player.openMenu(Platform.get().extendedMenuProvider(Component.translatable("block.automobility.garage"),
                (id, inv, user) -> new GarageScreenHandler(id, inv, data), user -> data));
        return InteractionResult.CONSUME;
    }
}
