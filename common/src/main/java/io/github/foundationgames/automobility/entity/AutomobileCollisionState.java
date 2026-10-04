package io.github.foundationgames.automobility.entity;

import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

/** Monotonic collision flags: once a flag is true, further intersections cannot change it. */
final class AutomobileCollisionState {
    private static final BiPredicate<VoxelShape, VoxelShape> INTERSECTS =
            (first, second) -> Shapes.joinIsNotEmpty(first, second, BooleanOp.AND);

    private final VoxelShape ground;
    private final VoxelShape floor;
    private final VoxelShape wall;
    private final VoxelShape stepWall;
    private boolean groundHit;
    private boolean floorHit;
    private boolean wallHit;
    private boolean stepWallHit;

    AutomobileCollisionState(VoxelShape ground, VoxelShape floor, VoxelShape wall, VoxelShape stepWall) {
        this.ground = ground;
        this.floor = floor;
        this.wall = wall;
        this.stepWall = stepWall;
    }

    void addLocalShape(VoxelShape shape, int x, int y, int z) {
        addLocalShape(shape, x, y, z, INTERSECTS);
    }

    // The same path is used by production and differential tests with counted real intersections.
    void addLocalShape(VoxelShape shape, int x, int y, int z,
                       BiPredicate<VoxelShape, VoxelShape> intersects) {
        if (shape.isEmpty() || complete()) {
            return;
        }
        var block = shape.move(x, y, z);
        groundHit = groundHit || intersects.test(block, ground);
        floorHit = floorHit || intersects.test(block, floor);
        wallHit = wallHit || intersects.test(block, wall);
        stepWallHit = stepWallHit || intersects.test(block, stepWall);
    }

    boolean complete() {
        return groundHit && floorHit && wallHit && stepWallHit;
    }

    boolean blockGround() {
        return groundHit;
    }

    boolean onGround(BooleanSupplier entitySupport) {
        // Entity support must still be queried when blocks did not support the car, including
        // when the block scan was skipped because a chunk is unavailable.
        return groundHit || entitySupport.getAsBoolean();
    }

    boolean floorDirectlyBelow() {
        return floorHit;
    }

    boolean touchingWall() {
        return wallHit && stepWallHit;
    }
}
