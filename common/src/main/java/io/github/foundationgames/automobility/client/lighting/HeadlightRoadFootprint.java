package io.github.foundationgames.automobility.client.lighting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure, bounded world-column discovery for the projected road footprint of a headlight. */
public final class HeadlightRoadFootprint {
    public static final int MAX_COLUMNS = 1024;
    private static final double MAX_RANGE = 38;
    private static final double MAX_TANGENT = 0.56;
    private static final double NEAR_HALF_WIDTH = 0.8;

    private HeadlightRoadFootprint() {}

    public record Column(int x, int z, double distance, double across) {}

    /**
     * Enumerates world-aligned columns, rather than a sparse grid rotated with the car.
     * On flat ground every column whose center lies in the footprint receives a probe.
     * The fixed bounds cover the built-in LOW/HIGH beams; extremely steep poses use the
     * renderer's direct fan instead. Oversized projected meshes are sampled across their
     * whole length, never cut off by a near-to-far early exit.
     */
    public static List<Column> columns(double originX, double originZ, double forwardX, double forwardZ,
                                       double rightX, double rightZ, double range, double tangent) {
        return columns(originX, originZ, forwardX, forwardZ, rightX, rightZ, range, tangent, MAX_COLUMNS);
    }

    /** Lower-detail probes span the entire optical footprint; connected discovery fills surfaces. */
    public static List<Column> columns(double originX, double originZ, double forwardX, double forwardZ,
                                       double rightX, double rightZ, double range, double tangent, int maxColumns) {
        if (maxColumns < 0 || maxColumns > MAX_COLUMNS) throw new IllegalArgumentException("Invalid footprint quota");
        for (double value : new double[] {originX, originZ, forwardX, forwardZ, rightX, rightZ, range, tangent}) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite road footprint");
        }
        if (range <= 0 || range > MAX_RANGE || tangent <= 0 || tangent > MAX_TANGENT
                || Math.abs(originX) > 30_000_000 || Math.abs(originZ) > 30_000_000
                || forwardX * forwardX + forwardZ * forwardZ > 1.00001
                || rightX * rightX + rightZ * rightZ > 1.00001) {
            throw new IllegalArgumentException("Road footprint exceeds built-in bounds");
        }
        double determinant = forwardX * rightZ - forwardZ * rightX;
        if (Math.abs(determinant) < 0.25) return List.of();
        double distanceX = rightZ / determinant, distanceZ = -rightX / determinant;
        double acrossX = -forwardZ / determinant, acrossZ = forwardX / determinant;
        double distanceMargin = 0.5 * (Math.abs(distanceX) + Math.abs(distanceZ));
        double acrossMargin = 0.5 * (Math.abs(acrossX) + Math.abs(acrossZ)) + distanceMargin * tangent;
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (double distance : new double[] {0, range}) {
            double width = NEAR_HALF_WIDTH + distance * tangent;
            for (double across : new double[] {-width, width}) {
                double x = originX + forwardX * distance + rightX * across;
                double z = originZ + forwardZ * distance + rightZ * across;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }
        }
        List<Column> columns = new ArrayList<>();
        for (int x = (int)Math.floor(minX); x <= (int)Math.floor(maxX); x++) {
            for (int z = (int)Math.floor(minZ); z <= (int)Math.floor(maxZ); z++) {
                double deltaX = x + 0.5 - originX, deltaZ = z + 0.5 - originZ;
                double distance = deltaX * distanceX + deltaZ * distanceZ;
                double across = deltaX * acrossX + deltaZ * acrossZ;
                double clampedDistance = Math.max(0, Math.min(range, distance));
                if (distance >= -distanceMargin && distance <= range + distanceMargin
                        && Math.abs(across) <= NEAR_HALF_WIDTH + clampedDistance * tangent + acrossMargin) {
                    columns.add(new Column(x, z, distance, across));
                }
            }
        }
        columns.sort(Comparator.comparingDouble(Column::distance).thenComparingDouble(Column::across));
        if (maxColumns == 0) return List.of();
        if (columns.size() <= maxColumns) return List.copyOf(columns);
        if (maxColumns == 1) return List.of(columns.get(columns.size() / 2));
        List<Column> bounded = new ArrayList<>(maxColumns);
        for (int i = 0; i < maxColumns; i++) {
            int index = (int)((long)i * (columns.size() - 1) / (maxColumns - 1));
            bounded.add(columns.get(index));
        }
        return List.copyOf(bounded);
    }
}
