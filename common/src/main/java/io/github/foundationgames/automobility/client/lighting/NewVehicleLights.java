package io.github.foundationgames.automobility.client.lighting;

import io.github.foundationgames.automobility.Automobility;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Explicit authored lamp positions; one shared registration path, no inferred faces. */
public final class NewVehicleLights {
    private static final Vec3 RIGHT = new Vec3(1, 0, 0);
    private static final Vec3 REAR_RIGHT = new Vec3(-1, 0, 0);
    private static final Vec3 DOWN = new Vec3(0, 1, 0);

    private NewVehicleLights() {}

    public static void register() {
        register("defender_90",
                front(.59375, -.48125, -1.96875, .3109375, .35535714285714287),
                front(.75625, -.2625, -1.955625, .0984375, .1125),
                rear(.59375, -.3625, 1.940625, .1328125, .1517857142857143),
                rear(.59375, -.209375, 1.940625, .1171875, .13392857142857142),
                front(.59375, -.2625, -1.955625, .13124999999999998, .15),
                rear(.59375, -.521875, 1.940625, .1328125, .1517857142857143));
        register("vw_t3",
                front(.59375, -.68125, -2.31875, .3125, .35714285714285715),
                List.of(),
                rear(.59375, -.54375, 2.30425, .16874999999999998, .125),
                rear(.59375, -.4, 2.30425, .16874999999999998, .09999999999999999),
                front(.59375, -.36875, -2.294875, .1875, .09999999999999999),
                rear(.59375, -.69375, 2.30425, .16874999999999998, .11875));
        register("mx5_na",
                front(.48125, -.634375, -1.505625, .25937499999999997, .29642857142857143),
                List.of(),
                rear(.459375, -.32, 2.010625, .20625, .090625),
                rear(.32499999999999996, -.32, 2.010625, .051250000000000004, .08124999999999999),
                front(.48125, -.28125, -1.991875, .19625, .05),
                rear(.605625, -.32, 2.010625, .06562499999999999, .08124999999999999));
    }

    private static void register(String frame, List<VehicleHeadlightLayout.LampFace> heads,
                                 List<VehicleHeadlightLayout.LampFace> frontMarkers,
                                 List<VehicleHeadlightLayout.LampFace> rearMarkers,
                                 List<VehicleHeadlightLayout.LampFace> reverse,
                                 List<VehicleHeadlightLayout.LampFace> frontSignals,
                                 List<VehicleHeadlightLayout.LampFace> rearSignals) {
        var model = Automobility.rl("frame/" + frame);
        VehicleHeadlightLayout.register(model,
                new VehicleHeadlightLayout(heads.stream().map(VehicleHeadlightLayout.LampFace::center).toList(), heads));
        VehicleMarkerLightLayout.register(model, new VehicleMarkerLightLayout(frontMarkers, rearMarkers));
        VehicleReverseLightLayout.register(model, new VehicleReverseLightLayout(reverse));
        VehicleTurnSignalLayout.register(model, new VehicleTurnSignalLayout(frontSignals, rearSignals));
    }

    private static List<VehicleHeadlightLayout.LampFace> front(
            double spread, double y, double z, double width, double height) {
        return pair(spread, y, z, width, height, RIGHT);
    }

    private static List<VehicleHeadlightLayout.LampFace> rear(
            double spread, double y, double z, double width, double height) {
        return pair(spread, y, z, width, height, REAR_RIGHT);
    }

    private static List<VehicleHeadlightLayout.LampFace> pair(
            double spread, double y, double z, double width, double height, Vec3 right) {
        return List.of(
                new VehicleHeadlightLayout.LampFace(new Vec3(-spread, y, z), right, DOWN, width, height),
                new VehicleHeadlightLayout.LampFace(new Vec3(spread, y, z), right, DOWN, width, height));
    }
}
