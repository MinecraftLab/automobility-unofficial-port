package io.github.foundationgames.automobility.client.lighting;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.entity.render.AutomobileEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.*;
import static io.github.foundationgames.automobility.client.lighting.VehicleConeGeometry.*;

/** Bounded presentation cones. Every mesh is immutable until its deferred callback releases it. */
public final class VehicleConeRenderer {
    private static final Identifier WHITE = Identifier.withDefaultNamespace("textures/block/white_concrete.png");
    private static final VehicleConeMesh.Lease[] LEASES = new VehicleConeMesh.Lease[7];
    static { for (int i = 0; i < LEASES.length; i++) LEASES[i] = new VehicleConeMesh.Lease(); }
    private static final VehicleConeCollision VISITOR = new VehicleConeCollision();
    private static final Set<AutomobileEntity> SEEN = Collections.newSetFromMap(new IdentityHashMap<>());
    private static long frame, generation, visits, boxes, shapeCalls;
    private static int queued, emitted, beams, vertices, unknown;
    private static String failure;

    public record Snapshot(long frame, long generation, int meshArrays, int heldMeshes, int queued,
                           int emitted, int beams, long blockVisits, long shapeCalls, long collisionBoxes,
                           int vertices, int unknownCells, String failure) {}
    public static Snapshot snapshot() {
        int held = 0; for (var lease : LEASES) if (lease.held()) held++;
        return new Snapshot(frame, generation, LEASES.length, held, queued, emitted, beams, visits,
                shapeCalls, boxes, vertices, unknown, failure);
    }
    public static void beginFrame() {
        frame++;
        for (var lease : LEASES) if (lease.held()) fail(new IllegalStateException("Cone callback did not finish its frame"));
        if (VehicleConeMesh.Lease.creations() != LEASES.length) fail(new IllegalStateException("Unexpected cone mesh allocation"));
        SEEN.clear(); visits = boxes = shapeCalls = 0; queued = emitted = beams = vertices = unknown = 0;
    }
    /** Generation invalidation never releases an in-flight buffer or reuses world visibility. */
    public static void invalidate() {
        generation++;
        boolean held = false; for (var lease : LEASES) held |= lease.held();
        if (!held) failure = null;
    }
    private static void fail(Throwable problem) {
        if (failure == null) {
            failure = problem.getClass().getSimpleName() + ": " + problem.getMessage();
            System.err.println("[Automobility cones] stopped after guard failure: " + failure);
        }
    }
    private static V v(Vec3 point) { return new V(point.x, point.y, point.z); }

    public static void submit(AutomobileEntityRenderer.State state, PoseStack pose,
                              SubmitNodeCollector collector, List<HeadlightBeam> sources) {
        if (failure != null || ComplementaryTemporalProjection.isShadowPass() || sources.isEmpty()) return;
        var client = Minecraft.getInstance(); var world = client.level;
        if (world == null || state.automobile == null || state.automobile.level() != world) return;
        if (SEEN.contains(state.automobile)) return;
        if (sources.size() > 2 || SEEN.size() >= LEASES.length) {
            fail(new IllegalStateException("Bounded cone car/beam count")); return;
        }
        VehicleConeMesh.Lease available = null;
        for (var item : LEASES) if (!item.held()) { available = item; break; }
        if (available == null) { fail(new IllegalStateException("No free cone mesh")); return; }
        final var lease = available;
        final long token = lease.acquire(), submittedGeneration = generation;
        SEEN.add(state.automobile);
        boolean queueAttempted = false;
        try {
            final var modelGeneration = client.getModelManager().getBlockStateModelSet();
            var origin = new Vec3(state.x, state.y, state.z);
            if (state.passengerOffset != null) origin = origin.add(state.passengerOffset);
            V relative = v(origin);
            var pos = new BlockPos.MutableBlockPos();
            // Exactly the qualified cone269 sequence: a fresh context for this source's scan.
            var context = CollisionContext.of(state.automobile);
            int at = 0, localUnknown = 0;
            long localVisits = 0, localBoxes = 0, localCalls = 0;
            for (var source : sources) {
                var volume = new Volume(v(source.origin()), v(source.forward()), v(source.right()), v(source.up()),
                        source.range(), source.horizontalTangent(), source.verticalTangent());
                Box bound = volume.bounds;
                int x0 = (int)Math.floor(bound.x0()) - 1, x1 = (int)Math.floor(bound.x1()) + 1;
                int y0 = (int)Math.floor(bound.y0()) - 1, y1 = (int)Math.floor(bound.y1()) + 1;
                int z0 = (int)Math.floor(bound.z0()) - 1, z1 = (int)Math.floor(bound.z1()) + 1;
                if ((long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1) > 8192)
                    throw new IllegalStateException("Bounded cone cells");
                for (int x=x0; x<=x1; x++) for (int y=y0; y<=y1; y++) for (int z=z0; z<=z1; z++) {
                    pos.set(x,y,z); localVisits++;
                    if (!world.isLoaded(pos)) {
                        volume.unknown(new Box(x,y,z,x+1,y+1,z+1)); localUnknown++; continue;
                    }
                    var stateAt = world.getBlockState(pos);
                    var collision = stateAt.getCollisionShape(world,pos,context); localCalls++;
                    if (!collision.isEmpty()) {
                        VISITOR.begin(volume,x,y,z);
                        try { collision.forAllBoxes(VISITOR); } finally { VISITOR.end(); }
                        localBoxes += VISITOR.boxes; localUnknown += VISITOR.unknown;
                    }
                }
                at = VehicleConeMesh.append(volume,relative.x(),relative.y(),relative.z(),lease.mesh,at);
            }
            final int used = at;
            visits += localVisits; boxes += localBoxes; shapeCalls += localCalls; unknown += localUnknown;
            beams += sources.size();
            final var renderType = RenderTypes.entityTranslucentEmissive(WHITE,false);
            SubmitNodeCollector.CustomGeometryRenderer callback = (transform,buffer) -> {
                try {
                    lease.enterEmission(token);
                    try {
                        if (failure != null || generation != submittedGeneration || client.level != world
                                || client.getModelManager().getBlockStateModelSet() != modelGeneration) return;
                        if (ComplementaryTemporalProjection.isShadowPass())
                            throw new IllegalStateException("Cone queued into shadow pass");
                        var matrix = transform.pose(); var mesh = lease.mesh;
                        for (int i=0; i<used; i+=7)
                            buffer.addVertex(matrix,mesh[i],mesh[i+1],mesh[i+2])
                                    .setColor(255,247,224,Math.max(0,Math.min(255,Math.round(mesh[i+3]*255))))
                                    .setUv(.5f,.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0)
                                    .setNormal(transform,mesh[i+4],mesh[i+5],mesh[i+6]);
                        emitted++; vertices += used/7;
                    } finally { lease.release(token); }
                } catch (Throwable problem) { fail(problem); }
            };
            queued++; queueAttempted = true;
            collector.submitCustomGeometry(pose, renderType, callback);
        } catch (Throwable problem) {
            // A queue that may have accepted the callback keeps its lease until that callback finishes.
            if (!queueAttempted && lease.held()) lease.release(token);
            fail(problem);
        }
    }
    private VehicleConeRenderer() {}
}
