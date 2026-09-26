package io.github.foundationgames.automobility.entity;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutomobileCollisionStateTest {
    private static final VoxelShape AIR = Shapes.empty();
    private static final VoxelShape FULL = Shapes.block();
    private static final VoxelShape SLAB = Shapes.box(0, 0, 0, 1, 0.5, 1);
    private static final VoxelShape TOP_SLAB = Shapes.box(0, 0.5, 0, 1, 1, 1);
    private static final VoxelShape STAIRS = Shapes.or(SLAB, Shapes.box(0.5, 0.5, 0, 1, 1, 1));
    private static final VoxelShape WALL = Shapes.box(0.25, 0, 0.25, 0.75, 1.5, 0.75);
    private static final VoxelShape FENCE = Shapes.or(Shapes.box(0.375, 0, 0.375, 0.625, 1.5, 0.625),
            Shapes.box(0, 0.375, 0.4375, 1, 1.25, 0.5625));
    private static final List<VoxelShape> SHAPES = List.of(AIR, FULL, SLAB, TOP_SLAB, STAIRS, WALL, FENCE);

    @Test
    void realShapesMatchExhaustiveLegacyAlgorithmAcrossContactThresholds() {
        int cases = 0;
        double[] heights = {-0.05, -0.04, 0, 0.5, 1, 1.5, 2};
        double[] offsets = {-1e-6, -1e-8, 0, 1e-8, 1e-6};
        for (var shape : SHAPES) {
            for (double height : heights) {
                for (double offset : offsets) {
                    for (double step : new double[]{0, 0.5, 1}) {
                        var probes = probes(new AABB(0.1, height + offset, 0.1, 0.9, height + offset + 0.8, 0.9),
                                new Vec3(0.2, 0, -0.1), step);
                        var blocks = List.of(new Block(shape, 0, 0, 0), new Block(AIR, 1, 0, 0));
                        assertParity(probes, blocks, List.of(), true);
                        // Entity-only support, side contact, exact touching and miss also use real AABBs.
                        for (var support : List.of(new AABB(-1, height - 0.03, -1, 2, height + 0.01, 2),
                                new AABB(0.9, height - 0.04, 0.1, 1.2, height, 0.9),
                                new AABB(2, height - 0.04, 2, 3, height, 3))) {
                            assertParity(probes, blocks, List.of(support), true);
                        }
                        cases += 4;
                    }
                }
            }
        }
        assertEquals(2940, cases);
    }

    @Test
    void mixedTranslatedShapesAndEntitySupportsMatchWithDeterministicSeed() {
        var random = new Random(0xC0111510L);
        for (int i = 0; i < 1000; i++) {
            double x = random.nextInt(-3, 4) + random.nextDouble();
            double y = random.nextInt(-2, 4) + random.nextDouble();
            double z = random.nextInt(-3, 4) + random.nextDouble();
            var probes = probes(new AABB(x, y, z, x + 0.5 + random.nextDouble(), y + 0.8, z + 0.5 + random.nextDouble()),
                    new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5), random.nextDouble());
            var blocks = new ArrayList<Block>();
            for (int j = 0; j < 40; j++) blocks.add(new Block(SHAPES.get(random.nextInt(SHAPES.size())),
                    random.nextInt(-4, 5), random.nextInt(-3, 5), random.nextInt(-4, 5)));
            var entities = List.of(new AABB(x - 0.1, y - 0.2, z - 0.1, x + 0.6, y + (i % 2 == 0 ? 0.01 : -0.1), z + 0.6));
            assertParity(probes, blocks, entities, true);
            assertParity(probes, blocks, entities, false);
        }
    }

    @Test
    void independentWallAndStepContactsStillRequireBothFlags() {
        var probes = new Probes(Shapes.box(0, -0.04, 0, 1, 0, 1), Shapes.box(0.4, -0.05, 0.4, 0.6, 0, 0.6),
                Shapes.box(0, 0.1, 0, 1, 0.3, 1), Shapes.box(0, 0.8, 0, 1, 1.0, 1), new AABB(0, -0.04, 0, 1, 0, 1));
        var state = probes.state();
        state.addLocalShape(Shapes.box(0, 0.1, 0, 1, 0.3, 1), 0, 0, 0);
        assertFalse(state.touchingWall());
        assertFalse(state.complete());
        state.addLocalShape(Shapes.box(0, 0.8, 0, 1, 1, 1), 0, 0, 0);
        assertTrue(state.touchingWall());
        assertFalse(state.complete(), "wall flags alone must not stop the scan before ground/floor");
        assertParity(probes, List.of(new Block(Shapes.box(0, 0.1, 0, 1, 0.3, 1), 0, 0, 0),
                new Block(Shapes.box(0, 0.8, 0, 1, 1, 1), 0, 0, 0)), List.of(), true);
    }

    @Test
    void airDoesNoIntersectionsAndFullySatisfiedStateEndsScan() {
        var probes = probes(new AABB(0, 0.5, 0, 1, 1.3, 1), Vec3.ZERO, 0.5);
        var air = new ArrayList<Block>();
        for(int i = 0; i < 100; i++) air.add(new Block(AIR, i, 0, 0));
        var oldAir = legacy(probes, air, List.of(), true);
        var newAir = optimized(probes, air, List.of(), true);
        assertEquals(400, oldAir.intersections());
        assertEquals(0, newAir.intersections());
        assertEquals(oldAir.flags(), newAir.flags());

        var enclosed = new ArrayList<Block>();
        enclosed.add(new Block(Shapes.box(-1, -1, -1, 2, 3, 2), 0, 0, 0));
        enclosed.addAll(air);
        var oldEnclosed = legacy(probes, enclosed, List.of(), true);
        var newEnclosed = optimized(probes, enclosed, List.of(), true);
        assertEquals(oldEnclosed.flags(), newEnclosed.flags());
        assertEquals(404, oldEnclosed.intersections());
        assertEquals(4, newEnclosed.intersections());
        assertEquals(1, newEnclosed.blockVisits());
        assertEquals(0, newEnclosed.entityQueries());
    }

    @Test
    void repeatedFloorUsesPerFlagShortCircuitWithoutStoppingUnresolvedWalls() {
        var probes = probes(new AABB(0, 1, 0, 1, 1.8, 1), Vec3.ZERO, 0.5);
        var blocks = new ArrayList<Block>();
        for(int i = 0; i < 100; i++) blocks.add(new Block(FULL, 0, 0, 0));
        var old = legacy(probes, blocks, List.of(), true);
        var now = optimized(probes, blocks, List.of(), true);
        assertEquals(old.flags(), now.flags());
        assertEquals(400, old.intersections());
        assertEquals(202, now.intersections());
        assertEquals(100, now.blockVisits());
        assertEquals(0, now.entityQueries());
    }

    @Test
    void unloadedChunksStillAllowEntitySupportWithoutPromotingFloorOrWall() {
        var probes = probes(new AABB(0, 1, 0, 1, 1.8, 1), Vec3.ZERO, 0.5);
        var blocks = List.of(new Block(FULL, 0, 0, 0));
        var entities = List.of(new AABB(0, 0, 0, 1, 1, 1));
        var result = optimized(probes, blocks, entities, false);
        assertEquals(new Flags(true, false, false), result.flags());
        assertEquals(1, result.entityQueries());
        assertEquals(0, result.blockVisits());
        assertParity(probes, blocks, entities, false);
    }

    private static void assertParity(Probes probes, List<Block> blocks, List<AABB> entities, boolean loaded) {
        var old = legacy(probes, blocks, entities, loaded);
        var now = optimized(probes, blocks, entities, loaded);
        assertEquals(old.flags(), now.flags(), () -> "loaded=" + loaded + " probes=" + probes + " blocks=" + blocks);
        assertTrue(now.intersections() <= old.intersections());
        assertTrue(now.blockVisits() <= old.blockVisits());
    }

    // Literal pre-optimization algorithm: four eager intersections per block, unconditional entity query.
    private static Result legacy(Probes p, List<Block> blocks, List<AABB> entities, boolean loaded) {
        boolean ground = false, floor = false, wall = false, step = false;
        int intersections = 0, visits = 0;
        if(loaded) for(var block : blocks) {
            var shape = block.shape().move(block.x(), block.y(), block.z());
            ground |= Shapes.joinIsNotEmpty(shape, p.ground(), BooleanOp.AND);
            floor |= Shapes.joinIsNotEmpty(shape, p.floor(), BooleanOp.AND);
            wall |= Shapes.joinIsNotEmpty(shape, p.wall(), BooleanOp.AND);
            step |= Shapes.joinIsNotEmpty(shape, p.step(), BooleanOp.AND);
            intersections += 4;
            visits++;
        }
        ground |= entities.stream().anyMatch(e -> e.intersects(p.groundBox()));
        return new Result(new Flags(ground, floor, wall && step), intersections, visits, 1);
    }

    private static Result optimized(Probes p, List<Block> blocks, List<AABB> entities, boolean loaded) {
        var state = p.state();
        var intersections = new AtomicInteger();
        var entityQueries = new AtomicInteger();
        int visits = 0;
        if(loaded) for(var block : blocks) {
            visits++;
            state.addLocalShape(block.shape(), block.x(), block.y(), block.z(), (a, b) -> {
                intersections.incrementAndGet();
                return Shapes.joinIsNotEmpty(a, b, BooleanOp.AND);
            });
            if(state.complete()) break;
        }
        boolean ground = state.onGround(() -> {
            entityQueries.incrementAndGet();
            return entities.stream().anyMatch(e -> e.intersects(p.groundBox()));
        });
        return new Result(new Flags(ground, state.floorDirectlyBelow(), state.touchingWall()),
                intersections.get(), visits, entityQueries.get());
    }

    private static Probes probes(AABB b, Vec3 velocity, double step) {
        var ground = new AABB(b.minX, b.minY - 0.04, b.minZ, b.maxX, b.minY, b.maxZ);
        double width = (b.getXsize() + b.getZsize()) * 0.5f;
        var floor = new AABB(b.minX + width * 0.94, b.minY - 0.05, b.minZ + width * 0.94,
                b.maxX - width * 0.94, b.minY, b.maxZ - width * 0.94);
        var wall = Shapes.create(b.deflate(0.05).move(velocity.normalize().scale(0.12)));
        return new Probes(Shapes.create(ground), Shapes.create(floor), wall, wall.move(0, step - 0.05, 0), ground);
    }

    private record Probes(VoxelShape ground, VoxelShape floor, VoxelShape wall, VoxelShape step, AABB groundBox) {
        AutomobileCollisionState state() { return new AutomobileCollisionState(ground, floor, wall, step); }
    }
    private record Block(VoxelShape shape, int x, int y, int z) {}
    private record Flags(boolean ground, boolean floor, boolean wall) {}
    private record Result(Flags flags, int intersections, int blockVisits, int entityQueries) {}
}
