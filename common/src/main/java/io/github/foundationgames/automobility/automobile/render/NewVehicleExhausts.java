package io.github.foundationgames.automobility.automobile.render;

import io.github.foundationgames.automobility.automobile.AutomobileEngine;
import net.minecraft.resources.Identifier;

/** Authored outlets in frame coordinates; engine placement/scaling cannot move the tailpipe. */
public final class NewVehicleExhausts {
    private static final Identifier DEFENDER = Identifier.parse("automobility:frame/defender_90");
    private static final Identifier T3 = Identifier.parse("automobility:frame/vw_t3");
    private static final Identifier MX5 = Identifier.parse("automobility:frame/mx5_na");
    private static final AutomobileEngine.ExhaustPos DEFENDER_OUTLET = new AutomobileEngine.ExhaustPos(-10, -1.3f, 32.91f, -90, 0);
    private static final AutomobileEngine.ExhaustPos T3_OUTLET = new AutomobileEngine.ExhaustPos(-10, -1.25f, 38.25f, -90, 0);
    private static final AutomobileEngine.ExhaustPos MX5_OUTLET = new AutomobileEngine.ExhaustPos(8, -.8f, 32.30f, -90, 0);
    private NewVehicleExhausts() { }
    public static AutomobileEngine.ExhaustPos frameOutlet(Identifier model) {
        if (DEFENDER.equals(model)) return DEFENDER_OUTLET;
        if (T3.equals(model)) return T3_OUTLET;
        return MX5.equals(model) ? MX5_OUTLET : null;
    }
    public static AutomobileEngine.ExhaustPos frameOutlet(Identifier model, boolean engineHasExhaust) {
        return engineHasExhaust ? frameOutlet(model) : null;
    }
    public static float engineScale(Identifier model) {
        return T3.equals(model) ? .40f : MX5.equals(model) ? .65f : 1;
    }
}
