package io.github.foundationgames.automobility.client.lighting;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** World-independent, bounded discovery of connected exposed block surfaces inside light cones. */
public final class HeadlightSurfaceDiscovery {
    public static final int MAX_BLOCKS = 1536;
    public static final int MAX_CANDIDATES = 8192;
    private static final int[][] NEIGHBORS = {{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}};

    private HeadlightSurfaceDiscovery() {}

    public record Cell(int x, int y, int z) {}

    /**
     * Actual ray hits seed the search. Only existing exposed blocks connect it, so air gaps
     * are never crossed. This finds wall rows/columns missed by the sparse ray fan without
     * manufacturing wall planes. Discovery is conservative; the renderer still clips every
     * accepted patch from each lamp against the real world before drawing it.
     */
    public static List<Cell> expand(List<Cell> seeds, List<HeadlightBeam> beams, Predicate<Cell> exposedBlock) {
        return expand(seeds, beams, exposedBlock, MAX_BLOCKS, MAX_CANDIDATES);
    }

    /** Caller-owned fleet reservations; legacy callers retain the same discovery semantics. */
    public static List<Cell> expand(List<Cell> seeds, List<HeadlightBeam> beams, Predicate<Cell> exposedBlock,
                                    int maxBlocks, int maxCandidates) {
        if (maxBlocks < 0 || maxBlocks > MAX_BLOCKS || maxCandidates < 0 || maxCandidates > MAX_CANDIDATES)
            throw new IllegalArgumentException("Invalid surface discovery reservation");
        List<BeamBounds> bounds = new ArrayList<>(beams.size());
        for (HeadlightBeam beam : beams) {
            bounds.add(new BeamBounds(beam, radius(beam.forward()), radius(beam.right()), radius(beam.up())));
        }
        Set<Cell> visited = new HashSet<>();
        var pending = new ArrayDeque<Cell>();
        // visited makes the queue unique, so a second hash table is unnecessary.
        var surfaces = new ArrayList<Cell>();
        for (Cell cell : seeds) {
            if (visited.size() >= maxCandidates) break;
            if (!visited.contains(cell) && intersectsAny(cell, bounds) && visited.add(cell)) pending.addLast(cell);
        }
        while (!pending.isEmpty() && surfaces.size() < maxBlocks) {
            Cell cell = pending.removeFirst();
            if (!exposedBlock.test(cell)) continue;
            surfaces.add(cell);
            for (int[] offset : NEIGHBORS) {
                if (visited.size() >= maxCandidates) break;
                Cell neighbor = new Cell(cell.x + offset[0], cell.y + offset[1], cell.z + offset[2]);
                if (!visited.contains(neighbor) && intersectsAny(neighbor, bounds) && visited.add(neighbor)) pending.addLast(neighbor);
            }
        }
        return List.copyOf(surfaces);
    }

    private static boolean intersectsAny(Cell cell, List<BeamBounds> bounds) {
        for (BeamBounds bound : bounds) {
            if (intersects(cell, bound.beam, bound.axialRadius, bound.horizontalRadius, bound.verticalRadius)) return true;
        }
        return false;
    }

    /** Conservative unit-cell/cone overlap; avoids rejecting a wall block whose corner is lit. */
    public static boolean intersects(Cell cell, HeadlightBeam beam) {
        return intersects(cell, beam, radius(beam.forward()), radius(beam.right()), radius(beam.up()));
    }

    private static boolean intersects(Cell cell, HeadlightBeam beam, double axialRadius,
                                      double horizontalRadius, double verticalRadius) {
        double deltaX = (cell.x + 0.5) - beam.origin().x;
        double deltaY = (cell.y + 0.5) - beam.origin().y;
        double deltaZ = (cell.z + 0.5) - beam.origin().z;
        double axial = deltaX * beam.forward().x + deltaY * beam.forward().y + deltaZ * beam.forward().z;
        double farthest = Math.min(beam.range(), axial + axialRadius);
        if (farthest <= 0 || axial - axialRadius >= beam.range()) return false;
        double horizontal = Math.max(0, Math.abs(deltaX * beam.right().x + deltaY * beam.right().y + deltaZ * beam.right().z) - horizontalRadius)
                / (0.15 + farthest * beam.horizontalTangent());
        double vertical = Math.max(0, Math.abs(deltaX * beam.up().x + deltaY * beam.up().y + deltaZ * beam.up().z) - verticalRadius)
                / (0.10 + farthest * beam.verticalTangent());
        return horizontal * horizontal + vertical * vertical <= 1;
    }

    private static double radius(Vec3 axis) {
        return 0.5 * (Math.abs(axis.x) + Math.abs(axis.y) + Math.abs(axis.z));
    }

    private record BeamBounds(HeadlightBeam beam, double axialRadius, double horizontalRadius, double verticalRadius) {}
}
