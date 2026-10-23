package io.github.foundationgames.automobility.automobile.render.obj;

import net.minecraft.resources.Identifier;

/** Geometry only; current steering/spin stay in each captured pose. */
public final class RetainedWheelScope {
    private RetainedWheelScope() {}
    public static boolean supports(Identifier model, float wheelbaseScale) {
        if (wheelbaseScale != 1f || model == null || !model.getNamespace().equals("automobility")) return false;
        return switch (model.getPath()) {
            case "wheel/defender_terrain", "wheel/t3_steel", "wheel/mx5_alloy" -> true;
            default -> false;
        };
    }
}
