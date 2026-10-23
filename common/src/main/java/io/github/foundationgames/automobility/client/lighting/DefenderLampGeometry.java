package io.github.foundationgames.automobility.client.lighting;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** The authored OBJ supplies housings; only lit circular lenses need an extra pass. */
public final class DefenderLampGeometry {
    public static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath("automobility", "frame/defender_90");
    public static final int SEGMENTS = CircularLampGeometry.SEGMENTS;
    public static final double DOME_RATIO = CircularLampGeometry.DOME_RATIO;
    public static final double SURFACE_OFFSET = CircularLampGeometry.SURFACE_OFFSET;

    private DefenderLampGeometry() {}

    public static boolean appliesTo(Identifier model) { return MODEL_ID.equals(model); }

    public static VehicleHeadlightLampRenderer.Panels forModel(
            Identifier model, VehicleHeadlightLampRenderer.Panels panels) {
        if (!appliesTo(model)) return panels;
        int capacity = 0;
        for (var panel : panels.emissive()) if (panel.lens()) capacity = Math.addExact(capacity, 2 * SEGMENTS);
        List<VehicleHeadlightLampRenderer.PanelQuad> emitted = new ArrayList<>(capacity);
        for (var panel : panels.emissive()) {
            if (panel.lens()) CircularLampGeometry.domeInto(panel, emitted);
        }
        return new VehicleHeadlightLampRenderer.Panels(List.of(), emitted);
    }

    public static List<VehicleHeadlightLampRenderer.PanelQuad> dome(VehicleHeadlightLampRenderer.PanelQuad panel) {
        return CircularLampGeometry.dome(panel);
    }
}
