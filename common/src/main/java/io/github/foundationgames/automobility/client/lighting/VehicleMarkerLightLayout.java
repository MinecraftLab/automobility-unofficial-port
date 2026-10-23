package io.github.foundationgames.automobility.client.lighting;

import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Physical marker faces, in the same model-transformed, y-down coordinates as headlamps. */
public record VehicleMarkerLightLayout(List<VehicleHeadlightLayout.LampFace> frontFaces,
                                      List<VehicleHeadlightLayout.LampFace> rearFaces) {
    private static final double FACE_OFFSET_PX = 0.03;
    private static final VehicleMarkerLightLayout EMPTY = new VehicleMarkerLightLayout(List.of(), List.of());
    private static final Map<Identifier, VehicleMarkerLightLayout> LAYOUTS = new HashMap<>();

    static {
        // ModelDefinition yaw=-90: main rear deck x=8..18,y=-3..0,z=-5..5.
        registerBuiltIn("standard", rearPair(3.5, -1.5, 18, 2.6, 1.9));
        // main bone offset (0,-2,5), rear 3x3x2 boxes at x=-7.75/4.75,y=-6,z=13.
        registerBuiltIn("motorcar", rearPair(6.25, -6.5, 20, 3, 3));
        // Tractor rear body x=-5..5,y=-8..-2,z=8..12.
        registerBuiltIn("tractor", rearPair(3.5, -5, 12, 2.6, 2.6));
        // Rickshaw's two side plates end at z=17, x=-7..-5 and x=5..7.
        registerBuiltIn("rickshaw", rearPair(6, -4, 17, 1.6, 2.6));
        // No existing physical headlamps: both ends of the solid base x=-6..6,y=-4..0,z=-11..11.
        registerBuiltIn("shopping_cart", new VehicleMarkerLightLayout(
                flatPair(4, -2, -11, 2, 1.5, false), flatPair(4, -2, 11, 2, 1.5, true)));
        registerBuiltIn("c_arr", cartRear());
        register(RiaLorryLightGeometry.MODEL_ID, RiaLorryLightGeometry.markers());
        // ModelDefinition scale=2. Rear cube ends at z=9; existing front planar cuboid is z=-10.
        registerBuiltIn("pineapple", new VehicleMarkerLightLayout(
                flatPair(5, -8, -10, 2.6, 2.6, false), flatPair(5, -8, 9, 2.6, 2.6, true)));
    }

    public VehicleMarkerLightLayout {
        frontFaces = List.copyOf(frontFaces);
        rearFaces = List.copyOf(rearFaces);
        if (frontFaces.size() > 2 || rearFaces.size() > 2) {
            throw new IllegalArgumentException("A marker layout permits at most two faces at each end");
        }
        if (frontFaces.stream().anyMatch(face -> face.normal().z >= 0)
                || rearFaces.stream().anyMatch(face -> face.normal().z <= 0)) {
            throw new IllegalArgumentException("Marker faces must point out of the corresponding end");
        }
    }

    /** Addons must supply verified physical faces; unknown models never receive floating panels. */
    public static void register(Identifier frameModel, VehicleMarkerLightLayout layout) {
        LAYOUTS.put(Objects.requireNonNull(frameModel), Objects.requireNonNull(layout));
    }

    public static VehicleMarkerLightLayout forFrame(AutomobileFrame frame) {
        return forModel(frame.model().modelId());
    }

    static VehicleMarkerLightLayout forModel(Identifier frameModel) {
        return LAYOUTS.getOrDefault(frameModel, EMPTY);
    }

    private static void registerBuiltIn(String name, VehicleMarkerLightLayout layout) {
        register(Identifier.fromNamespaceAndPath("automobility", "frame/" + name), layout);
    }

    private static VehicleMarkerLightLayout rearPair(double spread, double y, double back,
                                                    double width, double height) {
        return new VehicleMarkerLightLayout(List.of(), flatPair(spread, y, back, width, height, true));
    }

    private static List<VehicleHeadlightLayout.LampFace> flatPair(double spread, double y, double z,
                                                                double width, double height, boolean rear) {
        Vec3 right = new Vec3(rear ? -1 : 1, 0, 0);
        Vec3 down = new Vec3(0, 1, 0);
        double faceZ = (z + (rear ? FACE_OFFSET_PX : -FACE_OFFSET_PX)) / 16;
        return List.of(
                new VehicleHeadlightLayout.LampFace(new Vec3(-spread / 16, y / 16, faceZ),
                        right, down, width / 16, height / 16),
                new VehicleHeadlightLayout.LampFace(new Vec3(spread / 16, y / 16, faceZ),
                        right, down, width / 16, height / 16));
    }

    private static VehicleMarkerLightLayout cartRear() {
        // c_arr rear_axle bar: offset(10,0,-8), size(2,2,16), bone origin(12,-5,0),
        // rotation Z=+theta, then frame yaw=-90. Match the mirrored physical front bar.
        double theta = 0.3926999997419088;
        double sin = Math.sin(theta), cos = Math.cos(theta);
        Vec3 down = new Vec3(0, cos, -sin);
        Vec3 normal = new Vec3(0, sin, cos);
        double y = (-5 + 12 * sin + cos) / 16;
        double z = (12 + 12 * cos - sin) / 16;
        Vec3 offset = normal.scale(FACE_OFFSET_PX / 16);
        Vec3 right = new Vec3(-1, 0, 0);
        return new VehicleMarkerLightLayout(List.of(), List.of(
                new VehicleHeadlightLayout.LampFace(new Vec3(-6.5 / 16, y, z).add(offset),
                        right, down, 2.6 / 16, 1.9 / 16),
                new VehicleHeadlightLayout.LampFace(new Vec3(6.5 / 16, y, z).add(offset),
                        right, down, 2.6 / 16, 1.9 / 16)));
    }
}
