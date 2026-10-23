package io.github.foundationgames.automobility.client.lighting;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Shared immutable lens template. World positions and dynamic light state are never cached. */
final class CircularLampGeometry {
    static final int SEGMENTS = 20;
    static final double DOME_RATIO = .045;
    static final double SURFACE_OFFSET = .008 / 16;
    private static final Template TEMPLATE = template();
    private static final boolean SCALAR_POINTS = Boolean.getBoolean("automobility.experimental.scalarLampPoints");

    private CircularLampGeometry() {}

    static List<VehicleHeadlightLampRenderer.PanelQuad> dome(VehicleHeadlightLampRenderer.PanelQuad panel) {
        List<VehicleHeadlightLampRenderer.PanelQuad> result = new ArrayList<>(2 * SEGMENTS);
        domeInto(panel, result);
        return List.copyOf(result);
    }

    /** Append unchanged geometry into a caller-owned list; no mutable scratch escapes. */
    static void domeInto(VehicleHeadlightLampRenderer.PanelQuad panel,
                         List<VehicleHeadlightLampRenderer.PanelQuad> result) {
        domeInto(panel, result, SCALAR_POINTS);
    }

    /** Explicit path selection also lets pure tests compare both production algorithms. */
    static void domeInto(VehicleHeadlightLampRenderer.PanelQuad panel,
                         List<VehicleHeadlightLampRenderer.PanelQuad> result, boolean scalarPoints) {
        var corners = panel.corners();
        Vec3 center = corners.get(0).add(corners.get(1)).add(corners.get(2)).add(corners.get(3)).scale(.25);
        Vec3 right = corners.get(3).subtract(corners.get(0)).scale(.5);
        Vec3 down = corners.get(1).subtract(corners.get(0)).scale(.5);
        double radius = Math.min(right.length(), down.length());
        Vec3[] points = new Vec3[TEMPLATE.points().size()];
        for (int i = 0; i < points.length; i++) {
            UnitPoint point = TEMPLATE.points().get(i);
            points[i] = scalarPoints
                    ? scalarPoint(center, right, down, panel.normal(), point.x(), point.y(),
                            radius * DOME_RATIO * point.bulge() + SURFACE_OFFSET)
                    : center.add(right.scale(point.x())).add(down.scale(point.y()))
                            .add(panel.normal().scale(radius * DOME_RATIO * point.bulge() + SURFACE_OFFSET));
        }
        for (Indices quad : TEMPLATE.quads()) {
            result.add(new VehicleHeadlightLampRenderer.PanelQuad(
                    List.of(points[quad.a()], points[quad.b()], points[quad.c()], points[quad.d()]),
                    panel.normal(), panel.color(), panel.light(), panel.end(), true));
        }
    }

    /** Exactly the original scale/add sequence per component; no FMA or reassociation. */
    static Vec3 scalarPoint(Vec3 center, Vec3 right, Vec3 down, Vec3 normal, double x, double y, double offset) {
        return new Vec3(((center.x + right.x * x) + down.x * y) + normal.x * offset,
                ((center.y + right.y * x) + down.y * y) + normal.y * offset,
                ((center.z + right.z * x) + down.z * y) + normal.z * offset);
    }

    private static Template template() {
        List<UnitPoint> points = new ArrayList<>(1 + 2 * (SEGMENTS + 1));
        points.add(new UnitPoint(0, 0, 1));
        double[] cos = new double[SEGMENTS + 1], sin = new double[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = i * 2 * Math.PI / SEGMENTS;
            cos[i] = Math.cos(angle);
            sin[i] = Math.sin(angle);
        }
        for (double rho : new double[]{.55, 1}) {
            for (int i = 0; i <= SEGMENTS; i++) {
                points.add(new UnitPoint(rho * cos[i], rho * sin[i], 1 - rho * rho));
            }
        }
        List<Indices> quads = new ArrayList<>(2 * SEGMENTS);
        for (int band = 0; band < 2; band++) {
            for (int i = 0; i < SEGMENTS; i++) {
                quads.add(new Indices(index(band, i), index(band, i + 1),
                        index(band + 1, i + 1), index(band + 1, i)));
            }
        }
        return new Template(List.copyOf(points), List.copyOf(quads));
    }

    private static int index(int ring, int segment) {
        return ring == 0 ? 0 : 1 + (ring - 1) * (SEGMENTS + 1) + segment;
    }

    private record UnitPoint(double x, double y, double bulge) {}
    private record Indices(int a, int b, int c, int d) {}
    private record Template(List<UnitPoint> points, List<Indices> quads) {}
}
