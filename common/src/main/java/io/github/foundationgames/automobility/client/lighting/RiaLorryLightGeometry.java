package io.github.foundationgames.automobility.client.lighting;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Verified optional model coordinates only: no RIA classes, assets or mandatory dependency.
 * Model pixels are y-down; definition has identity transform. Panels inset 0.2px
 * on fixed OBJ faces, offset 0.03px outwards to avoid z-fighting.
 */
final class RiaLorryLightGeometry {
    static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath("riautomobility", "frame/lorry");
    private RiaLorryLightGeometry() {}

    static VehicleHeadlightLayout headlights() {
        // White original light_107/108 faces, separate from the outer amber lenses.
        var faces = pair(12.77273, -13.22727, -9.11364, -57.93909, 5.6, 7.6, false);
        return new VehicleHeadlightLayout(faces.stream().map(VehicleHeadlightLayout.LampFace::center).toList(), faces);
    }
    static VehicleMarkerLightLayout markers() {
        // New red inserts on the fixed outer body_20/23 supports, clear of cargo doors/plate.
        return new VehicleMarkerLightLayout(List.of(), pair(20.77273, -21.22727, -7.11364, 70.12091, 1.6, 3.6, true));
    }
    static VehicleReverseLightLayout reverse() {
        // Separate white inserts on inner body_21/22 supports.
        return new VehicleReverseLightLayout(pair(8.77273, -9.22727, -7.11364, 70.12091, 1.6, 3.6, true));
    }
    static VehicleTurnSignalLayout signals() {
        // Original outer light_105/106 amber lenses, separated from the white lenses.
        var front = pair(17.77273, -18.22727, -10.11364, -57.93909, 3.6, 9.6, false);
        // Fixed upper body_18 crossbar: above red inserts, clear of doors and plate.
        var rear = pair(20.77273, -21.22727, -10.11364, 70.12091, 1.6, 1.6, true);
        return new VehicleTurnSignalLayout(front, rear);
    }
    private static List<VehicleHeadlightLayout.LampFace> pair(double x1, double x2, double y, double z,
                                                             double width, double height, boolean rear) {
        Vec3 right = new Vec3(rear ? -1 : 1, 0, 0), down = new Vec3(0, 1, 0);
        return List.of(new VehicleHeadlightLayout.LampFace(new Vec3(x1 / 16, y / 16, z / 16),
                        right, down, width / 16, height / 16),
                new VehicleHeadlightLayout.LampFace(new Vec3(x2 / 16, y / 16, z / 16),
                        right, down, width / 16, height / 16));
    }
}
