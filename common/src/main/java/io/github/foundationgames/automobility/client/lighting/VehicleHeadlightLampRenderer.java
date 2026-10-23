package io.github.foundationgames.automobility.client.lighting;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.entity.render.AutomobileEntityRenderer;
import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;
import io.github.foundationgames.automobility.automobile.light.VehicleSignalMode;
import io.github.foundationgames.automobility.automobile.light.Mx5PopupGeometry;
import io.github.foundationgames.automobility.automobile.light.Mx5VisualPose;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small physical lamps attached to verified frame faces. They are
 * regular shaded geometry when off and an opaque full-bright emissive face when on.
 * Shaderpack bloom may use that emissive surface; no shader integration is required.
 */
public final class VehicleHeadlightLampRenderer {
    // An existing opaque vanilla texel supplies a solid color; no new texture or pack is needed.
    private static final Identifier PANEL_TEXTURE = Identifier.withDefaultNamespace("textures/block/white_concrete.png");
    private static final Panels EMPTY = new Panels(List.of(), List.of());
    // Pure immutable local geometry only; no entity/world, mode, tint, or transform is cached.
    private static final Map<VehicleHeadlightLayout.LampFace, LocalFace> LOCAL_FACES =
            new LinkedHashMap<>(64, 0.75f, true);

    private VehicleHeadlightLampRenderer() {}

    /** Call at entity origin, before AutomobileEntityRenderer applies chassis transforms. */
    public static void submit(AutomobileEntityRenderer.State state, PoseStack pose, SubmitNodeCollector submitter) {
        if (state.automobile == null) return;
        submit(state, pose, submitter, VehicleLampSnapshot.capture(state.automobile));
    }

    /** The same captured phase is passed to the nearby surface-light submission. */
    public static void submit(AutomobileEntityRenderer.State state, PoseStack pose, SubmitNodeCollector submitter,
                              VehicleLampSnapshot lamps) {
        Panels panels = panels(state, lamps);
        if (!panels.solid().isEmpty()) {
            submitter.submitCustomGeometry(pose, RenderTypes.entitySolid(PANEL_TEXTURE),
                    (transform, buffer) -> emit(panels.solid(), transform, buffer));
        }
        if (!panels.emissive().isEmpty()) {
            // Opaque alpha uses the existing tested emissive entity pass for both ends.
            submitter.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(PANEL_TEXTURE),
                    (transform, buffer) -> emit(panels.emissive(), transform, buffer));
        }
    }

    /** Actual immutable submitted geometry, also available to guarded native QA without a separate model. */
    public static Panels panels(AutomobileEntityRenderer.State state) {
        if (state.automobile == null) return EMPTY;
        return panels(state, VehicleLampSnapshot.capture(state.automobile));
    }

    public static Panels panels(AutomobileEntityRenderer.State state, VehicleLampSnapshot lamps) {
        AutomobileEntity automobile = state.automobile;
        if (automobile == null || state.isInvisible || automobile.getFrame().isEmpty()
                || state.distanceToCameraSq > 96 * 96) {
            return EMPTY;
        }
        var layout = VehicleHeadlightLayout.forFrame(automobile.getFrame());
        var markers = VehicleMarkerLightLayout.forFrame(automobile.getFrame());
        var signals = VehicleTurnSignalLayout.forFrame(automobile.getFrame());
        var reverse = VehicleReverseLightLayout.forFrame(automobile.getFrame());
        boolean frontMarkersOnly = layout.faces().isEmpty();
        List<VehicleHeadlightLayout.LampFace> front = frontMarkersOnly ? markers.frontFaces() : layout.faces();
        if (front.isEmpty() && markers.rearFaces().isEmpty()
                && signals.frontFaces().isEmpty() && signals.rearFaces().isEmpty() && reverse.rearFaces().isEmpty()) return EMPTY;
        float bounce = automobile.getSuspensionBounce(state.tickDelta) * 0.048f;
        float engineShake = automobile.engineRunning()
                ? (float)(Math.cos((automobile.getTime() + state.tickDelta) * 2.7) / 156) : 0;
        Matrix4f chassis = HeadlightLampGeometry.chassis(state.verticalOffset, state.angularRotation,
                state.yaw, automobile.getWheels().model().radius(), bounce, engineShake);
        // Capture one authoritative phase for both ends and both sides; there is no client blink clock.
        var model = automobile.getFrame().model().modelId();
        if (Mx5PopupGeometry.appliesTo(model)) return mx5Panels(front, markers.rearFaces(), signals,
                reverse.rearFaces(), chassis, lamps, state.lightCoords, state.mx5VisualPose);
        return modelPanels(model, front, markers.rearFaces(), markers.frontFaces(), signals, reverse.rearFaces(),
                chassis, lamps, state.lightCoords, frontMarkersOnly);
    }

    /** Pure fixed-lamp path shared by actual submission and final-stream differential tests. */
    static Panels modelPanels(Identifier model, List<VehicleHeadlightLayout.LampFace> front,
                              List<VehicleHeadlightLayout.LampFace> rear, List<VehicleHeadlightLayout.LampFace> frontMarkers,
                              VehicleTurnSignalLayout signals, List<VehicleHeadlightLayout.LampFace> reverse,
                              Matrix4f chassis, VehicleLampSnapshot lamps, int ambientLight, boolean frontMarkersOnly) {
        // These two authored OBJ models already supply their unlit housings. Their
        // original model finishers discard all solid panels, so never construct them.
        boolean retainSolid = !DefenderLampGeometry.appliesTo(model) && !T3LampGeometry.appliesTo(model);
        Panels drawn = panels(front, rear, signals, reverse, chassis, lamps,
                ambientLight, frontMarkersOnly, !T3LampGeometry.appliesTo(model), retainSolid);
        if (DefenderLampGeometry.appliesTo(model) && lamps.lightMode().hasPositionLights()) {
            var emissive = new ArrayList<>(drawn.emissive());
            addFaces(frontMarkers, End.FRONT, chassis, HeadlightLampGeometry.appearance(VehicleLightMode.POSITION, ambientLight),
                    ambientLight, null, emissive, false);
            drawn = new Panels(drawn.solid(), emissive);
        }
        return T3LampGeometry.forModel(model, DefenderLampGeometry.forModel(model, drawn));
    }

    static Panels panels(List<VehicleHeadlightLayout.LampFace> front, List<VehicleHeadlightLayout.LampFace> rear,
                         Matrix4f chassis, VehicleLightMode effectiveMode, int ambientLight) {
        return panels(front, rear, chassis, effectiveMode, ambientLight, false);
    }

    static Panels panels(List<VehicleHeadlightLayout.LampFace> front, List<VehicleHeadlightLayout.LampFace> rear,
                         Matrix4f chassis, VehicleLightMode effectiveMode, int ambientLight, boolean frontMarkersOnly) {
        return panels(front, rear, VehicleTurnSignalLayout.empty(), chassis, effectiveMode, ambientLight,
                frontMarkersOnly, VehicleSignalMode.OFF, false);
    }

    static Panels panels(List<VehicleHeadlightLayout.LampFace> front, List<VehicleHeadlightLayout.LampFace> rear,
                         VehicleTurnSignalLayout signals, Matrix4f chassis, VehicleLightMode effectiveMode,
                         int ambientLight, boolean frontMarkersOnly, VehicleSignalMode signalMode, boolean signalLit) {
        return panels(front, rear, signals, List.of(), chassis,
                new VehicleLampSnapshot(effectiveMode, signalMode, signalLit), ambientLight, frontMarkersOnly);
    }

    static Panels panels(List<VehicleHeadlightLayout.LampFace> front, List<VehicleHeadlightLayout.LampFace> rear,
                         VehicleTurnSignalLayout signals, List<VehicleHeadlightLayout.LampFace> reverse, Matrix4f chassis,
                         VehicleLampSnapshot lamps, int ambientLight, boolean frontMarkersOnly) {
        return panels(front, rear, signals, reverse, chassis, lamps, ambientLight, frontMarkersOnly, true);
    }

    static Panels panels(List<VehicleHeadlightLayout.LampFace> front, List<VehicleHeadlightLayout.LampFace> rear,
                         VehicleTurnSignalLayout signals, List<VehicleHeadlightLayout.LampFace> reverse, Matrix4f chassis,
                         VehicleLampSnapshot lamps, int ambientLight, boolean frontMarkersOnly, boolean frontBezel) {
        return panels(front, rear, signals, reverse, chassis, lamps, ambientLight, frontMarkersOnly, frontBezel, true);
    }

    static Panels panels(List<VehicleHeadlightLayout.LampFace> front, List<VehicleHeadlightLayout.LampFace> rear,
                         VehicleTurnSignalLayout signals, List<VehicleHeadlightLayout.LampFace> reverse, Matrix4f chassis,
                         VehicleLampSnapshot lamps, int ambientLight, boolean frontMarkersOnly, boolean frontBezel,
                         boolean retainSolid) {
        if (front.size() > 2 || rear.size() > 2 || reverse.size() > 2) {
            throw new IllegalArgumentException("At most two physical lamp faces at each end and function");
        }
        VehicleLightMode effectiveMode = lamps.lightMode();
        int faces = front.size() + rear.size() + signals.frontFaces().size() + signals.rearFaces().size() + reverse.size();
        List<PanelQuad> solid = retainSolid ? new ArrayList<>(2 * faces) : null;
        List<PanelQuad> emissive = new ArrayList<>(faces);
        // Supplemental markers do not claim to be the origins of inferred addon-style headlight beams.
        VehicleLightMode frontMode = frontMarkersOnly
                ? (effectiveMode.hasPositionLights() ? VehicleLightMode.POSITION : VehicleLightMode.OFF) : effectiveMode;
        addFaces(front, End.FRONT, chassis, HeadlightLampGeometry.appearance(frontMode, ambientLight),
                ambientLight, solid, emissive, frontBezel);
        addFaces(rear, End.REAR, chassis, HeadlightLampGeometry.rearAppearance(effectiveMode, lamps.brakeLit(), ambientLight),
                ambientLight, solid, emissive);
        addSignalFaces(signals.frontFaces(), End.SIGNAL_FRONT, chassis, lamps.signalMode(), lamps.signalLit(),
                ambientLight, solid, emissive);
        addSignalFaces(signals.rearFaces(), End.SIGNAL_REAR, chassis, lamps.signalMode(), lamps.signalLit(),
                ambientLight, solid, emissive);
        addFaces(reverse, End.REVERSE_REAR, chassis, HeadlightLampGeometry.reverseAppearance(lamps.reverseLit(), ambientLight),
                ambientLight, solid, emissive);
        return new Panels(solid == null ? List.of() : solid, emissive);
    }

    private static void addFaces(List<VehicleHeadlightLayout.LampFace> faces, End end, Matrix4f chassis,
                                 HeadlightLampGeometry.LensAppearance lens, int ambientLight,
                                 List<PanelQuad> solid, List<PanelQuad> emissive) {
        addFaces(faces, end, chassis, lens, ambientLight, solid, emissive, true);
    }
    private static void addFaces(List<VehicleHeadlightLayout.LampFace> faces, End end, Matrix4f chassis,
                                 HeadlightLampGeometry.LensAppearance lens, int ambientLight,
                                 List<PanelQuad> solid, List<PanelQuad> emissive, boolean bezel) {
        for (var face : faces) {
            addFace(face, end, chassis, lens, ambientLight, solid, emissive, bezel);
        }
    }

    private static void addSignalFaces(List<VehicleHeadlightLayout.LampFace> faces, End end, Matrix4f chassis,
                                       VehicleSignalMode mode, boolean phaseLit, int ambientLight,
                                       List<PanelQuad> solid, List<PanelQuad> emissive) {
        for (var face : faces) {
            boolean selected = mode == VehicleSignalMode.HAZARD
                    || (VehicleTurnSignalLayout.isLeft(face) ? mode == VehicleSignalMode.LEFT : mode == VehicleSignalMode.RIGHT);
            addFace(face, end, chassis, HeadlightLampGeometry.signalAppearance(selected && phaseLit, ambientLight),
                    ambientLight, solid, emissive);
        }
    }

    private static void addFace(VehicleHeadlightLayout.LampFace face, End end, Matrix4f chassis,
                                HeadlightLampGeometry.LensAppearance lens, int ambientLight,
                                List<PanelQuad> solid, List<PanelQuad> emissive) {
        addFace(face, end, chassis, lens, ambientLight, solid, emissive, true);
    }
    private static void addFace(VehicleHeadlightLayout.LampFace face, End end, Matrix4f chassis,
                                HeadlightLampGeometry.LensAppearance lens, int ambientLight,
                                List<PanelQuad> solid, List<PanelQuad> emissive, boolean bezel) {
        if (solid == null && !lens.emissive()) return;
        LocalFace local = localFace(face);
        Vec3 normal = new Vec3(chassis.transformDirection(face.normal().toVector3f())).normalize();
        if (bezel && solid != null) solid.add(quad(local.bezel(), chassis, normal, HeadlightLampGeometry.BEZEL_COLOR, ambientLight, end, false));
        var lensQuad = quad(local.lens(), chassis, normal, lens.color(), lens.light(), end, true);
        (lens.emissive() ? emissive : solid).add(lensQuad);
    }

    static Panels mx5Panels(List<VehicleHeadlightLayout.LampFace> front, List<VehicleHeadlightLayout.LampFace> rear,
                            VehicleTurnSignalLayout signals, List<VehicleHeadlightLayout.LampFace> reverse,
                            Matrix4f chassis, VehicleLampSnapshot lamps, int ambientLight, Mx5VisualPose visual) {
        Panels fixed = panels(List.of(), rear, signals, reverse, chassis, lamps, ambientLight, false);
        var solid = new ArrayList<>(fixed.solid()); var emissive = new ArrayList<>(fixed.emissive());
        for (var face : front) {
            boolean left = face.center().x < 0;
            var posed = Mx5PopupGeometry.chassis(chassis, visual.progress(left));
            addFace(face, End.FRONT, posed, HeadlightLampGeometry.appearance(visual.lensMode(left), ambientLight),
                    ambientLight, solid, emissive, false);
        }
        return new Panels(solid, emissive);
    }

    private static LocalFace localFace(VehicleHeadlightLayout.LampFace face) {
        LocalFace cached = LOCAL_FACES.get(face);
        if (cached != null) return cached;
        LocalFace local = new LocalFace(HeadlightLampGeometry.bezel(face), HeadlightLampGeometry.lens(face));
        LOCAL_FACES.put(face, local);
        if (LOCAL_FACES.size() > 96) {
            LOCAL_FACES.remove(LOCAL_FACES.keySet().iterator().next());
        }
        return local;
    }

    private static PanelQuad quad(List<Vec3> corners, Matrix4f chassis, Vec3 normal, int color, int light,
                                  End end, boolean lens) {
        List<Vec3> transformed = new ArrayList<>(4);
        for (Vec3 point : corners) transformed.add(new Vec3(chassis.transformPosition(point.toVector3f())));
        return new PanelQuad(transformed, normal, color, light, end, lens);
    }

    private static void emit(List<PanelQuad> panels, PoseStack.Pose pose, VertexConsumer buffer) {
        for (var panel : panels) {
            int red = (panel.color >> 16) & 255;
            int green = (panel.color >> 8) & 255;
            int blue = panel.color & 255;
            for (var point : panel.corners) {
                buffer.addVertex(pose, (float)point.x, (float)point.y, (float)point.z)
                        .setColor(red, green, blue, 255).setUv(0.5f, 0.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(panel.light)
                        .setNormal(pose, (float)panel.normal.x, (float)panel.normal.y, (float)panel.normal.z);
            }
        }
    }

    public enum End { FRONT, REAR, SIGNAL_FRONT, SIGNAL_REAR, REVERSE_REAR }

    public record PanelQuad(List<Vec3> corners, Vec3 normal, int color, int light, End end, boolean lens) {
        public PanelQuad { corners = List.copyOf(corners); }
    }

    public record Panels(List<PanelQuad> solid, List<PanelQuad> emissive) {
        public Panels {
            solid = List.copyOf(solid);
            emissive = List.copyOf(emissive);
        }

        public int quadCount() { return solid.size() + emissive.size(); }
    }

    private record LocalFace(List<Vec3> bezel, List<Vec3> lens) {}
}
