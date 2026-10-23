package io.github.foundationgames.automobility.client.lighting;

import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Separate turn-signal faces on verified model surfaces, in model-transformed y-down coordinates. */
public record VehicleTurnSignalLayout(List<VehicleHeadlightLayout.LampFace> frontFaces,
                                      List<VehicleHeadlightLayout.LampFace> rearFaces) {
    private static final double FACE_OFFSET_PX = 0.03;
    private static final VehicleTurnSignalLayout EMPTY = new VehicleTurnSignalLayout(List.of(), List.of());
    private static final Map<Identifier, VehicleTurnSignalLayout> LAYOUTS = new HashMap<>();

    static {
        // Optional exact RIA model ID; no addon classes, assets, or mandatory dependency.
        register(RiaLorryLightGeometry.MODEL_ID, RiaLorryLightGeometry.signals());
        // Standard bumper and rear deck, after ModelDefinition yaw=-90; clear of existing lamp bezels.
        registerBuiltIn("standard", pair(
                flatPair(2.7, -1, -22, 1.4, 1.4, false),
                flatPair(1.4, -1.5, 18, 1.2, 1.5, true)));
        // Motorcar main bone offset(0,-2,5): hood front z=-21, rear platform z=23.
        registerBuiltIn("motorcar", pair(
                flatPair(2.1, -6.5, -21, 1.4, 1.6, false),
                flatPair(4.5, -1, 23, 1.6, 1.4, true)));
        // Tractor hood above the headlamps and the unused top strip of the rear block.
        registerBuiltIn("tractor", pair(
                flatPair(2.5, -11.4, -19, 1.6, 1.4, false),
                flatPair(3.5, -7.2, 12, 1.6, 1.2, true)));
        // Rickshaw front crossbar and narrow rear plate strips; its single headlamp stays intact.
        registerBuiltIn("rickshaw", pair(
                flatPair(3, 0, -12, 1.4, 1.4, false),
                flatPair(6, -5.65, 17, 1.4, 0.5, true)));
        // Shopping cart's solid base, below the existing front/rear position markers.
        registerBuiltIn("shopping_cart", pair(
                flatPair(4, -0.5, -11, 1.5, 0.75, false),
                flatPair(4, -0.5, 11, 1.5, 0.75, true)));
        registerBuiltIn("c_arr", pair(cartPair(false), cartPair(true)));
        // Pineapple ModelDefinition scale=2: planar front z=-10 and cube rear z=9.
        registerBuiltIn("pineapple", pair(
                flatPair(5, -4.5, -10, 1.7, 1.6, false),
                flatPair(5, -4.5, 9, 1.7, 1.6, true)));
    }

    public VehicleTurnSignalLayout {
        frontFaces = List.copyOf(frontFaces);
        rearFaces = List.copyOf(rearFaces);
        validate(frontFaces, false);
        validate(rearFaces, true);
    }

    /** Addons supply their own physical faces. Unknown models have no guessed floating indicators. */
    public static void register(Identifier frameModel, VehicleTurnSignalLayout layout) {
        LAYOUTS.put(Objects.requireNonNull(frameModel), Objects.requireNonNull(layout));
    }

    public static VehicleTurnSignalLayout forFrame(AutomobileFrame frame) {
        return forModel(frame.model().modelId());
    }

    static VehicleTurnSignalLayout forModel(Identifier frameModel) {
        return LAYOUTS.getOrDefault(frameModel, EMPTY);
    }

    static VehicleTurnSignalLayout empty() {
        return EMPTY;
    }

    /** Model +X becomes the driver's left through the existing chassis transform. */
    static boolean isLeft(VehicleHeadlightLayout.LampFace face) {
        return face.center().x > 0;
    }

    private static void validate(List<VehicleHeadlightLayout.LampFace> faces, boolean rear) {
        if (faces.size() > 2) throw new IllegalArgumentException("At most two signal faces at each end");
        for (var face : faces) {
            if (Math.abs(face.center().x) < 0.000001
                    || (rear ? face.normal().z <= 0 : face.normal().z >= 0)) {
                throw new IllegalArgumentException("Signal faces need a side and an outward end normal");
            }
        }
        if (faces.size() == 2 && isLeft(faces.getFirst()) == isLeft(faces.getLast())) {
            throw new IllegalArgumentException("Each end permits one face per side");
        }
    }

    private static void registerBuiltIn(String name, VehicleTurnSignalLayout layout) {
        register(Identifier.fromNamespaceAndPath("automobility", "frame/" + name), layout);
    }

    private static VehicleTurnSignalLayout pair(List<VehicleHeadlightLayout.LampFace> front,
                                                List<VehicleHeadlightLayout.LampFace> rear) {
        return new VehicleTurnSignalLayout(front, rear);
    }

    private static List<VehicleHeadlightLayout.LampFace> flatPair(double spread, double y, double surfaceZ,
                                                                 double width, double height, boolean rear) {
        Vec3 right = new Vec3(rear ? -1 : 1, 0, 0);
        Vec3 down = new Vec3(0, 1, 0);
        double z = (surfaceZ + (rear ? FACE_OFFSET_PX : -FACE_OFFSET_PX)) / 16;
        return List.of(
                new VehicleHeadlightLayout.LampFace(new Vec3(-spread / 16, y / 16, z),
                        right, down, width / 16, height / 16),
                new VehicleHeadlightLayout.LampFace(new Vec3(spread / 16, y / 16, z),
                        right, down, width / 16, height / 16));
    }

    private static List<VehicleHeadlightLayout.LampFace> cartPair(boolean rear) {
        // Same actual sloped axle planes as the existing c_arr lamps; signals use the free inner strip.
        double theta = 0.3926999997419088;
        double sin = Math.sin(theta), cos = Math.cos(theta);
        Vec3 down = new Vec3(0, cos, rear ? -sin : sin);
        Vec3 normal = new Vec3(0, sin, rear ? cos : -cos);
        double y = (-5 + 12 * sin + cos) / 16;
        double z = (rear ? 12 + 12 * cos - sin : -12 - 12 * cos + sin) / 16;
        Vec3 offset = normal.scale(FACE_OFFSET_PX / 16);
        Vec3 right = new Vec3(rear ? -1 : 1, 0, 0);
        return List.of(
                new VehicleHeadlightLayout.LampFace(new Vec3(-4.0 / 16, y, z).add(offset),
                        right, down, 1.4 / 16, 1.6 / 16),
                new VehicleHeadlightLayout.LampFace(new Vec3(4.0 / 16, y, z).add(offset),
                        right, down, 1.4 / 16, 1.6 / 16));
    }
}
