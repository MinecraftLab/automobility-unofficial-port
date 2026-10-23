package io.github.foundationgames.automobility.client.lighting;

import io.github.foundationgames.automobility.automobile.light.VehicleLightMode;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** T3 uses the shared circular lens template, not another vehicle's renderer. */
public final class T3LampGeometry {
    public static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath("automobility", "frame/vw_t3");
    public static final int RUNNING_REAR_COLOR = 0xFFC62A2A;
    private static final int BASE_RUNNING_REAR_COLOR =
            HeadlightLampGeometry.rearAppearance(VehicleLightMode.POSITION, 0).color();

    private T3LampGeometry() {}

    public static boolean appliesTo(Identifier model) { return MODEL_ID.equals(model); }

    public static VehicleHeadlightLampRenderer.Panels forModel(
            Identifier model, VehicleHeadlightLampRenderer.Panels panels) {
        if (!appliesTo(model)) return panels;
        int capacity = 0;
        for (var panel : panels.emissive()) if (panel.lens()) capacity = Math.addExact(capacity,
                panel.end() == VehicleHeadlightLampRenderer.End.FRONT ? 2 * CircularLampGeometry.SEGMENTS : 1);
        List<VehicleHeadlightLampRenderer.PanelQuad> emitted = new ArrayList<>(capacity);
        for (var panel : panels.emissive()) {
            if (!panel.lens()) continue;
            if (panel.end() == VehicleHeadlightLampRenderer.End.FRONT) {
                CircularLampGeometry.domeInto(panel, emitted);
            } else if (panel.end() == VehicleHeadlightLampRenderer.End.REAR
                    && panel.color() == BASE_RUNNING_REAR_COLOR) {
                emitted.add(new VehicleHeadlightLampRenderer.PanelQuad(panel.corners(), panel.normal(),
                        RUNNING_REAR_COLOR, panel.light(), panel.end(), panel.lens()));
            } else {
                emitted.add(panel);
            }
        }
        return new VehicleHeadlightLampRenderer.Panels(List.of(), emitted);
    }
}
