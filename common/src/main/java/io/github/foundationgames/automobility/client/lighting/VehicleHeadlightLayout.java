package io.github.foundationgames.automobility.client.lighting;

import io.github.foundationgames.automobility.automobile.AutomobileFrame;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Headlamp positions in block units after the frame model's own transforms, before
 * the chassis transforms (model y points down). A physical face shares its center
 * with its beam origin, so visible sources and moving surface light stay attached.
 */
public record VehicleHeadlightLayout(List<Vec3> lamps, List<LampFace> faces) {
    private static final double FACE_OFFSET_PX = 0.03;
    private static final Map<Identifier, VehicleHeadlightLayout> LAYOUTS = new HashMap<>();

    static {
        // standard/main: x=-22 bumper face, y=-2..0, z=-8..8; ModelDefinition yaw=-90.
        registerBuiltIn("standard", physicalPair(5.5, 1, 22, 3.5, 1.9));
        // motorcar/main: root offset (0,-2,5), two 3x3 lamp boxes at x=3/-6,y=-6,z=-27.
        registerBuiltIn("motorcar", physicalPair(4.5, 6.5, 22, 3, 3));
        // tractor/main: the hood's front face spans x=-4..4,y=-13..-4,z=-19.
        registerBuiltIn("tractor", physicalPair(2.5, 8.5, 19, 2.6, 2.6));
        // Rickshaw has a single centered 3x3 lamp box, not two suspended car lamps.
        registerBuiltIn("rickshaw", withFaces(List.of(flatFace(0, 6.25, 14.5, 3, 3))));
        registerBuiltIn("c_arr", cartFaces());
        register(RiaLorryLightGeometry.MODEL_ID, RiaLorryLightGeometry.headlights());
    }

    /** Existing integrations with only positions retain their beam-only layout. */
    public VehicleHeadlightLayout(List<Vec3> lamps) {
        this(lamps, List.of());
    }

    public VehicleHeadlightLayout {
        lamps = List.copyOf(lamps);
        faces = List.copyOf(faces);
        if (lamps.isEmpty() || lamps.size() > 2 || lamps.stream().anyMatch(p -> !p.isFinite())) {
            throw new IllegalArgumentException("A headlight layout needs one or two finite positions");
        }
        if (!faces.isEmpty() && faces.size() != lamps.size()) {
            throw new IllegalArgumentException("Physical lamp faces must share the beam origins");
        }
        for (int i = 0; i < faces.size(); i++) {
            if (!faces.get(i).center().equals(lamps.get(i))) {
                throw new IllegalArgumentException("Physical lamp faces must share the beam origins");
            }
        }
    }

    /** Resource/model integrations may register beam positions and optional physical faces. */
    public static void register(Identifier frameModel, VehicleHeadlightLayout layout) {
        LAYOUTS.put(java.util.Objects.requireNonNull(frameModel), java.util.Objects.requireNonNull(layout));
    }

    public static VehicleHeadlightLayout forFrame(AutomobileFrame frame) {
        return forModel(frame.model().modelId(), frame.width(),
                frame.model().lengthPx(), frame.model().frontAttachmentPos());
    }

    static VehicleHeadlightLayout forModel(Identifier modelId, double width, double lengthPx, double frontAttachmentPx) {
        VehicleHeadlightLayout registered = LAYOUTS.get(modelId);
        // Unknown/addon models retain directional light, but need explicit face geometry.
        // Inferred bounding dimensions cannot prove a visible panel is attached to a model.
        return registered != null ? registered : pair(
                Math.max(3, Math.min(8, width * 6)), 5,
                Math.max(lengthPx / 2, frontAttachmentPx) + 0.2);
    }

    private static void registerBuiltIn(String name, VehicleHeadlightLayout layout) {
        register(Identifier.fromNamespaceAndPath("automobility", "frame/" + name), layout);
    }

    private static VehicleHeadlightLayout physicalPair(double spreadPx, double heightPx, double frontPx,
                                                       double widthPx, double heightSizePx) {
        return withFaces(List.of(flatFace(-spreadPx, heightPx, frontPx, widthPx, heightSizePx),
                flatFace(spreadPx, heightPx, frontPx, widthPx, heightSizePx)));
    }

    private static LampFace flatFace(double xPx, double heightPx, double frontPx, double widthPx, double heightSizePx) {
        return new LampFace(new Vec3(xPx / 16, -heightPx / 16, -(frontPx + FACE_OFFSET_PX) / 16),
                new Vec3(1, 0, 0), new Vec3(0, 1, 0), widthPx / 16, heightSizePx / 16);
    }

    private static VehicleHeadlightLayout cartFaces() {
        // c_arr's front_axle bar: offset(-12,0,-8), size(2,2,16), bone origin(-12,-5,0),
        // rotation Z=-0.3926999997, followed by ModelDefinition yaw=-90.
        double angle = 0.3926999997419088;
        double sin = Math.sin(angle), cos = Math.cos(angle);
        Vec3 down = new Vec3(0, cos, sin);
        Vec3 normal = new Vec3(0, sin, -cos);
        double centerY = (-5 + 12 * sin + cos) / 16;
        double centerZ = (-12 - 12 * cos + sin) / 16;
        Vec3 offset = normal.scale(FACE_OFFSET_PX / 16);
        return withFaces(List.of(
                new LampFace(new Vec3(-6.5 / 16, centerY, centerZ).add(offset), new Vec3(1, 0, 0), down, 2.6 / 16, 1.9 / 16),
                new LampFace(new Vec3(6.5 / 16, centerY, centerZ).add(offset), new Vec3(1, 0, 0), down, 2.6 / 16, 1.9 / 16)));
    }

    private static VehicleHeadlightLayout withFaces(List<LampFace> faces) {
        return new VehicleHeadlightLayout(faces.stream().map(LampFace::center).toList(), faces);
    }

    private static VehicleHeadlightLayout pair(double spreadPx, double heightPx, double frontPx) {
        return new VehicleHeadlightLayout(List.of(
                new Vec3(-spreadPx / 16, -heightPx / 16, -frontPx / 16),
                new Vec3(spreadPx / 16, -heightPx / 16, -frontPx / 16)));
    }

    /** Unit right/down axes describe the actual front face, including a sloped model bone. */
    public record LampFace(Vec3 center, Vec3 right, Vec3 down, double width, double height) {
        public LampFace {
            if (center == null || right == null || down == null || !center.isFinite()
                    || !right.isFinite() || !down.isFinite() || !Double.isFinite(width) || !Double.isFinite(height)
                    || width <= 0 || height <= 0 || Math.abs(right.lengthSqr() - 1) > 0.000001
                    || Math.abs(down.lengthSqr() - 1) > 0.000001 || Math.abs(right.dot(down)) > 0.000001) {
                throw new IllegalArgumentException("A lamp face needs finite dimensions and perpendicular unit axes");
            }
        }

        public Vec3 normal() {
            return down.cross(right).normalize();
        }
    }
}
