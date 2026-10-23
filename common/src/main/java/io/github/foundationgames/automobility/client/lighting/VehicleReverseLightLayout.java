package io.github.foundationgames.automobility.client.lighting;

import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Separate white reversing lamps on verified rear model surfaces, in model-transformed y-down coordinates. */
public record VehicleReverseLightLayout(List<VehicleHeadlightLayout.LampFace> rearFaces) {
    private static final double FACE_OFFSET_PX = .03;
    private static final VehicleReverseLightLayout EMPTY = new VehicleReverseLightLayout(List.of());
    private static final Map<Identifier, VehicleReverseLightLayout> LAYOUTS = new HashMap<>();

    static {
        // Standard rear deck x=-5..5,y=-3..0,z=18. Central pair fits between the existing amber faces.
        registerBuiltIn("standard", flatPair(.4, -1.5, 18, .65, 1.4));
        // Motorcar rear platform: bone offset(0,-2,5), local box(-6,0,11), rear at z=23.
        registerBuiltIn("motorcar", flatPair(1.6, -1, 23, 1.8, 1.2));
        // Tractor rear body: x=-5..5,y=-8..-2,z=8..12; white pair is inside the red lamps.
        registerBuiltIn("tractor", flatPair(1, -4.9, 12, 1.4, 1.6));
        // Rickshaw side plates x=-7..-5 and5..7,y=-6..-2,z=17; free bottom strip below the red faces.
        registerBuiltIn("rickshaw", flatPair(6, -2.35, 17, 1.2, .45));
        // Shopping cart solid base x=-6..6,y=-4..0,z=-11..11; inner pair clears red and amber.
        registerBuiltIn("shopping_cart", flatPair(1.5, -2, 11, 1.8, 1.5));
        registerBuiltIn("c_arr", cartPair());
        register(RiaLorryLightGeometry.MODEL_ID, RiaLorryLightGeometry.reverse());
        // Pineapple model scale2: rear cube x=-9..9,y=-16..0,z=9; inner pair clears both coloured pairs.
        registerBuiltIn("pineapple", flatPair(1.8, -8, 9, 1.8, 2));
    }

    public VehicleReverseLightLayout {
        rearFaces = List.copyOf(rearFaces);
        if (rearFaces.size() > 2 || rearFaces.stream().anyMatch(face -> face.normal().z <= 0)) {
            throw new IllegalArgumentException("At most two outward rear reversing faces");
        }
    }

    /** Unknown models have no inferred white panels; addons register their verified physical geometry. */
    public static void register(Identifier frameModel, VehicleReverseLightLayout layout) {
        LAYOUTS.put(Objects.requireNonNull(frameModel), Objects.requireNonNull(layout));
    }

    public static VehicleReverseLightLayout forFrame(AutomobileFrame frame) {
        return forModel(frame.model().modelId());
    }

    static VehicleReverseLightLayout forModel(Identifier model) { return LAYOUTS.getOrDefault(model, EMPTY); }

    private static void registerBuiltIn(String name, List<VehicleHeadlightLayout.LampFace> faces) {
        register(Identifier.fromNamespaceAndPath("automobility", "frame/" + name), new VehicleReverseLightLayout(faces));
    }

    private static List<VehicleHeadlightLayout.LampFace> flatPair(double spread, double y, double z,
                                                                double width, double height) {
        Vec3 right = new Vec3(-1, 0, 0), down = new Vec3(0, 1, 0);
        return List.of(new VehicleHeadlightLayout.LampFace(
                        new Vec3(-spread / 16, y / 16, (z + FACE_OFFSET_PX) / 16), right, down, width / 16, height / 16),
                new VehicleHeadlightLayout.LampFace(
                        new Vec3(spread / 16, y / 16, (z + FACE_OFFSET_PX) / 16), right, down, width / 16, height / 16));
    }

    private static List<VehicleHeadlightLayout.LampFace> cartPair() {
        // The inner free part of c_arr's actual sloped rear axle bar; model yaw=-90 and bone Z rotation.
        double theta = .3926999997419088, sin = Math.sin(theta), cos = Math.cos(theta);
        Vec3 down = new Vec3(0, cos, -sin), normal = new Vec3(0, sin, cos);
        double y = (-5 + 12 * sin + cos) / 16, z = (12 + 12 * cos - sin) / 16;
        Vec3 offset = normal.scale(FACE_OFFSET_PX / 16), right = new Vec3(-1, 0, 0);
        return List.of(new VehicleHeadlightLayout.LampFace(new Vec3(-1.5 / 16, y, z).add(offset),
                        right, down, 1.8 / 16, 1.5 / 16),
                new VehicleHeadlightLayout.LampFace(new Vec3(1.5 / 16, y, z).add(offset),
                        right, down, 1.8 / 16, 1.5 / 16));
    }
}
