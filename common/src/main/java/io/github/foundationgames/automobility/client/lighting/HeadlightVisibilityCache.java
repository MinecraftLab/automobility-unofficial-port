package io.github.foundationgames.automobility.client.lighting;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Objects;
import java.util.function.BiFunction;

/**
 * A bounded snapshot for one synchronous surface rebuild and one automobile.
 * Never retain this object between rebuilds: state, neighbors and entity context
 * must remain stable for the duration of its use.
 */
public final class HeadlightVisibilityCache {
    static final int MAX_ENTRIES = 16_384;

    private final BlockGetter originalWorld;
    private final Entity automobile;
    private final CollisionContext testContext;
    private final Long2ObjectOpenHashMap<CellFacts> cells = new Long2ObjectOpenHashMap<>(256);
    private final BiFunction<ClipContext, BlockPos, BlockHitResult> visit;

    public HeadlightVisibilityCache(BlockGetter originalWorld, Entity automobile) {
        this.originalWorld = Objects.requireNonNull(originalWorld, "originalWorld");
        this.automobile = Objects.requireNonNull(automobile, "automobile");
        this.testContext = null;
        this.visit = this::clipCell;
    }

    /** Headless differential tests; production callers always use the entity constructor. */
    HeadlightVisibilityCache(BlockGetter originalWorld, CollisionContext testContext) {
        this.originalWorld = Objects.requireNonNull(originalWorld, "originalWorld");
        this.automobile = null;
        this.testContext = Objects.requireNonNull(testContext, "testContext");
        this.visit = this::clipCell;
    }

    /** Same native COLLIDER/NONE traversal, endpoints and entity context as world.clip. */
    public BlockHitResult clip(Vec3 from, Vec3 to) {
        ClipContext context = testContext == null
                ? new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, automobile)
                : new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, testContext);
        return BlockGetter.traverseBlocks(from, to, context, visit, HeadlightVisibilityCache::miss);
    }

    /**
     * Patch callers only need to know that an exhausted traversal was clear.
     * Keep non-null visitor results, including custom MISS results, unchanged.
     * Discovery still uses clip(), with its exact native terminal MISS attributes.
     */
    BlockHitResult clipOrNullOnTraversalMiss(Vec3 from, Vec3 to) {
        ClipContext context = testContext == null
                ? new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, automobile)
                : new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, testContext);
        return BlockGetter.traverseBlocks(from, to, context, visit, ignored -> null);
    }

    private BlockHitResult clipCell(ClipContext context, BlockPos position) {
        CellFacts fact = facts(position);
        Vec3 from = context.getFrom();
        Vec3 to = context.getTo();
        VoxelShape collision = fact.collision;
        if (collision == null) {
            // Shape callbacks receive the actual world and the original per-ray entity context.
            collision = context.getBlockShape(fact.state, originalWorld, position);
            if (!fact.dynamic) fact.collision = collision;
        }
        // Keep native interaction overrides, including custom overrides on the original world.
        // Interaction shape is deliberately not memoized or evaluated before a collision hit.
        // Every ray uses Fluid.NONE, whose empty shape cannot contribute a nearer hit.
        // facts still performs both native world reads before the collision callback.
        return originalWorld.clipWithInteractionOverride(from, to, position, collision, fact.state);
    }

    private CellFacts facts(BlockPos position) {
        long key = position.asLong();
        CellFacts existing = cells.get(key);
        if (existing != null) return existing;
        // Do not substitute an unloaded/air shortcut: native clip asks both world getters.
        CellFacts fact = new CellFacts(originalWorld.getBlockState(position), originalWorld.getFluidState(position));
        HeadlightStaticSurfaceScope.observe(originalWorld,position,fact.state);
        // Dynamic shapes bypass the entire cell cache; overflow also only bypasses admission.
        if (!fact.dynamic && cells.size() < MAX_ENTRIES) cells.put(key, fact);
        return fact;
    }

    private static BlockHitResult miss(ClipContext context) {
        Vec3 from = context.getFrom(), to = context.getTo();
        return BlockHitResult.miss(context.getTo(),
                Direction.getApproximateNearest(from.x - to.x, from.y - to.y, from.z - to.z),
                BlockPos.containing(context.getTo()));
    }

    private static final class CellFacts {
        private final BlockState state;
        private final FluidState fluid;
        private final boolean dynamic;
        private VoxelShape collision;

        private CellFacts(BlockState state, FluidState fluid) {
            this.state = state;
            this.fluid = fluid;
            this.dynamic = state.getBlock().hasDynamicShape();
        }
    }
}
